import chisel3._
import chisel3.util._
import config.Configs._
import config._
//NOTE:因为不喜欢SimTop顶层掺了一堆代码极其不美观，所以增加这个单元来解耦合
class MulDivUnit extends Module {
  val io = IO(new Bundle {
    val to_mdu=Flipped(new to_mul_div_bundle())
    val from_mdu=Output(new from_mul_div_bundle())
  })
  if(GenCtrl.FAST_SIM){
    val div = Module(new DivFastSim())
    val mul = Module(new MulFastSim())
    div.io.div:=io.to_mdu.div_en
    div.io.div_signed:=io.to_mdu.signed
    div.io.x:=io.to_mdu.src1
    div.io.y:=io.to_mdu.src2
    io.from_mdu.div_result:=div.io.s
    io.from_mdu.mod_result:=div.io.r
    io.to_mdu.div_ok:=div.io.complete
    div.io.divFastSimFlush:=io.to_mdu.divFastSimFlush

    mul.io.mul_signed:=io.to_mdu.signed
    mul.io.x:=io.to_mdu.src1
    mul.io.y:=io.to_mdu.src2
    io.from_mdu.mul_result:=mul.io.result
  }else{
    val div = Module(new Div())
    val mul = Module(new Mul())
    div.io.clock:=clock
    div.io.reset:=reset.asBool
    mul.io.clock:=clock
    mul.io.reset:=reset.asBool

    div.io.div:=io.to_mdu.div_en
    div.io.div_signed:=io.to_mdu.signed
    div.io.x:=io.to_mdu.src1
    div.io.y:=io.to_mdu.src2
    io.from_mdu.div_result:=div.io.s
    io.from_mdu.mod_result:=div.io.r
    io.to_mdu.div_ok:=div.io.complete

    mul.io.mul_signed:=io.to_mdu.signed
    mul.io.x:=io.to_mdu.src1
    mul.io.y:=io.to_mdu.src2
    io.from_mdu.mul_result:=mul.io.result
  }
}

class to_mul_div_bundle extends Bundle{
  val div_en=Output(Bool())
  val signed=Output(Bool())
  val src1=Output(UInt(DATA_WIDTH.W))
  val src2=Output(UInt(DATA_WIDTH.W))
  val div_ok=Input(Bool())
  val divFastSimFlush=Output(Bool())
}

class from_mul_div_bundle extends Bundle{
  val div_result=UInt(DATA_WIDTH.W)
  val mod_result=UInt(DATA_WIDTH.W)
  val mul_result=UInt((DATA_WIDTH*2).W)
}

class Div extends BlackBox with HasBlackBoxPath{
  val io=IO(new Bundle {
    val clock =Input(Clock())
    val reset =Input(Bool())
    val div   =Input(Bool())
    val div_signed=Input(Bool())
    val x  =Input(UInt(32.W))
    val y  =Input(UInt(32.W))
    val s  =Output(UInt(32.W))
    val r  =Output(UInt(32.W))
    val complete=Output(Bool())
  })
  addPath("playground/src/FuncUnit/Div.v")
}

class Mul extends BlackBox with HasBlackBoxPath{
  val io=IO(new Bundle {
    val clock =Input(Clock())
    val reset =Input(Bool())
    val mul_signed=Input(Bool())
    val x  =Input(UInt(32.W))
    val y  =Input(UInt(32.W))
    val result=Output(UInt(64.W))
  })
  addPath("playground/src/FuncUnit/Mul.v")
}

class MulFastSim extends Module {
  val io = IO(new Bundle {
    val mul_signed=Input(Bool())
    val x  =Input(UInt(32.W))
    val y  =Input(UInt(32.W))
    val result=Output(UInt(64.W))
  })
  val mul_result=RegInit(0.U(64.W))
  io.result:=mul_result
  when(io.mul_signed){
    mul_result:=(io.x.asSInt*io.y.asSInt).asUInt
  }.otherwise{
    mul_result:=io.x*io.y
  }
}

class DivFastSim extends Module {
  val io = IO(new Bundle {
    val divFastSimFlush=Input(Bool())
    val div   =Input(Bool())
    val div_signed=Input(Bool())
    val x  =Input(UInt(32.W))
    val y  =Input(UInt(32.W))
    val s  =Output(UInt(32.W))
    val r  =Output(UInt(32.W))
    val complete=Output(Bool())
  })
  val cnt=RegInit(31.U(5.W))
  val divisor=RegInit(0.U(32.W))
  val dividend=RegInit(0.U(32.W))
  val div_sign=RegInit(false.B)
  val mod_sign=RegInit(false.B)
  val div_result=RegInit(0.U(32.W))
  val mod_result=RegInit(0.U(32.W))
  io.s:=Mux(div_sign, -div_result, div_result)
  io.r:=Mux(mod_sign, -mod_result, mod_result)
  io.complete:=WireDefault(false.B)
  when(io.divFastSimFlush){
    cnt:=31.U
  }
  when(cnt===0.U&&io.div){
    io.complete:=true.B
    cnt:=cnt-1.U
    div_result:=(divisor / dividend).asUInt
    mod_result:=(divisor % dividend).asUInt
  }.elsewhen(cnt===31.U){
    when(io.div){
      cnt:=cnt-1.U
      divisor :=Mux(io.div_signed&&io.x(31), -io.x,io.x)
      dividend:=Mux(io.div_signed&&io.y(31), -io.y,io.y)
      div_sign:=io.div_signed&&(io.x(31) ^ io.y(31))
      mod_sign:=io.div_signed&&io.x(31)
    }
  }.elsewhen(cnt=/=31.U&&io.div){
    cnt:=cnt-1.U
  }
}