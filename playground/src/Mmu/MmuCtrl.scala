import chisel3._
import chisel3.util._
import config.Configs._
import chisel3.experimental.BundleLiterals._
import config.GenCtrl

class MmuCtrl extends Module{
  //根据不同级发出的飞线进行绑线操作
  val io=IO(new Bundle { //TODO:打包成bundle
    //from_pf
    val inst=new inst_vaddr_to_paddr_bundle()
    //from_ex
    val data=new data_vaddr_to_paddr_bundle()

    val from_ls=if(GenCtrl.USE_TLB) Some(Input(new ls_to_mmuctrl_bundle())) else None
    val from_ex=if(GenCtrl.USE_TLB) Some(Input(new ex_to_mmuctrl_bundle())) else None
    //csr_io
    val tlbtranIO=if(GenCtrl.USE_TLB) Some(Flipped(new tlbtran_io_bundle())) else None
    val mmutranIO=Flipped(new mmutran_io_bundle())
    //to_diff
    val diff_tlbfill_idx=if(GenCtrl.USE_TLB) Some(Output(UInt(log2Ceil(TLB_NUM).W))) else None
  }) 
  /*NOTE:分为两个模式，use_tlb和unuse_tlb.
    unuse_tlb模式包括直接地址翻译模式和直接映射模式，这两个模式的映射均能当拍出结果
    页表涉及到了tlb查询，需要下一拍出结果*/
  val tlb=if(GenCtrl.USE_TLB) Some(Module(new TlbUnit())) else None

  val pg_mode= ~io.mmutranIO.to_mmu.da &&  io.mmutranIO.to_mmu.pg
  val da_mode=  io.mmutranIO.to_mmu.da && ~io.mmutranIO.to_mmu.pg
  val inst_addrtran=Module(new AddrTran())
  val data_addrtran=Module(new AddrTran())
  val insttran_en_next=RegNext(io.inst.addr_en)
  inst_addrtran.io.tran_en:=io.inst.addr_en
  inst_addrtran.io.vaddr:=io.inst.vaddr
  inst_addrtran.io.mode.pg:=pg_mode
  inst_addrtran.io.mode.da:=da_mode
  inst_addrtran.io.dmw0:=io.mmutranIO.to_mmu.dmw0
  inst_addrtran.io.dmw1:=io.mmutranIO.to_mmu.dmw1
  inst_addrtran.io.datx:=io.mmutranIO.to_mmu.datf
  inst_addrtran.io.plv:=io.mmutranIO.to_mmu.plv
  if(GenCtrl.USE_TLB){
    inst_addrtran.io.tlb_ps4kb.get:=tlb.get.io.s(0).ps4kb
    inst_addrtran.io.tlb_ppn.get  :=tlb.get.io.s(0).ppn
    inst_addrtran.io.tlb_mat.get  :=tlb.get.io.s(0).mat
  }
  io.inst.paddr:=inst_addrtran.io.paddr
  if(GenCtrl.FROCE_ICACHE){
    io.inst.direct_uncache:=false.B
    io.inst.page_uncache  :=false.B
  }else{
    io.inst.direct_uncache:=inst_addrtran.io.direct_uncache
    io.inst.page_uncache  :=inst_addrtran.io.page_uncache
  }
  dontTouch(io.inst.direct_uncache)
  dontTouch(io.inst.page_uncache)
  


  val datatran_en_next=RegNext(io.data.addr_en)
  data_addrtran.io.tran_en:=io.data.addr_en
  data_addrtran.io.vaddr:=io.data.vaddr
  data_addrtran.io.mode.pg:=pg_mode
  data_addrtran.io.mode.da:=da_mode
  data_addrtran.io.dmw0:=io.mmutranIO.to_mmu.dmw0
  data_addrtran.io.dmw1:=io.mmutranIO.to_mmu.dmw1
  data_addrtran.io.datx:=io.mmutranIO.to_mmu.datm
  data_addrtran.io.plv:=io.mmutranIO.to_mmu.plv
  if(GenCtrl.USE_TLB){
    data_addrtran.io.tlb_ps4kb.get:=tlb.get.io.s(1).ps4kb
    data_addrtran.io.tlb_ppn.get  :=tlb.get.io.s(1).ppn
    data_addrtran.io.tlb_mat.get  :=tlb.get.io.s(1).mat
  }
  io.data.paddr:=data_addrtran.io.paddr
  if(GenCtrl.FROCE_DCACHE){
    io.data.uncache:=false.B
  }else{
    io.data.uncache:=data_addrtran.io.page_uncache||RegNext(data_addrtran.io.direct_uncache)
  }
  dontTouch(io.data.uncache)

