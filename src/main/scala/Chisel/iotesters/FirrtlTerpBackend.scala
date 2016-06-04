// See LICENSE for license details.
package Chisel.iotesters

import java.io.File

import Chisel.{Module, Bits}
import Chisel.internal.HasId

import scala.collection.mutable.HashMap

import firrtl_interpreter.InterpretiveTester

private[iotesters] class FirrtlTerpBackend(
                                           dut: Module,
                                           firrtlIR: String,
                                           verbose: Boolean = true,
                                           logger: java.io.PrintStream = System.out,
                                           _base: Int = 16,
                                           _seed: Long = System.currentTimeMillis) extends Backend(_seed) {
  val interpretiveTester = new InterpretiveTester(firrtlIR)
  reset(5) // reset firrtl interpreter on construction

  val portNames = getDataNames(dut).toMap

  def poke(signal: HasId, value: BigInt, off: Option[Int] = None): Unit = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        interpretiveTester.poke(name, value)
        if (verbose) logger println s"  POKE ${name} <- ${bigIntToStr(value, _base)}"
      case _ =>
    }
  }

  def peek(signal: HasId, off: Option[Int] = None): BigInt = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        val result = interpretiveTester.peek(name)
        if (verbose) logger println s"  PEEK ${name} -> ${bigIntToStr(result, _base)}"
        result
      case _ => BigInt(rnd.nextInt)
    }
  }

  def expect(signal: HasId, expected: BigInt, msg: => String = "") : Boolean = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        val got = interpretiveTester.peek(name)
        val good = got == expected
        if (verbose) logger println s"""${msg}  EXPECT ${name} -> ${bigIntToStr(got, _base)} == ${bigIntToStr(expected, _base)} ${if (good) "PASS" else "FAIL"}"""
        good
      case _ => false
    }
  }

  def step(n: Int): Unit = {
    interpretiveTester.step(n)
  }

  def reset(n: Int = 1): Unit = {
    interpretiveTester.poke("reset", 1)
    interpretiveTester.step(n)
    interpretiveTester.poke("reset", 0)
  }

  def finish: Unit = Unit
}

private[iotesters] object setupFirrtlTerpBackend {
  def apply(dutGen: ()=> Chisel.Module): Backend = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val dir = new File(testDirPath)
    dir.mkdirs()

    CircuitGraph.clear
    val circuit = Chisel.Driver.elaborate(dutGen)
    val dut = CircuitGraph construct circuit

    // Dump FIRRTL for debugging
    val firrtlIRFilePath = s"${testDirPath}/${circuit.name}.ir"
    Chisel.Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))
    val firrtlIR = Chisel.Driver.emit(dutGen)

    new FirrtlTerpBackend(dut, firrtlIR)
  }
}
