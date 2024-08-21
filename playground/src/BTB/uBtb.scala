import chisel3._
import chisel3.util._
import config.Configs._
import config.BtbParams._
import os.read.inputStream


trait  uBtbParams {
  val Num_Ways = 32
  val Tag_Size = 30

  def getTag(pc:UInt) = pc(Tag_Size + 1, 2)

}
class uBtbEntry extends BTBEntry{}

class uBtbWay extends Module with uBtbParams{
    val io = IO(new Bundle{
        val req_tag_0 = Input(UInt(Tag_Size.W))
        val resp_0 = Output(new uBtbEntry)
        val resp_hit_0 = Output(Bool())
        val req_tag_1 = Input(UInt(Tag_Size.W))
        val resp_1 = Output(new uBtbEntry)
        val resp_hit_1 = Output(Bool())

        val update_tag = Input(UInt(Tag_Size.W))
        val update_hit = Output(Bool())
        val can_write  = Output(Bool())

        val write_valid = Input(Bool())
        val write_entry = Input(new uBtbEntry)
        val write_tag = Input(UInt(Tag_Size.W))
  })
    val data = Reg(new uBtbEntry)
    val tag = Reg(UInt(Tag_Size.W))
    val valid = RegInit(false.B)

    io.resp_0 := data
    io.resp_hit_0 := tag === io.req_tag_0 && valid

    io.resp_1 := data
    io.resp_hit_1 := tag === io.req_tag_1 && valid

    io.update_hit := (tag === io.update_tag && valid) ||
                     (io.write_tag === io.update_tag && io.write_valid)
    io.can_write := !valid
    when (io.write_valid) {
        when (!valid) {
            valid := true.B
        }
        tag   := io.write_tag
        data  := io.write_entry
    }
}

class uBtb extends Module with uBtbParams with BPUtils {
    val io = IO(new Bundle{
        val in_0   = Input(new PredictorInput)
        val in_1   = Input(new PredictorInput)
        // val in_0   = Flipped(new PredictorInput)
        // val in_1   = Flipped(new PredictorInput)
        // val in_0   = Flipped(DecoupledIO(new PredictorInput))
        // val in_1   = Flipped(DecoupledIO(new PredictorInput))

        val out_0    = Output(new PredictorOutput)
        val out_1    = Output(new PredictorOutput)

        val update = Input(new PredictorUpdate)
        // val update = Flipped(Valid(new PredictorUpdate))


    })

    val ways = Seq.tabulate(Num_Ways)(w => Module(new uBtbWay))
    val ctrs = Seq.tabulate(Num_Ways)(w => RegInit(2.U(2.W)))
    val replacer = new PseudoLRU(Num_Ways)
    // val replacer = new RandomReplacement(Num_Ways)
    val replacer_touch_ways = Wire(Vec(3, Valid(UInt(log2Ceil(Num_Ways).W))))
    // val lfsr = new LFSR64()

    //req every way
    //req_0
    val bp_fire_0_reg = io.in_0.bp_fire
    val req_pc_0_reg  = io.in_0.req_pc
    val bp_fire_1_reg = io.in_1.bp_fire
    val req_pc_1_reg  = io.in_1.req_pc
    // val bp_fire_0_reg = RegNext(io.in_0.bp_fire)
    // val req_pc_0_reg  = RegEnable(io.in_0.req_pc, 0.U, io.in_0.bp_fire)
    // val bp_fire_1_reg = RegNext(io.in_1.bp_fire)
    // val req_pc_1_reg  = RegEnable(io.in_1.req_pc, 0.U, io.in_1.bp_fire)

    ways.foreach(_.io.req_tag_0 := getTag(req_pc_0_reg))
    // ways.foreach(_.io.req_tag_0 := getTag(io.in_0.req_pc))
    // ways.foreach(_.io.req_tag_0 := getTag(io.in_0.bits.req_pc))

    val total_hits_oh_0 = VecInit(ways.map(_.io.resp_hit_0)).asUInt
    val hit_0 = total_hits_oh_0.orR
    // val hit = total_hits_oh.reduce(_||_)
    val hit_way_0 = OHToUInt(total_hits_oh_0)
    val all_entries_0 = VecInit(ways.map(_.io.resp_0))
    val resp_0 = Mux1H(total_hits_oh_0, all_entries_0)
    val brTaken_0 = Mux1H(total_hits_oh_0, ctrs)(1)
    // io.in_0.ready := true.B //will be changed

    //req_1
    ways.foreach(_.io.req_tag_1 := getTag(io.in_1.req_pc))
    // ways.foreach(_.io.req_tag_1 := getTag(io.in_1.bits.req_pc))
    val total_hits_oh_1 = VecInit(ways.map(_.io.resp_hit_1)).asUInt
    val hit_1 = total_hits_oh_1.orR
    // val hit = total_hits_oh.reduce(_||_)
    val hit_way_1 = OHToUInt(total_hits_oh_1)
    val all_entries_1 = VecInit(ways.map(_.io.resp_1))
    val resp_1 = Mux1H(total_hits_oh_1, all_entries_1)
    val brTaken_1 = Mux1H(total_hits_oh_1, ctrs)(1)

