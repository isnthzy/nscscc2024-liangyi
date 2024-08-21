import chisel3._
import chisel3.util._
import config.Configs._
import Control._
import config.GenCtrl
import config.BtbParams._

//NOTE:LS承担接受访存结果，ex2执行级
class LSU extends Module {
  val ls=IO(new Bundle {
    val in=Flipped(new ex_to_ls_bus_bundle())
    val to_wb=new ls_to_wb_bus_bundle()

    val from_mdu=Input(new from_mul_div_bundle())
    val fw_pf =Output(new pf_from_ls_bus_bundle())
    val fw_if =Output(new if_from_ls_bus_bundle())
    val fw_id =Output(new id_from_ls_bus_bundle())
    val fw_ih =Output(new ih_from_ls_bus_bundle())
    val fw_ex =Output(new ex_from_ls_bus_bundle())
    val tlb_excp=Input(new data_tlb_excp_bundle())
    val to_tlb=if(GenCtrl.USE_TLB) Some(Output(new ls_to_mmuctrl_bundle())) else None
    val to_csr=Output(new ls_to_csr_bundle())
    val ubtb_update = Output(new PredictorUpdate())
    // val btb_updata=new Bundle {
    //   val taken=Bool()
    //   val target=UInt(ADDR_WIDTH.W)
    // }
    val rc=new DataAxiBridgeRespondCannel()
    val tlb_excp_cancel_req=Output(Bool())
    val diff_tlbfill_idx=if(GenCtrl.USE_TLB) Some(Input(UInt(5.W))) else None
    val diff_paddr=Input(UInt(32.W))
  })
  val ls_valid=dontTouch(RegInit(VecInit(Seq.fill(2)(false.B))))
  val ls_clog=dontTouch(Wire(Vec(2,Bool())))
  val ls_excp_en=dontTouch(Wire(Vec(2,Bool())))
  val ls_ready_go=Wire(Vec(2,Bool()))
  val real_valid=Wire(Vec(2,Bool()))
  for(i<-0 until 2){
    when(ls.in.allowin){
      ls_valid(i):=ls.in.dualmask(i)
    }
  }
  for(i<-0 until 2){
    real_valid(i):=ls_valid(i) && ~ls_excp_en(i)
  }
  ls_ready_go(0):= ~ls_clog(0)||ls_excp_en(0)
  ls_ready_go(1):= ~ls_clog(1)||ls_excp_en.asUInt.orR
  ls.in.allowin:= ~(ls_valid(0)||ls_valid(1))|| ls_ready_go.asUInt.andR && ls.to_wb.allowin
  ls.to_wb.dualmask(0):=ls_valid(0)&&ls_ready_go.asUInt.andR
  ls.to_wb.dualmask(1):=ls_valid(1)&&ls_ready_go.asUInt.andR&& ~ls_excp_en(0)
  val ls_bits=Wire(Vec(2,new ls_to_wb_bus_data_bundle()))
//------------------------load单元------------------------
  //TODO:统一命名格式
  val mem_data=dontTouch(Wire(UInt(32.W)))
  val ls_addrLow2Bit=MuxPriorA(ls.in.bits(0).ld_en,ls.in.bits).addr_low2bit
  val ls_ld_type=MuxPriorA(ls.in.bits(0).ld_en,ls.in.bits).ld_type
  val load_byte_data_map=Map(
    "b00".U -> ls.rc.rdata(7 , 0),
    "b01".U -> ls.rc.rdata(15, 8),
    "b10".U -> ls.rc.rdata(23,16),
    "b11".U -> ls.rc.rdata(31,24)
  )
  val load_byte_data=Mux1hMap(ls_addrLow2Bit,load_byte_data_map)

  val load_half_data_map=Map(    
    "b00".U -> ls.rc.rdata(15, 0), 
    "b01".U -> ls.rc.rdata(15, 0),
    "b10".U -> ls.rc.rdata(31,16), 
    "b11".U -> ls.rc.rdata(31,16)
  )
  val load_half_data=Mux1hMap(ls_addrLow2Bit,load_half_data_map)

