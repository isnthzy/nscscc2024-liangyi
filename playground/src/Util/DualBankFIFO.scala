import chisel3._
import chisel3.util._  
import config.Configs._

class DualBankFIFO(val data_width:Int,val fifo_size:Int) extends Module{
  val io=IO(new Bundle {
    val flush_fifo =Input(Bool())
    val fifo_length=Output(UInt(log2Ceil(fifo_size+1).W))
    val input_data0=Input(UInt(data_width.W))
    val input_data1=Input(UInt(data_width.W))
    val input_size =Input(UInt(2.W))
    val output_data0=Output(UInt(data_width.W))
    val output_data1=Output(UInt(data_width.W))
    val output_size =Input(UInt(2.W))
  }) 
  val data_bank0=RegInit(VecInit(Seq.fill(fifo_size/2)(0.U(data_width.W))))
  val data_bank1=RegInit(VecInit(Seq.fill(fifo_size/2)(0.U(data_width.W))))
  val head_bank0_idx=RegInit(0.U(log2Ceil(fifo_size/2).W))
  val head_bank1_idx=RegInit(0.U(log2Ceil(fifo_size/2).W))
  val tail_bank0_idx=RegInit(0.U(log2Ceil(fifo_size/2).W))
  val tail_bank1_idx=RegInit(0.U(log2Ceil(fifo_size/2).W))
  val head_sel=RegInit(false.B)
  val tail_sel=RegInit(false.B)
  val length=RegInit(0.U(log2Ceil(fifo_size+1).W)) //NOTE:加一位防溢出

  io.fifo_length:=length
  io.output_data0:=Mux( tail_sel,data_bank1(tail_bank1_idx),data_bank0(tail_bank0_idx))
  io.output_data1:=Mux(~tail_sel,data_bank1(tail_bank1_idx),data_bank0(tail_bank0_idx))
  when(io.flush_fifo){
    length:=0.U
    head_sel:=false.B
    tail_sel:=false.B
    head_bank0_idx:=0.U
    head_bank1_idx:=0.U
    tail_bank0_idx:=0.U
    tail_bank1_idx:=0.U
  }.otherwise{
    head_sel:=head_sel^io.input_size(0)
    tail_sel:=tail_sel^io.output_size(0)
    length:=length+io.input_size-io.output_size
    when(io.input_size.orR){
      when(head_sel){
        data_bank1(head_bank1_idx):=io.input_data0
      }.otherwise{
        data_bank0(head_bank0_idx):=io.input_data0
      }
    }
    when(io.input_size(1)){
      when(head_sel){
        data_bank0(head_bank0_idx):=io.input_data1
      }.otherwise{
        data_bank1(head_bank1_idx):=io.input_data1
      }
    }


    when((head_sel===0.U&&io.input_size(0))||io.input_size(1)){
      head_bank0_idx:=head_bank0_idx+1.U
    }
    when((head_sel===1.U&&io.input_size(0))||io.input_size(1)){
      head_bank1_idx:=head_bank1_idx+1.U
    }
    when((tail_sel===0.U&&io.output_size(0))||io.output_size(1)){
      tail_bank0_idx:=tail_bank0_idx+1.U
    }
    when((tail_sel===1.U&&io.output_size(0))||io.output_size(1)){
      tail_bank1_idx:=tail_bank1_idx+1.U
    }
  }
}