    // io.in_1.ready := true.B //will be changed

    // for(((c, pred), entry) <- ctrs zip ubtb_pred zip all_entries){
    //     pred.brTaken := c(1)
    //     pred.brTarget := entry.brTarget
    //     pred.fallThroughAddr := req_pc + 8.U
    // }

    // io.out.read_hit   := hit_1 || hit_0
    // io.out_0.read_hit   := hit_0
    io.out_0.brTaken    := hit_0 && brTaken_0
    // io.out.firstTaken := (hit_0 && brTaken_0)
    io.out_0.entry      := resp_0
    // io.out.fallThroughAddr := req_pc_0_reg + 8.U
    // io.out.fallThroughAddr := io.in_0.req_pc + 8.U

    io.out_1.brTaken    := hit_1 && brTaken_1
    // io.out.firstTaken := (hit_0 && brTaken_0)
    io.out_1.entry      := resp_1

    // pred update replacer state
    replacer_touch_ways(0).valid := RegNext(bp_fire_0_reg && hit_0)
    // replacer_touch_ways(0).valid := RegNext(io.in_0.bp_fire && hit_0)
    // replacer_touch_ways(0).valid := RegNext(io.in_0.bits.bp_fire && hit_0)
    replacer_touch_ways(0).bits  := RegEnable(hit_way_0, bp_fire_0_reg && hit_0)
    // replacer_touch_ways(0).bits  := RegEnable(hit_way_0, io.in_0.bp_fire && hit_0)
    // replacer_touch_ways(0).bits  := RegEnable(hit_way_0, io.in_0.bits.bp_fire && hit_0)

    replacer_touch_ways(1).valid := RegNext(bp_fire_1_reg && hit_1)
    // replacer_touch_ways(1).valid := RegNext(io.in_1.bp_fire && hit_1)
    // replacer_touch_ways(1).valid := RegNext(io.in_1.bits.bp_fire && hit_1)
    replacer_touch_ways(1).bits  := RegEnable(hit_way_1, bp_fire_1_reg && hit_1)
    // replacer_touch_ways(1).bits  := RegEnable(hit_way_1, io.in_1.bp_fire && hit_1)
    // replacer_touch_ways(1).bits  := RegEnable(hit_way_1, io.in_1.bits.bp_fire && hit_1)

    //update
    val u_valid = io.update.valid
    val u_tag = getTag(io.update.pc)
    // val u_valid = io.update.bits.valid
    // val u_tag = getTag(io.update.bits.pc)
    ways.foreach(_.io.update_tag := u_tag)
    val u_hits_oh = VecInit(ways.map(_.io.update_hit)).asUInt
    val u_hit = u_hits_oh.orR
    val can_writes = VecInit(ways.map(_.io.can_write))
    val has_empty  = can_writes.reduce(_||_)
    dontTouch(has_empty)
    val empty_way = PriorityEncoder(can_writes)
    dontTouch(empty_way)
    // val u_hit = u_hits_oh.reduce(_||_)
    val u_br_update_valid = io.update.brTaken && io.update.valid
    // val u_br_update_valid = io.update.bits.brTaken && io.update.bits.valid

    val u_valid_reg     = RegNext(u_valid)
    val u_tag_reg       = RegEnable(u_tag, u_valid)
    val u_hits_oh_reg   = RegEnable(u_hits_oh, u_valid)
    val u_hit_reg       = RegEnable(u_hit, u_valid)
    val u_alloc_way     = Mux(has_empty, empty_way, replacer.way)

    val u_write_way   = Mux(u_hit_reg, u_hits_oh_reg, UIntToOH(u_alloc_way))
    val u_write_entry = RegEnable(io.update.entry, u_valid)
    // val u_write_entry = RegEnable(io.update.bits.entry, u_valid)
    val u_write_valid = VecInit((0 until Num_Ways).map(w => u_write_way(w).asBool && u_valid_reg))
    for (w <- 0 until Num_Ways) {
        ways(w).io.write_valid := u_write_valid(w)
        ways(w).io.write_tag   := u_tag_reg
        ways(w).io.write_entry := u_write_entry
    }

    // update saturating counters
    val u_br_update_valid_reg = RegEnable(u_br_update_valid, u_valid)
    val u_br_taken_reg        = RegEnable(io.update.brTaken, u_valid)
    // val u_br_taken_reg        = RegEnable(io.update.bits.brTaken, u_valid)
    for (w <- 0 until Num_Ways) {
        when (u_write_valid(w)) {
            when (u_br_update_valid_reg) {
            ctrs(w) := satUpdate(ctrs(w), 2, u_br_taken_reg)
            }
        }
    }

    replacer_touch_ways(2).valid := u_valid_reg
    replacer_touch_ways(2).bits  := OHToUInt(u_write_way)

    /******** update replacer *********/
    replacer.access(replacer_touch_ways)


}