  mem_data:=Mux1H(Seq(
    ls_ld_type(OneHotDef(LD_LW)) -> ls.rc.rdata,
    ls_ld_type(OneHotDef(LD_LH)) -> Sext(load_half_data,32),
    ls_ld_type(OneHotDef(LD_LB)) -> Sext(load_byte_data,32),
    ls_ld_type(OneHotDef(LD_LHU))-> Zext(load_half_data,32),
    ls_ld_type(OneHotDef(LD_LBU))-> Zext(load_byte_data,32),
    ls_ld_type(OneHotDef(LD_LLW))-> ls.rc.rdata,
  ))

  for(i<-0 until 2){
    ls_clog(i):=ls.in.bits(i).ld_en&& ~ls.rc.data_ok&&ls_valid(i)
  }
//------------------------load单元------------------------
  //NOTE:双alu机制，当第二个指令是alu且和第一条指令有冲突时，第二条指令在ls级被计算
  val ExtraAlu=Module(new Alu())
  val result2_is_ExtraAlu=ls.in.bits(1).relate_src1||ls.in.bits(1).relate_src2
  ExtraAlu.io.op:=ls.in.bits(1).delay_alu_op
  ExtraAlu.io.src1:=Mux(ls.in.bits(1).relate_src1,ls.in.bits(0).result,ls.in.bits(1).delay_src1)
  ExtraAlu.io.src2:=Mux(ls.in.bits(1).relate_src2,ls.in.bits(0).result,ls.in.bits(1).delay_src2)

  val ls_result=Wire(Vec(2,UInt(DATA_WIDTH.W)))
  val ls_final_result=Wire(Vec(2,UInt(DATA_WIDTH.W)))
  for(i<-0 until 2){
    ls_result(i):=Mux1H(Seq(
      ls.in.bits(i).wb_sel(OneHotDef(WB_ALU)) ->  ls.in.bits(i).result,
      ls.in.bits(i).wb_sel(OneHotDef(WB_MEM)) ->  mem_data,
      ls.in.bits(i).wb_sel(OneHotDef(WB_DIV)) ->  ls.from_mdu.div_result,
      ls.in.bits(i).wb_sel(OneHotDef(WB_MOD)) ->  ls.from_mdu.mod_result,
      ls.in.bits(i).wb_sel(OneHotDef(WB_MULL))->  ls.from_mdu.mul_result(31,0),
      ls.in.bits(i).wb_sel(OneHotDef(WB_MULH))->  ls.from_mdu.mul_result(63,32),
      ls.in.bits(i).wb_sel(OneHotDef(WB_SCW ))->  (ls.in.bits(i).is_sc_w&&real_valid(i)),
    ))
  }
  ls_final_result(0):=ls_result(0)
  ls_final_result(1):=Mux(result2_is_ExtraAlu,ExtraAlu.io.result,ls_result(1))
  // ls_result(0):=ls.in.bits(0).result
  // ls_result(1):=Mux(result2_is_ExtraAlu,ExtraAlu.io.result,ls.in.bits(1).result)
  
  val ls_alu_result=Wire(Vec(2,UInt(DATA_WIDTH.W)))
  ls_alu_result(0):=ls.in.bits(0).result //TODO:上方组合逻辑过长，需要优化
  ls_alu_result(1):=Mux(result2_is_ExtraAlu,ExtraAlu.io.result,ls.in.bits(1).result)

