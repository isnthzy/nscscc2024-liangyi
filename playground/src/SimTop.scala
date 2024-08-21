import chisel3._
import chisel3.util._
import config.Configs._
import config.GenCtrl
import config.CacheConfig._
/*TODO:改一下握手方式，改成雷思磊那种的？感觉chisel抽象一下也用不到多少代码，而且相比cpu设计实战的写法
  雷思磊那本书做多发可能会更简单？ ---------握手已全部龙芯化
  TODO:前递目标寄存器为0时不阻塞,load指令要判断目标地址是否相同再阻塞
*/


class SimTop extends Module {
  val io = IO(new Bundle {
    val intrpt   = Input(UInt(8.W))
    // AXI read address channel signals
    val arid     = Output(UInt(4.W)) 
    val araddr   = Output(UInt(32.W)) 
    val arlen    = Output(UInt(8.W)) 
    val arsize   = Output(UInt(3.W)) 
    val arburst  = Output(UInt(2.W)) 
    val arlock   = Output(UInt(2.W)) 
    val arcache  = Output(UInt(4.W)) 
    val arprot   = Output(UInt(3.W)) 
    val arvalid  = Output(Bool()) 
    val arready  = Input(Bool()) 

    // AXI read data channel signals
    val rid      = Input(UInt(4.W)) 
    val rdata    = Input(UInt(32.W)) 
    val rresp    = Input(UInt(2.W)) 
    val rlast    = Input(Bool()) 
    val rvalid   = Input(Bool()) 
    val rready   = Output(Bool()) 

    // AXI write address channel signals
    val awid     = Output(UInt(4.W)) 
    val awaddr   = Output(UInt(32.W)) 
    val awlen    = Output(UInt(8.W)) 
    val awsize   = Output(UInt(3.W)) 
    val awburst  = Output(UInt(2.W)) 
    val awlock   = Output(UInt(2.W)) 
    val awcache  = Output(UInt(4.W)) 
    val awprot   = Output(UInt(3.W)) 
    val awvalid  = Output(Bool()) 
    val awready  = Input(Bool()) 

    // AXI write data channel signals
    val wid      = Output(UInt(4.W)) 
    val wdata    = Output(UInt(32.W)) 
    val wstrb    = Output(UInt(4.W)) 
    val wlast    = Output(Bool()) 
    val wvalid   = Output(Bool()) 
    val wready   = Input(Bool()) 

    // AXI write response channel signals
    val bid      = Input(UInt(4.W)) 
    val bresp    = Input(UInt(2.W)) 
    val bvalid   = Input(Bool()) 
    val bready   = Output(Bool()) 

    val break_point=Input(Bool())
    val infor_flag =Input(Bool())
    val reg_num    =Input(UInt(5.W))
    val ws_valid   =Output(Bool())
    val rf_rdata   =Output(UInt(DATA_WIDTH.W))

    val debug0_wb_pc      =Output(UInt(ADDR_WIDTH.W))
    val debug0_wb_rf_wen  =Output(UInt(4.W))
    val debug0_wb_rf_wnum =Output(UInt(5.W))
    val debug0_wb_rf_wdata=Output(UInt(DATA_WIDTH.W))
  
    val debug1_wb_pc      =Output(UInt(ADDR_WIDTH.W))
    val debug1_wb_rf_wen  =Output(UInt(4.W))
    val debug1_wb_rf_wnum =Output(UInt(5.W))
    val debug1_wb_rf_wdata=Output(UInt(DATA_WIDTH.W))
  })

  {
    val RESET = "\u001B[0m"
    val RED = "\u001B[31m"
    val GREEN = "\u001B[32m"
    if(GenCtrl.FAST_SIM){
      println("Fast sim is "+GREEN+"ON"+RESET)
    }else{
      println("Fast sim is "+RED+"OFF"+RESET)
    }
    if(GenCtrl.USE_DIFF){
      println("Difftest is "+GREEN+"ON"+RESET)
    }else{
      println("Difftest is "+RED+"OFF"+RESET)
    }
    if(GenCtrl.PERF_CNT){
      println("Perf     is "+GREEN+"ON"+RESET)
    }else{
      println("Perf     is "+RED+"OFF"+RESET)
    }
    if(GenCtrl.USE_TLB){
      println("Tlb Generate is "+GREEN+"ON"+RESET)
    }else{
      println("Tlb Generate is "+RED+"OFF"+RESET)
    }


  }
  