  //NOTE:data不需要区分page_uncache和direct_uncache

//NOTE:例外
  if (GenCtrl.USE_TLB){
  
    //NOTE:hit等消息都是下一拍才出的，因此需要用next手动delay一拍保持同步
    //NOTE:traninst_en也一样
    io.inst.excp.tlbr:=RegNext(inst_addrtran.io.is_page_map)&&insttran_en_next&& ~tlb.get.io.s(0).hit 
    io.inst.excp.pif :=RegNext(inst_addrtran.io.is_page_map)&&insttran_en_next&& ~tlb.get.io.s(0).v.asBool
    io.inst.excp.ppi :=RegNext(inst_addrtran.io.is_page_map)&&insttran_en_next&& (tlb.get.io.s(0).plv < io.mmutranIO.to_mmu.plv) && tlb.get.io.s(0).v.asBool
    //NOTE:if级发现是tlb例外时停止pf继续取指，防止发错请求
  
    //NOTE:hit等消息都是下一拍才出的，因此需要用next手动delay一拍保持同步
    io.data.excp.tlbr:=RegNext(data_addrtran.io.is_page_map)&&datatran_en_next&& ~tlb.get.io.s(1).hit 
    io.data.excp.pix :=RegNext(data_addrtran.io.is_page_map)&&datatran_en_next&& ~tlb.get.io.s(1).v.asBool
    //NOTE:pix意思是pil(load)和pis(store),是什么要在ls级判断
    io.data.excp.ppi :=RegNext(data_addrtran.io.is_page_map)&&datatran_en_next&& (tlb.get.io.s(1).plv < io.mmutranIO.to_mmu.plv) && tlb.get.io.s(1).v.asBool
    io.data.excp.pme :=(RegNext(data_addrtran.io.is_page_map)&&datatran_en_next&& (tlb.get.io.s(1).plv>= io.mmutranIO.to_mmu.plv) &&
                         tlb.get.io.s(1).v.asBool && ~tlb.get.io.s(1).d.asBool) 
                        //NOTE:具体判断细节仍要在ls级进行
  }else{
    io.inst.excp:=0.U.asTypeOf(io.inst.excp)
    io.data.excp:=0.U.asTypeOf(io.data.excp)
  }