  for(i<-0 until 2){
    var can_bypass=ls.in.bits(i).wb_sel(OneHotDef(WB_ALU))
    // ls.fw_ih.bits(i).bypass_unready:=ls.fw_ih.bits(i).rf.wen&& ~can_bypass
    ls.fw_ih.bits(i).bypass_unready:=ls.fw_ih.bits(i).rf.wen&& ~ls_ready_go(i)
    ls.fw_ih.bits(i).rf.wen:=ls.in.bits(i).rf_wen&&real_valid(i)
    ls.fw_ih.bits(i).rf.waddr:=ls.in.bits(i).rf_addr
    ls.fw_ih.bits(i).rf.wdata:=ls_final_result(i)
  }

  //当前设计update口只有一个，从四路中选择一个，优先0 优先jmp,jmp都更新cond跳转再更新？
  val update_pred = Wire(new PredictorUpdate())
  update_pred := PriorityMux(Seq(
    ((ls.in.bits(0).isBrJmp  && ls_valid(0)) -> ls.in.bits(0).brjump_result),
    ((ls.in.bits(0).isBrCond && ls_valid(0) && ls.in.bits(0).brcond_result.brTaken ) -> ls.in.bits(0).brcond_result),
    ((ls.in.bits(1).isBrJmp  && ls_valid(1)) -> ls.in.bits(1).brjump_result),
    // ((ls.in.bits(1).isBrCond && ls_valid(1) && ls.in.bits(0).brcond_result.brTaken ) -> ls.in.bits(1).brcond_result)
  ))