  val AxiBridge=Module(new AxiBridge())

  io.arid:=AxiBridge.io.arid
  io.araddr:=AxiBridge.io.araddr
  io.arlen:=AxiBridge.io.arlen
  io.arsize:=AxiBridge.io.arsize
  io.arburst:=AxiBridge.io.arburst
  io.arlock:=AxiBridge.io.arlock
  io.arcache:=AxiBridge.io.arcache
  io.arprot:=AxiBridge.io.arprot
  io.arvalid:=AxiBridge.io.arvalid
  AxiBridge.io.arready:=io.arready

  AxiBridge.io.rid:=io.rid
  AxiBridge.io.rdata:=io.rdata
  AxiBridge.io.rresp:=io.rresp
  AxiBridge.io.rlast:=io.rlast
  AxiBridge.io.rvalid:=io.rvalid
  io.rready:=AxiBridge.io.rready

  io.awid:=AxiBridge.io.awid
  io.awaddr:=AxiBridge.io.awaddr
  io.awlen:=AxiBridge.io.awlen
  io.awsize:=AxiBridge.io.awsize
  io.awburst:=AxiBridge.io.awburst
  io.awlock:=AxiBridge.io.awlock
  io.awcache:=AxiBridge.io.awcache
  io.awprot:=AxiBridge.io.awprot
  io.awvalid:=AxiBridge.io.awvalid
  AxiBridge.io.awready:=io.awready

  io.wid:=AxiBridge.io.wid
  io.wdata:=AxiBridge.io.wdata
  io.wstrb:=AxiBridge.io.wstrb
  io.wlast:=AxiBridge.io.wlast
  io.wvalid:=AxiBridge.io.wvalid
  AxiBridge.io.wready:=io.wready

  AxiBridge.io.bid:=io.bid
  AxiBridge.io.bresp:=io.bresp
  AxiBridge.io.bvalid:=io.bvalid
  io.bready:=AxiBridge.io.bready

  val icache=Module(new MyICache(1<<INDEX_WIDTH, 1<<OFFSET_WIDTH, WAY_NUM_I, USE_LRU))
  val dcache=Module(new MyDCache(1<<INDEX_WIDTH, 1<<OFFSET_WIDTH, WAY_NUM_D, USE_LRU))

  AxiBridge.io.inst.rd_req:=icache.io.rd_req
  AxiBridge.io.inst.rd_type:=icache.io.rd_type
  AxiBridge.io.inst.rd_addr:=icache.io.rd_addr
  icache.io.rd_rdy:=AxiBridge.io.inst.rd_rdy
  icache.io.ret_valid:=AxiBridge.io.inst.ret_valid
  icache.io.ret_last :=AxiBridge.io.inst.ret_last
  icache.io.ret_data :=AxiBridge.io.inst.ret_data
  AxiBridge.io.inst.wr_req:=icache.io.wr_req
  AxiBridge.io.inst.wr_type:=icache.io.wr_type
  AxiBridge.io.inst.wr_addr:=icache.io.wr_addr
  AxiBridge.io.inst.wr_wstrb:=icache.io.wr_wstrb
  AxiBridge.io.inst.wr_data:=icache.io.wr_data
  icache.io.wr_rdy:=AxiBridge.io.inst.wr_rdy

