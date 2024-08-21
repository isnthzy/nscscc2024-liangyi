import chisel3._
import chisel3.util._
import config.Configs._

class RegFile extends Module{
  val io=IO(new Bundle {
    val wen1  =Input(Bool())
    val waddr1=Input(UInt(5.W))
    val wdata1=Input(UInt(DATA_WIDTH.W))
    val wen2  =Input(Bool())
    val waddr2=Input(UInt(5.W))
    val wdata2=Input(UInt(DATA_WIDTH.W))
    val raddr1=Input(UInt(5.W))
    val rdata1=Output(UInt(DATA_WIDTH.W))
    val raddr2=Input(UInt(5.W))
    val rdata2=Output(UInt(DATA_WIDTH.W))
    val raddr3=Input(UInt(5.W))
    val rdata3=Output(UInt(DATA_WIDTH.W))
    val raddr4=Input(UInt(5.W))
    val rdata4=Output(UInt(DATA_WIDTH.W))

    val RegToDiff=(Vec(32, UInt(32.W)))
  })
  val rf=RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  when(io.wen1){ 
    when(io.waddr1=/=0.U||io.wdata1=/=io.waddr2){
      rf(io.waddr1):=io.wdata1 
    }
  }
  when(io.wen2){ 
    when(io.waddr2=/=0.U){
      rf(io.waddr2):=io.wdata2
    }
  }
  io.rdata1:=rf(io.raddr1)
  io.rdata2:=rf(io.raddr2)
  io.rdata3:=rf(io.raddr3)
  io.rdata4:=rf(io.raddr4)

  io.RegToDiff:=rf
}  
