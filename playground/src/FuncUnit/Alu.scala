import chisel3._
import chisel3.util._
import config.Configs._
import Control._

class Alu extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(ALU_XXX.length.W))
    val src1 = Input(UInt(32.W))
    val src2 = Input(UInt(32.W))
    val result = Output(UInt(32.W))
  })
  

  val sll=Wire(UInt(64.W))
  sll := io.src1 << io.src2(4,0) //左移

  val alu_add= io.src1 + io.src2

  val alu_sub= io.src1 - io.src2 

  val alu_and = io.src1 & io.src2

  val alu_or  = io.src1 | io.src2

  val alu_xor = io.src1 ^ io.src2

  val alu_nor = ~(io.src1 | io.src2)

  val alu_slt = (io.src1.asSInt < io.src2.asSInt).asUInt

  val alu_sltu= (io.src1.asUInt < io.src2.asUInt).asUInt

  val alu_sll = sll(31,0)

  val alu_srl = (io.src1        >> io.src2(4,0)       ).asUInt

  val alu_sra = (io.src1.asSInt >> io.src2(4,0).asUInt).asUInt
  
  val alu_eq  = io.src1 === io.src2

  io.result := Mux1H(Seq(
    io.op(OneHotDef(ALU_ADD)) -> alu_add, 
    io.op(OneHotDef(ALU_SUB)) -> alu_sub,
    io.op(OneHotDef(ALU_AND)) -> alu_and,
    io.op(OneHotDef(ALU_OR )) -> alu_or,
    io.op(OneHotDef(ALU_XOR)) -> alu_xor,
    io.op(OneHotDef(ALU_NOR)) -> alu_nor,
    io.op(OneHotDef(ALU_SLT)) -> alu_slt,
    io.op(OneHotDef(ALU_SLTU))-> alu_sltu,
    io.op(OneHotDef(ALU_SLL)) -> alu_sll, 
    io.op(OneHotDef(ALU_SRL)) -> alu_srl,
    io.op(OneHotDef(ALU_SRA)) -> alu_sra,
    io.op(OneHotDef(ALU_PC4)) -> (io.src1+4.U), //NOTE: ALU_PC4 is io.src1+4
    io.op(OneHotDef(ALU_CPB)) -> io.src2,       //NOTE: ALU_CPB is copy-B
    io.op(OneHotDef(ALU_EQ )) -> alu_eq,
  ))
}