  AxiBridge.io.data.rd_req:=dcache.io.rd_req
  AxiBridge.io.data.rd_type:=dcache.io.rd_type
  AxiBridge.io.data.rd_addr:=dcache.io.rd_addr
  dcache.io.rd_rdy:=AxiBridge.io.data.rd_rdy
  dcache.io.ret_valid:=AxiBridge.io.data.ret_valid
  dcache.io.ret_last :=AxiBridge.io.data.ret_last
  dcache.io.ret_data :=AxiBridge.io.data.ret_data
  AxiBridge.io.data.wr_req:=dcache.io.wr_req
  AxiBridge.io.data.wr_type:=dcache.io.wr_type
  AxiBridge.io.data.wr_addr:=dcache.io.wr_addr
  AxiBridge.io.data.wr_wstrb:=dcache.io.wr_wstrb
  AxiBridge.io.data.wr_data:=dcache.io.wr_data
  dcache.io.wr_rdy:=AxiBridge.io.data.wr_rdy
  
  val CsrUnit=Module(new CsrFile())
  val MmuCtrl=Module(new MmuCtrl())
  val pfu=Module(new PreIFU())
  val ifu=Module(new IFU())
  val idu=Module(new IDU())
  val ihu=Module(new IHU())
  val exu=Module(new EXU())
  val lsu=Module(new LSU())
  val wbu=Module(new WBU())
  /*TODO:理论发射队列有位置就可以一直存?
  所以我们只需控制pf发起取指，就可以实现前端握手？
  */
  CsrUnit.io.interrupt:=io.intrpt
  CsrUnit.io.mmutranIO<>MmuCtrl.io.mmutranIO
  if(GenCtrl.USE_TLB){
    CsrUnit.io.tlbtranIO.get<>MmuCtrl.io.tlbtranIO.get
  }


//pfu
  pfu.pf.queueAllowIn:=idu.id.QinstAllowIn
  pfu.pf.from_if:=ifu.fs.fw_pf
  pfu.pf.from_id:=idu.id.fw_pf
  pfu.pf.from_ih:=ihu.ih.fw_pf
  pfu.pf.from_ex:=exu.ex.fw_pf
  pfu.pf.from_ls:=lsu.ls.fw_pf
  pfu.pf.csr_entries:=CsrUnit.io.csr_entries
  pfu.pf.direct_uncache:=MmuCtrl.io.inst.direct_uncache
  MmuCtrl.io.inst.vaddr:=pfu.pf.sc.addr
  MmuCtrl.io.inst.addr_en:=pfu.pf.sc.addr_en

  icache.io.valid:=pfu.pf.sc.ren || pfu.pf.sc.wen
  icache.io.op   :=pfu.pf.sc.wen
  icache.io.index:=pfu.pf.sc.addr(31-TAG_WIDTH,OFFSET_WIDTH)
  icache.io.tag  :=MmuCtrl.io.inst.paddr(31,31-TAG_WIDTH+1) //这个cache的tag延后一拍在cache里处理
  icache.io.offset:=pfu.pf.sc.addr(OFFSET_WIDTH-1,0)
  icache.io.uncached:=ifu.fs.icache_uncache
  icache.io.cacop_en := pfu.pf.icacop_en&&pfu.pf.sc.ren
  icache.io.cacop_op := pfu.pf.icacop_mode
  pfu.pf.sc.addr_ok:=icache.io.addr_ok
  pfu.pf.sc.data_ok:=icache.io.data_ok