  if(GenCtrl.USE_TLB){
    tlb.get.io.s(0).fetch:=io.inst.addr_en 
    tlb.get.io.s(0).vppn :=io.inst.vaddr(31,13)
    tlb.get.io.s(0).va_12:=io.inst.vaddr(12)
    tlb.get.io.s(0).asid :=io.tlbtranIO.get.to_tlb.asid.asid

    tlb.get.io.s(1).fetch:=io.data.addr_en || io.from_ex.get.tlbsrch_en
    tlb.get.io.s(1).vppn :=Mux(io.from_ex.get.is_tlbsrch,io.tlbtranIO.get.to_tlb.tlbehi.vppn,io.data.vaddr(31,13))
    tlb.get.io.s(1).va_12:=io.data.vaddr(12)
    tlb.get.io.s(1).asid :=io.tlbtranIO.get.to_tlb.asid.asid



  //NOTE:tlbsrch
    io.tlbtranIO.get.to_csr.tlbsrch_wen:=io.from_ls.get.tlbsrch_wen
    io.tlbtranIO.get.to_csr.tlbsrch_hit:=tlb.get.io.s(1).hit
    io.tlbtranIO.get.to_csr.tlbidx.idx:=tlb.get.io.s(1).index

  //NOTE:tlbrd
    io.tlbtranIO.get.to_csr.tlbrd_en:=io.from_ls.get.tlbrd_en
    tlb.get.io.r_idx:=io.tlbtranIO.get.to_tlb.tlbidx.idx
    io.tlbtranIO.get.to_csr.asid.asid:=tlb.get.io.r.asid
    //
    io.tlbtranIO.get.to_csr.tlbidx.ps:= tlb.get.io.r.ps
    io.tlbtranIO.get.to_csr.tlbidx.ne:= ~tlb.get.io.r.e
    //
    io.tlbtranIO.get.to_csr.tlbehi.vppn:=tlb.get.io.r.vppn
    io.tlbtranIO.get.to_csr.tlbelo0.v  := tlb.get.io.r.v0
    io.tlbtranIO.get.to_csr.tlbelo0.d  := tlb.get.io.r.d0
    io.tlbtranIO.get.to_csr.tlbelo0.plv:= tlb.get.io.r.plv0
    io.tlbtranIO.get.to_csr.tlbelo0.mat:= tlb.get.io.r.mat0
    io.tlbtranIO.get.to_csr.tlbelo0.g  := tlb.get.io.r.g
    io.tlbtranIO.get.to_csr.tlbelo0.ppn:= tlb.get.io.r.ppn0

    io.tlbtranIO.get.to_csr.tlbelo1.v  := tlb.get.io.r.v1
    io.tlbtranIO.get.to_csr.tlbelo1.d  := tlb.get.io.r.d1
    io.tlbtranIO.get.to_csr.tlbelo1.plv:= tlb.get.io.r.plv1
    io.tlbtranIO.get.to_csr.tlbelo1.mat:= tlb.get.io.r.mat1
    io.tlbtranIO.get.to_csr.tlbelo1.g  := tlb.get.io.r.g
    io.tlbtranIO.get.to_csr.tlbelo1.ppn:= tlb.get.io.r.ppn1

    io.tlbtranIO.get.to_csr.asid.asidbits   :=DontCare
    io.tlbtranIO.get.to_csr.asid.zero15_10  :=DontCare
    io.tlbtranIO.get.to_csr.asid.zero31_24  :=DontCare
    io.tlbtranIO.get.to_csr.tlbidx.zero23_16:=DontCare
    io.tlbtranIO.get.to_csr.tlbidx.zero30   :=DontCare
    io.tlbtranIO.get.to_csr.tlbehi.zero12_0 :=DontCare
    io.tlbtranIO.get.to_csr.tlbelo0.zero7   :=DontCare
    io.tlbtranIO.get.to_csr.tlbelo1.zero7   :=DontCare
  //NOTE:tlbwr || tlbfill
    io.diff_tlbfill_idx.get:=io.tlbtranIO.get.to_tlb.rand_idx
    tlb.get.io.w_en:=io.from_ls.get.tlbwr_en || io.from_ls.get.tlbfill_en
    tlb.get.io.w_idx:=MuxCase(0.U,Seq(
      io.from_ls.get.tlbwr_en  -> io.tlbtranIO.get.to_tlb.tlbidx.idx,
      io.from_ls.get.tlbfill_en-> io.tlbtranIO.get.to_tlb.rand_idx,
    ))
    tlb.get.io.w.asid:= io.tlbtranIO.get.to_tlb.asid.asid

    tlb.get.io.w.ps  := io.tlbtranIO.get.to_tlb.tlbidx.ps
    tlb.get.io.w.e   := Mux(io.tlbtranIO.get.to_tlb.ecode3f,1.U,~io.tlbtranIO.get.to_tlb.tlbidx.ne)
    tlb.get.io.w.vppn:= io.tlbtranIO.get.to_tlb.tlbehi.vppn
    tlb.get.io.w.g   := (io.tlbtranIO.get.to_tlb.tlbelo0.g.asBool
                       &&io.tlbtranIO.get.to_tlb.tlbelo1.g.asBool)
    tlb.get.io.w.v0  := io.tlbtranIO.get.to_tlb.tlbelo0.v
    tlb.get.io.w.d0  := io.tlbtranIO.get.to_tlb.tlbelo0.d
    tlb.get.io.w.plv0:= io.tlbtranIO.get.to_tlb.tlbelo0.plv
    tlb.get.io.w.mat0:= io.tlbtranIO.get.to_tlb.tlbelo0.mat
    tlb.get.io.w.ppn0:= io.tlbtranIO.get.to_tlb.tlbelo0.ppn
    tlb.get.io.w.v1  := io.tlbtranIO.get.to_tlb.tlbelo1.v
    tlb.get.io.w.d1  := io.tlbtranIO.get.to_tlb.tlbelo1.d
    tlb.get.io.w.plv1:= io.tlbtranIO.get.to_tlb.tlbelo1.plv
    tlb.get.io.w.mat1:= io.tlbtranIO.get.to_tlb.tlbelo1.mat
    tlb.get.io.w.ppn1:= io.tlbtranIO.get.to_tlb.tlbelo1.ppn


    //NOTE:invtlb
    tlb.get.io.invtlb.en:=io.from_ls.get.invtlb_en
    tlb.get.io.invtlb.op:=io.from_ls.get.invtlb_op
    tlb.get.io.invtlb.asid:=io.from_ls.get.invtlb_asid
    tlb.get.io.invtlb.va  :=io.from_ls.get.invtlb_va
  }
}