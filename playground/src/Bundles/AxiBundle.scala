
import chisel3._
import chisel3.util._  
import config.Configs._

/*-----------------------------AxiBridgeBundle-----------------------------*/
class InstAxiBridgeSendCannel extends Bundle{ //
  val addr_en=Output(Bool())
  val ren=Output(Bool())
  val wen=Output(Bool())
  val addr=Output(UInt(ADDR_WIDTH.W))
  val addr_ok=Input(Bool())
  val data_ok=Input(Bool())
  val size =Output(UInt(3.W))
  val wstrb=Output(UInt((DATA_WIDTH/8).W))
  val wdata=Output(UInt(DATA_WIDTH.W))
} //缩写为sc接口

class DataAxiBridgeSendCannel extends Bundle{ //
  val addr_en=Output(Bool())
  val ren=Output(Bool())
  val wen=Output(Bool())
  val addr=Output(UInt(ADDR_WIDTH.W))
  val addr_ok=Input(Bool())
  val size =Output(UInt(3.W))
  val wstrb=Output(UInt((DATA_WIDTH/8).W))
  val wdata=Output(UInt(DATA_WIDTH.W))
} //缩写为sc接口


class InstAxiBridgeRespondCannel extends Bundle{
  val rdata=Input(UInt((DATA_WIDTH*2).W))
  val addr_ok=Input(Bool())
  val data_ok=Input(Bool())
} //缩写为rc接口

class DataAxiBridgeRespondCannel extends Bundle{
  val rdata=Input(UInt(DATA_WIDTH.W))
  val data_ok=Input(Bool())
} //缩写为rc接口
