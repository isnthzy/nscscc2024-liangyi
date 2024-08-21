import chisel3._
import chisel3.util._
class TagvRAM(val size: Int, val width: Int) extends RawModule {
  val addrWidth = log2Ceil(size)

  val io = FlatIO(new Bundle {
    val clka = Input(Clock())
    val wea = Input(Bool())
    val addra = Input(UInt(addrWidth.W))
    val dina = Input(UInt(width.W))
    val douta = Output(UInt(width.W))
  })
  withClock(io.clka) {
    val mem = SyncReadMem(size, UInt(width.W))

    val readData = mem.read(io.addra)
    
    when(io.wea) {
      mem.write(io.addra, io.dina) 
    }
    io.douta := readData.asUInt
 }
}

