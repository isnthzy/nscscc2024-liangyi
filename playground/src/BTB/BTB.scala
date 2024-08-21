import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import config.Configs._
import config.BtbParams._

// class  BtbParams {
//   val BTB_Entrys = 128
//   val BTB_Ways = 2
//   val BTB_Sets = BTB_Entrys/BTB_Ways //64
// }
trait BPUtils {
    val nEntries = BTB_Entrys
    val nWays    = BTB_Ways
    val nSets    = nEntries/nWays // 128
    val tagSize    = 20

    def satUpdate(old: UInt, len: Int, taken: Bool): UInt = {
        val oldSatTaken = old === ((1 << len)-1).U
        val oldSatNotTaken = old === 0.U
        Mux(oldSatTaken && taken, ((1 << len)-1).U,
        Mux(oldSatNotTaken && !taken, 0.U,
            Mux(taken, old + 1.U, old - 1.U)))
    }

    def get_seg(pc: UInt, size: Int, star: Int): UInt = {
        pc(star + size - 1, star)
    }

    //折叠hist为l长度
    def compute_folded_idx(hist: UInt, histLen: Int, l: Int) = {
        if (histLen > 0) {
            val nChunks = (histLen + l - 1) / l
            val hist_chunks = (0 until nChunks) map {i =>
                if( (i+1)*l > histLen){
                    hist(histLen-1, i*l)
                }else{
                    hist((i+1)*l, i*l)
                }
            }
            ParallelXOR(hist_chunks)
        }
        else 0.U
    }
}

class BTBEntry extends Bundle {
    // val isBrCond = Bool()   //
    // val isBrJmp  = Bool()
    val brType   = UInt(BrTypeWidth.W)
    val brTarget = UInt(ADDR_WIDTH.W)

}
class BTBEntryWithTag extends Bundle with BPUtils{
    val valid = Bool()
    val tag = UInt(tagSize.W)
    val entry = new BTBEntry()
}

class PredictorInput extends Bundle {
    val bp_fire = Bool()
    val req_pc  = UInt(ADDR_WIDTH.W)
}
/**
  * 001 条件分支beq、bne ...
  * 010 无条件分支b bl jirl
  * 100 ras ? 待实现
  */
class PredictorOutput extends Bundle {
    // val read_hit        = UInt(FetchWidth.W)
    // val brTaken         = UInt(FetchWidth.W)
    // val firstTaken      = Bool()
    val brTaken         = Bool()
    val entry           = new BTBEntry()
    // val fallThroughAddr = UInt(ADDR_WIDTH.W)
}

class BTB extends Module with BPUtils {
    val io = IO(new Bundle{
        val in_0  = Input(new PredictorInput)
        val in_1  = Input(new PredictorInput)

        val out_0 = Output(new PredictorOutput)
        val out_1 = Output(new PredictorOutput)

        val update = Input(new PredictorUpdate)
    })
    // class BTBBank(val numSet: Int, val numWays: Int) extends Module{

    // }
    // val ctrs = Vec(nSets,
    //     RegInit(VecInit(Seq.fill(nSets)(0.U.asTypeOf(UInt(2.W)))))
    // )
    val btbBank = RegInit(VecInit(Seq.fill(nSets)(VecInit(Seq.fill(nWays)(0.U.asTypeOf(new BTBEntryWithTag()))))))

    // val btbBank = Seq.fill(nSets)(
    //     RegInit(VecInit.fill(nWays)(0.U.asTypeOf(new BTBEntryWithTag())))
    // )
    // val btbBank =Vec(nSets,
    //     RegInit(VecInit.fill(nWays)(0.U.asTypeOf(new BTBEntryWithTag())))
    // )
    // val btbBank = Seq.fill(nSets)(
    //     RegInit(VecInit.fill(nWays)(0.U.asTypeOf(new BTBEntryWithTag())))
    // )

    val predictor = Module(new Predictor())

    //pred
    val idx_0, idx_1, idx_u = Wire(UInt(log2Ceil(nSets).W))
    val tag_0, tag_1, tag_u = Wire(UInt(tagSize.W))

    val hits_0, hits_1, hits_u = Wire(Vec(nWays, Bool()))
    idx_0 := compute_folded_idx(io.in_0.req_pc, ADDR_WIDTH, log2Ceil(nSets))
    tag_0 := get_seg(io.in_0.req_pc, tagSize, lower)
    idx_1 := compute_folded_idx(io.in_1.req_pc, ADDR_WIDTH, log2Ceil(nSets))
    tag_1 := get_seg(io.in_1.req_pc, tagSize, lower)

    val rdata_0 = Wire(Vec(nWays, new BTBEntryWithTag))
    val rdata_1 = Wire(Vec(nWays, new BTBEntryWithTag))
    // val rdata_0 = Wire(Vec(nWays, new BTBEntryWithTag))
    // val rdata_1 = Wire(Vec(nWays, new BTBEntryWithTag))

    val read_entrys_0 = (0 until nWays).map{i =>rdata_0(i).entry}
    val read_entrys_1 = (0 until nWays).map{i =>rdata_1(i).entry}

    rdata_0 := btbBank(idx_0)
    rdata_1 := btbBank(idx_1)

    hits_0 := (0 until nWays).map{i => tag_0 === rdata_0(i).tag && io.in_0.bp_fire}
    hits_1 := (0 until nWays).map{i => tag_1 === rdata_1(i).tag && io.in_1.bp_fire}
    
    val resp_0 = PriorityMux(Seq.tabulate(nWays)(i => ((hits_0(i)) -> read_entrys_0(i))))
    val resp_1 = PriorityMux(Seq.tabulate(nWays)(i => ((hits_1(i)) -> read_entrys_1(i))))

    //direct prediction
    predictor.io.pc_in0 := io.in_0.req_pc
    predictor.io.pc_in1 := io.in_1.req_pc

    val brTaken0 = predictor.io.brTaken0
    val brTaken1 = predictor.io.brTaken1

    io.out_0.brTaken := brTaken0
    io.out_0.entry := resp_0
    io.out_1.brTaken := brTaken1
    io.out_1.entry := resp_1


    //update
    val rdata_u = Wire(Vec(nWays, new BTBEntryWithTag))
    val update_data = Wire(new BTBEntryWithTag())
    val write_way = Wire(UInt(1.W))
    val lfsr = LFSR(log2Ceil(nSets), io.update.valid)

    rdata_u := btbBank(idx_u)
    idx_u := compute_folded_idx(io.update.pc, ADDR_WIDTH, log2Ceil(nSets))
    tag_u := get_seg(io.update.pc, tagSize, lower)
    //更新时检查是否需要新分配
    hits_u := (0 until nWays).map{i => tag_u === rdata_u(i).tag && rdata_u(i).valid}
    //命中时更新，非命中时更新到空闲项，最后随机替换
    when(hits_u.reduce(_||_)){
        write_way := Mux(hits_u(0), 0.U, 1.U)
    }.elsewhen(!rdata_u(0).valid || !rdata_u(1).valid){
        write_way := Mux(!rdata_u(0).valid, 0.U, 1.U)
    }.otherwise{
        write_way := (lfsr ^ idx_u).xorR  //
    }

    update_data.valid := io.update.valid
    update_data.tag := tag_u
    update_data.entry := io.update.entry

    when( io.update.valid ){
        btbBank(idx_u)(write_way) := update_data
    }
    predictor.io.update.valid := io.update.valid
    predictor.io.update.brTaken := io.update.brTaken
    predictor.io.update.pc := io.update.pc
}