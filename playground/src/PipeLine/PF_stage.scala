import chisel3._
import chisel3.util._
import config.Configs._
import config.CacheConfig._
// import coursier.util.Config
// import config.GenCtrl


class PreIFU extends Module {
  val pf=IO(new Bundle {
    val to_if=Decoupled(new pf_to_if_bus_bundle())
    val queueAllowIn=Input(Bool())
    
    val from_if=Input(new pf_from_if_bus_bundle())
    val from_id=Input(new pf_from_id_bus_bundle())
    val from_ih=Input(new pf_from_ih_bus_bundle())
    val from_ex=Input(new pf_from_ex_bus_bundle())
    val from_ls=Input(new pf_from_ls_bus_bundle())
    val csr_entries=Input(new csr_entrise_bundle())
    val direct_uncache=Input(Bool())
    val sc=new InstAxiBridgeSendCannel()
    val icacop_en=Output(Bool())
    val icacop_mode=Output(UInt(2.W))

    // val invalid_if_second = Output(new pf_invalid_if_inst_bundle)

    val to_pred = Output(new PredictorInput())
    // val to_pred_1 = Output(new PredictorInput())
    val from_pred0 = Input(new PredictorOutput())
    val from_pred1 = Input(new PredictorOutput())
  })
  val flush_sign=dontTouch(Wire(Bool()))
  val pf_excp_en=Wire(Bool())
  val pf_flush =pf.from_id.br_j.taken||pf.from_ex.br_b.taken||pf.from_if.pre_uncache_miss||flush_sign 
  val idle_lock=RegInit(false.B)
  val to_pf_valid= ~reset.asBool&& ~idle_lock
  val fetch_req=(pf.to_if.ready && to_pf_valid && ~pf_flush  && ~pf_excp_en&& ~idle_lock)&& pf.queueAllowIn
  val icacop_change_req_addr=RegInit(false.B)
  val pf_ready_go=((pf.to_if.ready&&(pf.sc.addr_ok&& ~icacop_change_req_addr)&& ~idle_lock)|| pf_excp_en)&& pf.queueAllowIn
  pf.sc.addr_en:=pf.to_if.valid
  pf.to_if.valid:=Mux(pf_flush,false.B,to_pf_valid&&pf_ready_go)

  when(pf.from_ls.flush.idle&& ~pf.from_ih.has_int){
    idle_lock:=true.B
  }.elsewhen(pf.from_ih.has_int){
    idle_lock:=false.B
  }

  val icacop_mode_reg=RegInit(0.U(2.W))
  val icacop_addr_reg=RegInit(0.U(ADDR_WIDTH.W))
  when(pf.from_ex.icacop_en){
    icacop_change_req_addr:=true.B
    icacop_mode_reg:=pf.from_ex.icacop_mode
    icacop_addr_reg:=pf.from_ex.icacop_addr
  }
  when(pf.sc.addr_ok){
    icacop_change_req_addr:=false.B
  }
  pf.icacop_en:=icacop_change_req_addr
  pf.icacop_mode:=icacop_mode_reg

  val nextpc_plus8=dontTouch(Wire(Bool()))
  val pending_nextpc=RegInit(false.B)
  when((flush_sign||pf.from_id.br_j.taken||pf.from_ex.br_b.taken)&& ~pf.from_if.pre_uncache_miss){
    pending_nextpc:=true.B
  }
  when(fetch_req||pf.from_if.pre_uncache_miss){
    pending_nextpc:=false.B
  }
  //from Predictor
  // val hit = pf.from_pred0.read_hit
  val brTaken = pf.from_pred0.brTaken || (pf.from_pred1.brTaken&&nextpc_plus8)
  val brTarget = Mux(pf.from_pred0.brTaken, pf.from_pred0.entry.brTarget, pf.from_pred1.entry.brTarget)


  flush_sign:=pf.from_ls.flush.asUInt.orR
  val flushed_pc =Mux(pf.from_ls.flush.tlbrefill,pf.csr_entries.tlbentry,
                    Mux(pf.from_ls.flush.excp,pf.csr_entries.entry,
                      Mux(pf.from_ls.flush.refetch,pf.from_ls.refetch_pc,
                        Mux(pf.from_ls.flush.ertn,pf.csr_entries.era,0.U))))
  
  val pf_pc = dontTouch(RegInit(START_ADDR))
  // val REGpc = RegInit(START_ADDR)
  val pc_no_cross_cacheline=pf_pc(OFFSET_WIDTH-1,2)=/=Sext(1.U,OFFSET_WIDTH-2)//pc没有发生跨cacheline
  nextpc_plus8:=pc_no_cross_cacheline && ~pf.direct_uncache
  // val snpc  = Mux(hit && brTaken, brTarget,
  val snpc  = Mux(brTaken.orR, brTarget,
                Mux(pc_no_cross_cacheline && ~pf.direct_uncache, pf_pc+8.U, pf_pc+4.U))
  val dnpc  = Mux(flush_sign,flushed_pc,
                  Mux(pf.from_ex.br_b.taken,pf.from_ex.br_b.target,
                    Mux(pf.from_id.br_j.taken,pf.from_id.br_j.target,
                      Mux(pf.from_if.pre_uncache_miss&&pending_nextpc,pf_pc,pf.from_if.miss_pc))))

  dontTouch(snpc)
  dontTouch(dnpc)
  val nextpc=Mux(pf_flush,dnpc,snpc)
  //NOTE:当转移计算未完成时，不允许跳转（反例bne判断跳转后，无法取消跳转）
  when((pf_ready_go&&pf.to_if.ready)||pf_flush){
    pf_pc:=nextpc
  }


//---------------------------AXI BRIDEG---------------------------
  pf.sc.ren:=fetch_req
  pf.sc.addr:=Mux(icacop_change_req_addr,icacop_addr_reg,pf_pc)
  
  pf.sc.wen:=false.B
  pf.sc.size:=0.U
  pf.sc.wstrb:=0.U
  pf.sc.wdata:=0.U
//---------------------------AXI BRIDEG---------------------------
//---------------------------excp---------------------------
  val pf_excp_adef=(pf_pc(0)||pf_pc(1))
  val pf_excp=Wire(new pf_excp_bundle())
  pf_excp_en:=pf_excp_adef
  pf_excp.num:=pf_excp_adef
//---------------------------excp---------------------------
  pf.to_if.bits.pc:=pf_pc
  pf.to_if.bits.nextpc:=nextpc
  pf.to_if.bits.excp_en:=pf_excp_en
  pf.to_if.bits.excp_type:=pf_excp
  pf.to_if.bits.direct_uncache:=pf.direct_uncache
  pf.to_if.bits.pred0 := pf.from_pred0
  pf.to_if.bits.pred1 := pf.from_pred1

//---------------------------Predictor---------------------------
  pf.to_pred.bp_fire := fetch_req
  pf.to_pred.req_pc  := pf_pc

}