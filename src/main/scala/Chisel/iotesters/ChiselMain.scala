// See LICENSE for license details.

package Chisel.iotesters

import Chisel._

import scala.collection.mutable.{ArrayBuffer}
import scala.util.{DynamicVariable}
import scala.sys.process.Process
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.io.{File, IOException}

private[iotesters] class TesterContext {
  var isVCS = false
  var isGenVerilog = false
  var isGenHarness = false
  var isCompiling = false
  var isRunTest = false
  var isUpdate = true
  var testerSeed = System.currentTimeMillis
  val testCmd = ArrayBuffer[String]()
  var targetDir = new File("test_run_dir").getCanonicalPath
  var logFile: Option[String] = None
  var waveform: Option[String] = None
  val processes = ArrayBuffer[Process]()
}

object chiselMain {
  private val contextVar = new DynamicVariable[Option[TesterContext]](None)
  private[iotesters] def context = contextVar.value getOrElse (new TesterContext)

  private def parseArgs(args: Array[String]) {
    for (i <- 0 until args.size) {
      args(i) match {
        case "--vcs" => context.isVCS = true
        case "--v" => context.isGenVerilog = true
        case "--backend" => args(i+1) match {
          case "v" => context.isGenVerilog = true
          case "c" => context.isGenVerilog = true
          case _ =>
        }
        case "--genHarness" => context.isGenHarness = true
        case "--compile" => context.isCompiling = true
        case "--test" => context.isRunTest = true
        case "--testCommand" => context.testCmd ++= args(i+1) split ' '
        case "--testerSeed" => context.testerSeed = args(i+1).toLong
        case "--targetDir" => context.targetDir = args(i+1)
        case "--noUpdate" => context.isUpdate = false
        case "--logFile" => context.logFile = Some(args(i+1))
        case "--waveform" => context.waveform = Some(args(i+1))
        case _ =>
      }
    }
  }

  private def genHarness[T <: Module](dut: Module, isVCS: Boolean,
      firrtlIRFilePath: String, harnessFilePath:String, vcdFilePath: String) {
    if (isVCS) {
      assert(false, "unimplemented")
    } else {
      firrtl.Driver.compile(firrtlIRFilePath, harnessFilePath, new VerilatorCppHarnessCompiler(dut, vcdFilePath))
    }
  }

  private def compile(dutName: String) {
    val dir = new File(context.targetDir)

    if (context.isVCS) {
    } else {
      // Copy API files
      copyVerilatorHeaderFiles(s"${context.targetDir}")
      // Generate Verilator
      Driver.verilogToCpp(dutName, dutName, dir, Seq(), new File(s"${dutName}-harness.cpp")).!
      // Compile Verilator
      Driver.cppToExe(dutName, dir).!
    }
  }

  private def elaborate[T <: Module](args: Array[String], dutGen: () => T): T = {
    parseArgs(args)
    CircuitGraph.clear
    try {
      Files.createDirectory(Paths.get(context.targetDir))
    } catch {
      case x: FileAlreadyExistsException =>
      case x: IOException =>
        System.err.format("createFile error: %s%n", x)
    }
    val circuit = Driver.elaborate(dutGen)
    val dut = (CircuitGraph construct circuit).asInstanceOf[T]
    val dir = new File(context.targetDir)

    val firrtlIRFilePath = s"${dir}/${circuit.name}.ir"
    Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))

    val verilogFilePath = s"${dir}/${circuit.name}.v"
    if (context.isGenVerilog) firrtl.Driver.compile(
      firrtlIRFilePath, verilogFilePath, new firrtl.VerilogCompiler())

    if (context.isGenHarness) genHarness(dut, context.isVCS, firrtlIRFilePath,
      s"${chiselMain.context.targetDir}/${circuit.name}-harness.cpp",
      s"${chiselMain.context.targetDir}/${circuit.name}.vcd")

    if (context.isCompiling) compile(circuit.name)

    if (context.testCmd.isEmpty) {
      context.testCmd += s"""${context.targetDir}/${if (context.isVCS) "" else "V"}${dut.name}"""
    }
    dut
  }

  def apply[T <: Module](args: Array[String], dutGen: () => T): T = {
    val ctx = Some(new TesterContext)
    val dut = contextVar.withValue(ctx) {
      elaborate(args, dutGen)
    }
    contextVar.value = ctx // TODO: is it ok?
    dut
  }

  def apply[T <: Module](args: Array[String], dutGen: () => T, testerGen: T => PeekPokeTester[T]) = {
    contextVar.withValue(Some(new TesterContext)) {
      val dut = elaborate(args, dutGen)
      if (context.isRunTest) {
        try {
          assert(testerGen(dut).finish, "Test failed")
        } finally {
          context.processes foreach (_.destroy)
          context.processes.clear
        }
      }
      dut
    }
  }
}

object chiselMainTest {
  def apply[T <: Module](args: Array[String], dutGen: () => T)(testerGen: T => PeekPokeTester[T]) = {
    chiselMain(args, dutGen, testerGen)
  }
}