  ls.ubtb_update := update_pred
//NOTE:LS做为例外处理的终点
//---------------------------excp---------------------------
/*
excp_num[0]  int
        [1]  adef
        [2]  tlbr    |inst tlb exceptions
        [3]  pif     |
        [4]  ppi     |
        [5]  syscall
        [6]  brk
        [7]  ine
        [8]  ipe
        [9]  ale
        [10] <null>
        [11] tlbr    |
        [12] pme     |data tlb exceptions
        [13] ppi     |
        [14] pis     |
        [15] pil     |
*/
  val ls_excp_type=dontTouch(Wire(Vec(2,new ls_excp_bundle())))
  val excp_result=Wire(Vec(2,new excp_result_bundle()))
  for(i<-0 until 2){
    var ls_error_va=ls.in.bits(i).bad_addr //NOTE:result正好是alu_result因此可以用来给error_va
    ls_excp_type(i).pil :=ls.tlb_excp.pix &&(ls.in.bits(i).ld_en||ls.in.bits(i).cacop_en)&&ls_valid(i)
    ls_excp_type(i).pis :=ls.tlb_excp.pix && ls.in.bits(i).st_en&&ls_valid(i)
    ls_excp_type(i).ppi :=ls.tlb_excp.ppi &&(ls.in.bits(i).st_en||ls.in.bits(i).ld_en||ls.in.bits(i).cacop_en)&&ls_valid(i)
    ls_excp_type(i).pme :=ls.tlb_excp.pme && ls.in.bits(i).st_en&&ls_valid(i)
    ls_excp_type(i).tlbr:=ls.tlb_excp.tlbr&&(ls.in.bits(i).st_en||ls.in.bits(i).ld_en||ls.in.bits(i).cacop_en)&&ls_valid(i)
    ls_excp_type(i).zero:=false.B
    ls_excp_type(i).num :=ls.in.bits(i).excp_type
    ls_excp_en(i):=ls_excp_type(i).asUInt.orR
    
    var excp_num=ls_excp_type(i).asUInt
    var ls_pc=ls.in.bits(i).pc //NOTE:作用是改个名，你也不想看到那么长的名字对吧
    excp_result(i):=MuxCase(0.U,Seq(
      //NOTE:            ecode      va_error   va_bad_addr  esubcode      tlbrefill   tlb_excp
      excp_num(0) -> Cat(ECODE.INT ,0.U(1.W)   ,0.U(32.W)  ,0.U(9.W)     ,0.U(1.W)   ,0.U(1.W)),
      excp_num(1) -> Cat(ECODE.ADEF,ls_valid(i),ls_pc      ,ESUBCODE.ADEF,0.U(1.W)   ,0.U(1.W)),
      excp_num(2) -> Cat(ECODE.TLBR,ls_valid(i),ls_pc      ,0.U(9.W)     ,ls_valid(i),ls_valid(i)),
      excp_num(3) -> Cat(ECODE.PIF ,ls_valid(i),ls_pc      ,0.U(9.W)     ,0.U(1.W)   ,ls_valid(i)),
      excp_num(4) -> Cat(ECODE.PPI ,ls_valid(i),ls_pc      ,0.U(9.W)     ,0.U(1.W)   ,ls_valid(i)),
      excp_num(5) -> Cat(ECODE.SYS ,0.U(1.W)   ,0.U(32.W)  ,0.U(9.W)     ,0.U(1.W)   ,0.U(1.W)),
      excp_num(6) -> Cat(ECODE.BRK ,0.U(1.W)   ,0.U(32.W)  ,0.U(9.W)     ,0.U(1.W)   ,0.U(1.W)),
      excp_num(7) -> Cat(ECODE.INE ,0.U(1.W)   ,0.U(32.W)  ,0.U(9.W)     ,0.U(1.W)   ,0.U(1.W)),
      excp_num(8) -> Cat(ECODE.IPE ,0.U(1.W)   ,0.U(32.W)  ,0.U(9.W)     ,0.U(1.W)   ,0.U(1.W)),
      excp_num(9) -> Cat(ECODE.ALE ,ls_valid(i),ls_error_va,0.U(9.W)     ,0.U(1.W)   ,0.U(1.W)),
      excp_num(11)-> Cat(ECODE.TLBR,ls_valid(i),ls_error_va,0.U(9.W)     ,ls_valid(i),ls_valid(i)),
      excp_num(12)-> Cat(ECODE.PME ,ls_valid(i),ls_error_va,0.U(9.W)     ,0.U(1.W)   ,ls_valid(i)),
      excp_num(13)-> Cat(ECODE.PPI ,ls_valid(i),ls_error_va,0.U(9.W)     ,0.U(1.W)   ,ls_valid(i)),
      excp_num(14)-> Cat(ECODE.PIS ,ls_valid(i),ls_error_va,0.U(9.W)     ,0.U(1.W)   ,ls_valid(i)),
      excp_num(15)-> Cat(ECODE.PIL ,ls_valid(i),ls_error_va,0.U(9.W)     ,0.U(1.W)   ,ls_valid(i)),
    )).asTypeOf(new excp_result_bundle())
    //NOTE:虽然异常处理在ls级就结束了，但是仍然需要顺着流水到wb级中，再给diffcommit提交
  }
  ls.to_csr.csr_wen:=ls.in.bits(0).csr.wen&&real_valid(0)||ls.in.bits(1).csr.wen&&real_valid(1)
  ls.to_csr.wr_addr:=MuxPriorA(ls.in.bits(0).csr.wen,ls.in.bits).csr.addr
  ls.to_csr.wr_data:=MuxPriorA(ls.in.bits(0).csr.wen,ls.in.bits).csr.wdata
  ls.to_csr.excp_flush:=ls_excp_en(0)&&ls_valid(0)||ls_excp_en(1)&&ls_valid(1)
  ls.to_csr.ertn_flush:=ls.in.bits(0).csr.ertn_en&&real_valid(0)||ls.in.bits(1).csr.ertn_en&&real_valid(1)
  ls.to_csr.era_in    :=MuxPriorA(ls_excp_en(0),ls.in.bits).pc
  ls.to_csr.excp_result:=MuxPriorA(ls_excp_en(0),excp_result)
  ls.to_csr.llbit_set_en:=((ls.in.bits(0).is_ll_w||ls.in.bits(0).is_sc_w)&&real_valid(0)
                        || (ls.in.bits(1).is_ll_w||ls.in.bits(1).is_sc_w)&&real_valid(1))
  ls.to_csr.llbit_in  :=((ls.in.bits(0).is_ll_w&&real_valid(0)
                        ||ls.in.bits(1).is_ll_w&&real_valid(1))&1.U(1.W))
  //TODO:
  val to_pipeline_flush=Wire(new pipeline_flushs_bundle())
  val refetch_flush=Wire(Vec(2,Bool()))
  for(i<-0 until 2){
    refetch_flush(i):=(ls.in.bits(i).csr.wen||ls.in.bits(i).cacop_en
                     ||ls.in.bits(i).tlb_refetch||ls.in.bits(i).csr.idle_en
                     ||ls.in.bits(i).is_ll_w||ls.in.bits(i).is_sc_w)&&real_valid(i)
  }
  to_pipeline_flush.refetch:=refetch_flush.asUInt.orR
  to_pipeline_flush.excp   :=ls.to_csr.excp_flush
  to_pipeline_flush.idle   :=ls.in.bits(0).csr.idle_en&&real_valid(0)||ls.in.bits(1).csr.idle_en&&real_valid(1)
  to_pipeline_flush.ertn   :=ls.to_csr.ertn_flush
  to_pipeline_flush.tlbrefill:=excp_result(0).tlbrefill||excp_result(1).tlbrefill
  ls.fw_pf.flush:=to_pipeline_flush
  ls.fw_pf.refetch_pc:=MuxPriorA(refetch_flush(0),ls.in.bits).pc+4.U
  ls.fw_if.flush:=to_pipeline_flush
  ls.fw_id.flush:=to_pipeline_flush
  ls.fw_ih.flush:=to_pipeline_flush
  ls.fw_ex.flush:=to_pipeline_flush
  //NOTE:虽然异常处理在ls级就结束了，但是仍然需要顺着流水到wb级中，再给diffcommit提交
  for(i<-0 until 2){
    ls_bits(i).diffExcp.valid:=DontCare
    ls_bits(i).diffExcp.eret:=ls.in.bits(i).csr.ertn_en
    ls_bits(i).diffExcp.cause:=ls.to_csr.excp_result.ecode
    ls_bits(i).diffExcp.intrNo:=DontCare
    ls_bits(i).diffExcp.pc:=MuxPriorA(ls_excp_en(0),ls_bits).pc
    ls_bits(i).diffExcp.inst:=MuxPriorA(ls_excp_en(0),ls_bits).inst
  }

//--------------------------  TLB -------------------------
  if(GenCtrl.USE_TLB){
    ls.to_tlb.get.tlbrd_en   :=ls.in.bits(0).csr.op===SDEF(TLB_RD)&&real_valid(0)||ls.in.bits(1).csr.op===SDEF(TLB_RD)&&real_valid(1)
    ls.to_tlb.get.tlbwr_en   :=ls.in.bits(0).csr.op===SDEF(TLB_WR)&&real_valid(0)||ls.in.bits(1).csr.op===SDEF(TLB_WR)&&real_valid(1)
    ls.to_tlb.get.tlbsrch_wen:=ls.in.bits(0).csr.op===SDEF(TLB_SRCH)&&real_valid(0)||ls.in.bits(1).csr.op===SDEF(TLB_SRCH)&&real_valid(1)
    //NOTE:tlbsrch在ex级复用s1通道发起查找，由于tlb是两拍查找得结果，因此在ls级tlb得到结果并向csr写入结果
    ls.to_tlb.get.tlbfill_en :=ls.in.bits(0).csr.op===SDEF(TLB_FILL)&&real_valid(0)||ls.in.bits(1).csr.op===SDEF(TLB_FILL)&&real_valid(1)

    ls.to_tlb.get.invtlb_en  :=ls.in.bits(0).csr.op===SDEF(TLB_INV)&&real_valid(0)||ls.in.bits(1).csr.op===SDEF(TLB_INV)&&real_valid(1)
    ls.to_tlb.get.invtlb_op  :=MuxPriorA(ls.in.bits(0).csr.op===SDEF(TLB_INV)&&real_valid(0),ls.in.bits).rf_addr
    ls.to_tlb.get.invtlb_asid:=MuxPriorA(ls.in.bits(0).csr.op===SDEF(TLB_INV)&&real_valid(0),ls.in.bits).invtlb.asid
    ls.to_tlb.get.invtlb_va  :=MuxPriorA(ls.in.bits(0).csr.op===SDEF(TLB_INV)&&real_valid(0),ls.in.bits).invtlb.va
  }
  for(i<-0 until 2){
    ls_bits(i).diffInstr.is_TLBFILL:=ls.in.bits(i).csr.op===SDEF(TLB_FILL)&&real_valid(i)
    ls_bits(i).diffInstr.TLBFILL_index:=ls.diff_tlbfill_idx.getOrElse(0.U)
  }
  ls.tlb_excp_cancel_req:=(ls_excp_type(0).pil|ls_excp_type(0).pis|ls_excp_type(0).ppi|ls_excp_type(0).pme|ls_excp_type(0).tlbr
                         | ls_excp_type(1).pil|ls_excp_type(1).pis|ls_excp_type(1).ppi|ls_excp_type(1).pme|ls_excp_type(1).tlbr)
//--------------------------  TLB -------------------------

//NOTE:perf
  for(i<-0 until 2){
    ls_bits(i).perf_sys_quit:=ls.in.bits(i).perf_sys_quit
  }
  ls_bits(0).perf_branch.taken:=ls.in.bits(0).perf_branch.taken||ls.in.bits(1).perf_branch.taken
  ls_bits(0).perf_branch.br_type:=MuxPriorA(ls.in.bits(0).perf_branch.taken,ls.in.bits).perf_branch.br_type
  ls_bits(1).perf_branch:=0.U.asTypeOf(ls_bits(1).perf_branch)
 

