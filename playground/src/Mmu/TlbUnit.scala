import chisel3._
import chisel3.util._
import config.Configs._

class TlbUnit extends Module {
  val io = IO(new Bundle {
    val s=Vec(2,new tlb_s_bundle())

    val w_en =Input(Bool())
    val w_idx=Input(UInt(log2Ceil(TLB_NUM).W))
    val w=Input(new tlb_w_and_r_bundle())

    val r_idx=Input(UInt(log2Ceil(TLB_NUM).W))
    val r=Output(new tlb_w_and_r_bundle())
    val invtlb=Input(new mmu_with_tlb_inv_bundle())
  })
  val tlb=Reg(Vec(TLB_NUM,new tlb_w_and_r_bundle()))
  val match0  =VecInit(Seq.fill(TLB_NUM)(0.U(1.W)))
  val match1  =VecInit(Seq.fill(TLB_NUM)(0.U(1.W)))
  val s0_sel=VecInit(Seq.fill(TLB_NUM)(false.B))
  val s1_sel=VecInit(Seq.fill(TLB_NUM)(false.B))
  val s0_hit_index=WireDefault(0.U(log2Ceil(TLB_NUM).W))
  val s1_hit_index=WireDefault(0.U(log2Ceil(TLB_NUM).W))
  for(i<-0 until TLB_NUM){
    var ps4kb=tlb(i).ps===12.U

    match0(i):=(tlb(i).e.asBool&&(tlb(i).g.asBool||(tlb(i).asid===io.s(0).asid))
      &&Mux(ps4kb,io.s(0).vppn===tlb(i).vppn , io.s(0).vppn(18,9)===tlb(i).vppn(18,9)))
    s0_sel(i):=Mux(ps4kb,io.s(0).va_12,io.s(0).vppn(8))

    match1(i):=(tlb(i).e.asBool&&(tlb(i).g.asBool||(tlb(i).asid===io.s(1).asid))
      &&Mux(ps4kb,io.s(1).vppn===tlb(i).vppn , io.s(1).vppn(18,9)===tlb(i).vppn(18,9)))
    s1_sel(i):=Mux(ps4kb,io.s(1).va_12,io.s(1).vppn(8))
  }
  s0_hit_index:=OHToUInt(match0.asUInt)
  s1_hit_index:=OHToUInt(match1.asUInt)
  val s0_hit  =match0.asUInt.orR
  val s1_hit  =match1.asUInt.orR

  
  //NOTE:查找要一拍delay，因此得到结果手动等一拍指示结果
  io.s(0).hit:=RegNext(s0_hit)
  io.s(1).hit:=RegNext(s1_hit)
  io.s(0).index:=RegNext(s0_hit_index)
  io.s(0).ps4kb:=RegNext(tlb(s0_hit_index).ps===12.U)
  io.s(0).ppn  :=RegNext(Mux(s0_sel(s0_hit_index),tlb(s0_hit_index).ppn1,tlb(s0_hit_index).ppn0))
  io.s(0).v    :=RegNext(Mux(s0_sel(s0_hit_index),tlb(s0_hit_index).v1  ,tlb(s0_hit_index).v0))
  io.s(0).d    :=RegNext(Mux(s0_sel(s0_hit_index),tlb(s0_hit_index).d1  ,tlb(s0_hit_index).d0)  )
  io.s(0).mat  :=RegNext(Mux(s0_sel(s0_hit_index),tlb(s0_hit_index).mat1,tlb(s0_hit_index).mat0))
  io.s(0).plv  :=RegNext(Mux(s0_sel(s0_hit_index),tlb(s0_hit_index).plv1,tlb(s0_hit_index).plv0))

  io.s(1).index:=RegNext(s1_hit_index)
  io.s(1).ps4kb:=RegNext(tlb(s1_hit_index).ps===12.U)
  io.s(1).ppn  :=RegNext(Mux(s1_sel(s1_hit_index),tlb(s1_hit_index).ppn1,tlb(s1_hit_index).ppn0))
  io.s(1).v    :=RegNext(Mux(s1_sel(s1_hit_index),tlb(s1_hit_index).v1  ,tlb(s1_hit_index).v0))
  io.s(1).d    :=RegNext(Mux(s1_sel(s1_hit_index),tlb(s1_hit_index).d1  ,tlb(s1_hit_index).d0))
  io.s(1).mat  :=RegNext(Mux(s1_sel(s1_hit_index),tlb(s1_hit_index).mat1,tlb(s1_hit_index).mat0))
  io.s(1).plv  :=RegNext(Mux(s1_sel(s1_hit_index),tlb(s1_hit_index).plv1,tlb(s1_hit_index).plv0))

  when(io.w_en){  tlb(io.w_idx):=io.w }
  io.r:=tlb(io.r_idx)


  //NOTE:invtlb
  for(i<-0 until TLB_NUM){
    var ps4kb=tlb(i).ps===12.U
    when(io.invtlb.en){
      when(io.invtlb.op===0.U || io.invtlb.op===1.U){
        tlb(i).e:=0.U
      }
      when(io.invtlb.op===2.U){
        when(tlb(i).g.asBool){ 
         tlb(i).e:=0.U; 
        }
      }
      when(io.invtlb.op===3.U){
        when(~tlb(i).g.asBool){
          tlb(i).e:=0.U; 
      }
      }
      when(io.invtlb.op===4.U){
        when(~tlb(i).g.asBool && tlb(i).asid===io.invtlb.asid){
          tlb(i).e:=0.U; 
        }
      }
      when(io.invtlb.op===5.U){
        when(~tlb(i).g.asBool && tlb(i).asid===io.invtlb.asid &&
             Mux(ps4kb,tlb(i).vppn===io.invtlb.va,tlb(i).vppn(18,10)===io.invtlb.va(18,10))){
          tlb(i).e:=0.U; 
        }
      }
      when(io.invtlb.op===6.U){
        when((~tlb(i).g.asBool || tlb(i).asid===io.invtlb.asid) &&
             Mux(ps4kb,tlb(i).vppn===io.invtlb.va,tlb(i).vppn(18,10)===io.invtlb.va(18,10))){
          tlb(i).e:=0.U; 
        }
      }
    }
  }
}