  ifu.fs.rc.data_ok:=icache.io.data_ok
  ifu.fs.rc.rdata  :=Cat(icache.io.rdata_h,icache.io.rdata_l)
  ifu.fs.rc.addr_ok:=icache.io.addr_ok
  ifu.fs.page_uncache:=MmuCtrl.io.inst.page_uncache

//ifu
  StageConnect(pfu.pf.to_if,ifu.fs.in)
  ifu.fs.from_id:=idu.id.fw_if
  ifu.fs.from_ex:=exu.ex.fw_if
  ifu.fs.from_ls:=lsu.ls.fw_if
  ifu.fs.tlb_excp:=MmuCtrl.io.inst.excp
  icache.io.tlb_excp_cancel_req:=ifu.fs.tlb_excp_cancel_req
//TODO:前后端解耦
//idu
  idu.id.in.dualmask:=ifu.fs.to_id.dualmask
  idu.id.in.bits:=ifu.fs.to_id.bits
  idu.id.to_ih.allowin:=ihu.ih.in.allowin
  //NOTE:译码级与issue_hazard之间不设流水间寄存器，因为本身queue会耽误一周期
  idu.id.from_ex:=exu.ex.fw_id
  idu.id.from_ls:=lsu.ls.fw_id

//ihu
  ihu.ih.in.dualmask:=idu.id.to_ih.dualmask
  ihu.ih.in.bits:=idu.id.to_ih.bits
  //NOTE:译码级与issue_hazard之间不设流水间寄存器，因为本身queue会耽误一周期
  ihu.ih.from_ex:=exu.ex.fw_ih
  ihu.ih.from_ls:=lsu.ls.fw_ih
  ihu.ih.from_wb:=wbu.wb.fw_ih
  ihu.ih.from_csr<>CsrUnit.io.from_csr
//exu
  val MulDivUnit=Module(new MulDivUnit())
  ihu.ih.to_ex.allowin:=exu.ex.in.allowin
  exu.ex.in.dualmask:=ihu.ih.to_ex.dualmask
  for(i<-0 until 2){
    exu.ex.in.bits(i):=RegEnable(ihu.ih.to_ex.bits(i),
                                 0.U.asTypeOf(new ih_to_ex_bus_data_bundle()),
                                 ihu.ih.to_ex.dualmask(i)&&exu.ex.in.allowin)
  }
  exu.ex.from_ls:=lsu.ls.fw_ex
  for(i<-0 until 2){
    MulDivUnit.io.to_mdu<>exu.ex.to_mdu
  }
  MmuCtrl.io.data.addr_en:=false.B
  MmuCtrl.io.data.vaddr:=exu.ex.sc.addr
  MmuCtrl.io.data.addr_en:=exu.ex.sc.addr_en
  if(GenCtrl.USE_TLB){
    MmuCtrl.io.from_ex.get:=exu.ex.to_tlb.get
  }

  dcache.io.valid:=exu.ex.sc.ren || exu.ex.sc.wen // || exu.ex.ctrl_cache.dcacop_en NOTE :casued combinational cycle????
  dcache.io.op   :=Mux(exu.ex.ctrl_cache.dcacop_en,Cat(1.U(1.W),exu.ex.ctrl_cache.cacop_mode),exu.ex.sc.wen.asUInt)
  dcache.io.index:=exu.ex.sc.addr(31-TAG_WIDTH,OFFSET_WIDTH)
  dcache.io.tag  :=MmuCtrl.io.data.paddr(31,31-TAG_WIDTH+1) //这个cache的tag延后一拍在cache里处理
  dcache.io.offset:=exu.ex.sc.addr(OFFSET_WIDTH-1,0)
  dcache.io.wstrb :=exu.ex.sc.wstrb
  dcache.io.size  :=exu.ex.sc.size
  dcache.io.wdata :=exu.ex.sc.wdata
  dcache.io.uncached:=MmuCtrl.io.data.uncache
  dcache.io.cacop_en := exu.ex.ctrl_cache.dcacop_en
  dcache.io.cacop_op := exu.ex.ctrl_cache.cacop_mode
  exu.ex.sc.addr_ok:=dcache.io.addr_ok
  lsu.ls.rc.data_ok:=dcache.io.data_ok
  lsu.ls.rc.rdata  :=dcache.io.rdata
  //Predictor
  val ubtb = Module(new uBtb())
  // val ubtb = Module(new BTB())
  // ubtb.io.in_0 <> pfu.pf.to_pred
  ubtb.io.in_0 := pfu.pf.to_pred
  ubtb.io.in_1.bp_fire := pfu.pf.to_pred.bp_fire
  ubtb.io.in_1.req_pc  := pfu.pf.to_pred.req_pc + 4.U
  // secondPred.bp_fire := pfu.pf.to_pred.bp_fire
  // secondPred.req_pc  := pfu.pf.to_pred.req_pc + 4.U
  // ubtb.io.in_1 := secondPred