  //------------------------------流水级总线------------------------------
  for(i<-0 until 2){
    ls_bits(i).diffLoad:=ls.in.bits(i).diffLoad
    ls_bits(i).diffStore:=ls.in.bits(i).diffStore
    ls_bits(i).diffLoad.paddr :=ls.diff_paddr
    ls_bits(i).diffStore.paddr:=ls.diff_paddr
    ls_bits(i).diffInstr.csr_data  :=ls.in.bits(i).diffInstr.csr_data
    ls_bits(i).diffInstr.csr_rstat :=ls.in.bits(i).diffInstr.csr_rstat
    ls_bits(i).diffInstr.is_CNTinst:=ls.in.bits(i).diffInstr.is_CNTinst
    ls_bits(i).diffInstr.timer64value:=ls.in.bits(i).diffInstr.timer64value

    ls_bits(i).excp_en:=ls_excp_en(i)
    ls_bits(i).rf_wen:=ls.in.bits(i).rf_wen&&real_valid(i)
    ls_bits(i).rf_addr:=ls.in.bits(i).rf_addr
    ls_bits(i).wb_sel:=ls.in.bits(i).wb_sel
    ls_bits(i).is_scw:=ls.in.bits(i).is_sc_w
    ls_bits(i).alu_result:=ls_result(i)
    ls_bits(i).mem_result:=mem_data
    ls_bits(i).div_result:=ls.from_mdu.div_result
    ls_bits(i).mod_result:=ls.from_mdu.mod_result
    ls_bits(i).mul_result:=ls.from_mdu.mul_result
    ls_bits(i).result:=ls_final_result(i)
    ls_bits(i).pc    :=ls.in.bits(i).pc
    ls_bits(i).inst  :=ls.in.bits(i).inst
  }
  ls.to_wb.bits:=ls_bits

  //prediction counter
  val pred = Wire(Vec(FetchWidth, new PredictorOutput()))
  for( i <- 0 until FetchWidth ){
    pred(i) := ls.in.bits(i).pred
  }

}

