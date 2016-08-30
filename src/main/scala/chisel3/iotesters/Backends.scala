// See LICENSE for license details.
package chisel3.iotesters

import chisel3.internal.InstanceId

/**
  * define interface for ClassicTester backend implementations such as verilator and firrtl interpreter
  */

private[iotesters] abstract class Backend(_seed: Long = System.currentTimeMillis) {
  val rnd = new scala.util.Random(_seed)

  def poke(signal: InstanceId, value: BigInt, off: Option[Int]): Unit

  def peek(signal: InstanceId, off: Option[Int]): BigInt

  def poke(path: String, value: BigInt): Unit

  def peek(path: String): BigInt

  def expect(signal: InstanceId, expected: BigInt) : Boolean = expect(signal, expected, "")

  def expect(signal: InstanceId, expected: BigInt, msg: => String) : Boolean

  def expect(path: String, expected: BigInt) : Boolean = expect(path, expected, "")

  def expect(path: String, expected: BigInt, msg: => String) : Boolean

  def step(n: Int): Unit

  def reset(n: Int = 1): Unit

  def finish: Unit
}

