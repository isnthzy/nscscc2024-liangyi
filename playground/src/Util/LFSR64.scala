import chisel3._
import chisel3.util._
import config.Configs._


object LFSR
{
  def apply(size: Int = 16, increment: Bool = true.B): UInt =
  {
    val wide = size
    val lfsr = Reg(UInt(wide.W)) // random initial value based on simulation seed
    val xor = lfsr(0) ^ lfsr(1) ^ lfsr(3) ^ lfsr(4)
    when (increment) {
      lfsr := Mux(lfsr === 0.U, 1.U, Cat(xor, lfsr(wide-1,1)))
    }
    lfsr
  }
}