  // ubtb.io.in_1.bp_fire := pfu.pf.to_pred.bp_fire
  // ubtb.io.in_1.req_pc  := pfu.pf.to_pred.req_pc + 4.U
  // val updatePred = Wire(new PredictorUpdate())
  // updatePred.valid := false.B
  // updatePred.pc := 0.U
  // updatePred.brTaken := false.B
  // updatePred.entry := 0.U.asTypeOf(new BTBEntry)
  ubtb.io.update := lsu.ls.ubtb_update
  pfu.pf.from_pred0 := ubtb.io.out_0
  pfu.pf.from_pred1 := ubtb.io.out_1

//lsu
  exu.ex.to_ls.allowin:=lsu.ls.in.allowin
  lsu.ls.in.dualmask:=exu.ex.to_ls.dualmask
  for(i<-0 until 2){
    lsu.ls.in.bits(i):=RegEnable(exu.ex.to_ls.bits(i),
                                 0.U.asTypeOf(new ex_to_ls_bus_data_bundle()),
                                 exu.ex.to_ls.dualmask(i)&&lsu.ls.in.allowin)
    
    lsu.ls.from_mdu:=MulDivUnit.io.from_mdu
  }
  CsrUnit.io.to_csr:=lsu.ls.to_csr
  lsu.ls.tlb_excp:=MmuCtrl.io.data.excp
  if(GenCtrl.USE_TLB){
    MmuCtrl.io.from_ls.get:=lsu.ls.to_tlb.get
    lsu.ls.diff_tlbfill_idx.get:=MmuCtrl.io.diff_tlbfill_idx.get
  }
  dcache.io.tlb_excp_cancel_req:=lsu.ls.tlb_excp_cancel_req
  lsu.ls.diff_paddr:=MmuCtrl.io.data.paddr
//wbu
  lsu.ls.to_wb.allowin:=wbu.wb.in.allowin
  wbu.wb.in.dualmask:=lsu.ls.to_wb.dualmask
  for(i<-0 until 2){
    wbu.wb.in.bits(i):=RegEnable(lsu.ls.to_wb.bits(i),
                                 0.U.asTypeOf(new ls_to_wb_bus_data_bundle()),
                                 lsu.ls.to_wb.dualmask(i)&&wbu.wb.in.allowin)
  }
  io.ws_valid:=0.U
  io.rf_rdata:=0.U
  io.debug0_wb_pc:=wbu.wb.diffInstrCommit(0).pc
  io.debug0_wb_rf_wdata:=wbu.wb.diffInstrCommit(0).wdata
  io.debug0_wb_rf_wen  := VecInit(Seq.fill(4)(wbu.wb.diffInstrCommit(0).wen)).asUInt
  io.debug0_wb_rf_wnum :=wbu.wb.diffInstrCommit(0).wdest

  io.debug1_wb_pc:=wbu.wb.diffInstrCommit(1).pc
  io.debug1_wb_rf_wdata:=wbu.wb.diffInstrCommit(1).wdata
  io.debug1_wb_rf_wen  := VecInit(Seq.fill(4)(wbu.wb.diffInstrCommit(1).wen)).asUInt
  io.debug1_wb_rf_wnum :=wbu.wb.diffInstrCommit(1).wdest

  if(GenCtrl.USE_DIFF){
    val DiffCommit=Module(new DiffCommit())
    DiffCommit.io.instr:=wbu.wb.diffInstrCommit
    DiffCommit.io.load :=wbu.wb.diffLoadCommit
    DiffCommit.io.store:=wbu.wb.diffStoreCommit
    DiffCommit.io.excp :=wbu.wb.diffExcpCommit
    DiffCommit.io.excp.intrNo:=CsrUnit.io.diff_csr.estat
    DiffCommit.io.reg  :=ihu.ih.diff_reg
    DiffCommit.io.csr  :=CsrUnit.io.diff_csr
  }
}




object StageConnect {
  def apply[T <: Data](out: DecoupledIO[T], in: DecoupledIO[T]) = {
    out.ready:=in.ready
    in.valid:=out.valid
    in.bits <> RegEnable(out.bits,out.fire) 
  }
}
