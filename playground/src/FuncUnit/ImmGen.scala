import chisel3._
import chisel3.util._
import config.Configs._
import Control._

class ImmGen extends Module {
  val io=IO(new Bundle {
    val inst=Input(UInt(32.W))
    val itype=Input(UInt(TYPEXX.length.W))
    val out =Output(UInt(32.W))
  })
  val Imm12= io.inst(21,10)
  val Imm14= io.inst(23,10)
  val Imm16= io.inst(25,10)
  val Imm20= io.inst(25,5 )
  val Imm26= Cat(io.inst(9,0),io.inst(25,10))

  val immMap=Map(
    SDEF(SIMM12) -> Sext(Imm12, 32),
    SDEF(ZIMM12) -> Zext(Imm12, 32),
    SDEF(SIMM14) -> Sext(Cat(Imm14,0.U(2.W)), 32),
    SDEF(BIMM16) -> Sext(Cat(Imm16,0.U(2.W)), 32),
    SDEF(LIMM20) -> Cat(Imm20, 0.U(12.W)),
    SDEF(SIMM20) -> Sext(Cat(Imm20, 0.U(12.W)), 32),
    SDEF(BIMM26) -> Sext(Cat(Imm26,0.U(2.W)), 32)
  )
  io.out:=Mux1hMap(io.itype,immMap)
}
