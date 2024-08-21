import chisel3._
import chisel3.util._
import config.Configs._
import config.CacheConfig._
import config.GenCtrl

class IFU extends Module {
  val fs=IO(new Bundle {
    val in=Flipped(Decoupled(new pf_to_if_bus_bundle()))
    val to_id=new if_to_id_bus_bundle()
  
    val fw_pf=Output(new pf_from_if_bus_bundle()) //TODO:如果发现预测cache预测错误，就冲刷
    val from_id=Input(new if_from_id_bus_bundle())
    val from_ex=Input(new if_from_ex_bus_bundle())
    val from_ls=Input(new if_from_ls_bus_bundle())
    val tlb_excp=Input(new inst_tlb_excp_bundle())

    val page_uncache=Input(Bool())
    val icache_uncache=Output(Bool())
    val tlb_excp_cancel_req=Output(Bool())

    val rc=new InstAxiBridgeRespondCannel()
  })
  val if_dual_inst=dontTouch(WireDefault(0.U(64.W)))     //NOTE:if_inst的值
  val if_inst_discard=RegInit(false.B)              //NOTE:检测到取到错误的指令，拉高信号，指示丢弃
  val if_flush=(fs.from_id.br_flush   || fs.from_ex.br_flush   || fs.from_ls.flush.asUInt.orR) //NOTE:用来flush流水线  
  val if_excp_en=Wire(Bool())
  val instFetch_bits=dontTouch(Wire(Vec(2,new if_to_id_bus_data_bundle())))
  
  val if_valid_r=RegInit(false.B)
  val if_valid  =if_valid_r&& ~if_flush 
  //NOTE:对flush的行为的一种升级,这样处理可以确保flush是清除当前流水级以及当前流水级后面流水级的代码
  val if_ready_go=dontTouch(Wire(Bool()))
  val if_clog= ~fs.rc.data_ok || if_inst_discard //是要丢弃的就阻塞住
                                                                 //后端队列满的时候不能再塞了
  if_ready_go:=Mux(if_clog&& ~if_excp_en,false.B,true.B)
  fs.in.ready:= ~if_valid_r || if_ready_go 
  when(if_flush){
    if_valid_r:=false.B
  }.elsewhen(fs.in.ready){ 
    if_valid_r:=fs.in.valid
  }
  instFetch_bits(0).pc  :=fs.in.bits.pc
  instFetch_bits(0).inst:=if_dual_inst(31,0)
  instFetch_bits(1).pc  :=fs.in.bits.pc+4.U
  instFetch_bits(1).inst:=if_dual_inst(63,32)

  val page_uncache_buffer=RegInit(false.B)
  when(fs.in.ready&&if_ready_go||if_flush){
    page_uncache_buffer:=false.B
  }.elsewhen(fs.page_uncache){
    page_uncache_buffer:=true.B
  }
  fs.fw_pf.pre_uncache_miss:=(fs.page_uncache||page_uncache_buffer)&&(fs.in.ready&&if_ready_go)&& ~if_flush
  fs.fw_pf.miss_pc:=Mux(fs.in.bits.pred0.brTaken,fs.in.bits.nextpc,fs.in.bits.pc+4.U)

  val can_dual=((instFetch_bits(0).pc(OFFSET_WIDTH-1,2)=/=Sext(1.U,OFFSET_WIDTH-2))
              && ~(fs.page_uncache||page_uncache_buffer||fs.in.bits.direct_uncache)&& ~reset.asBool)
  dontTouch(can_dual)
  //NOTE:双发取指不能跨cacheline,pf默认预测cache,如果是uncache就对pf和cache抛冲刷重取指
  //NOTE:双发射掩码，00两条指令都无效,01为第一条指令有效，11为两条指令均有效


  // fs.to_id.dualmask:=MuxCase(Issue.SIGL,Seq(
  //   (if_flush|| ~(if_valid_r&&if_ready_go))  -> Issue.ZERO,
  //   if_excp_en                               -> fs.queueAllowIn.asUInt,
  //   (can_dual& ~fs.in.bits.pred.firstTaken)-> Issue.DUAL
  // )).asTypeOf(Vec(2,Bool())) //TODO:发生异常时，fs.queueAllowIn为低怎么办，已分析完成，与握手设计有关

  fs.to_id.dualmask:=MuxCase(Issue.SIGL,Seq(
    (if_flush|| ~(if_valid_r&&if_ready_go))  -> Issue.ZERO,
    if_excp_en                               -> Issue.SIGL,
    (can_dual& ~fs.in.bits.pred0.brTaken)    -> Issue.DUAL
  )).asTypeOf(Vec(2,Bool())) //TODO:发生异常时，fs.queueAllowIn为低怎么办，已分析完成，与握手设计有关

  when(if_flush&& ~fs.in.ready&& ~if_ready_go&& ~fs.rc.addr_ok){
    if_inst_discard:=true.B
  } //NOTE:addr_ok为1时说明总线空闲，不是取错指情况，不需要discard
  when(fs.rc.data_ok){
    if_inst_discard:=false.B
  }
  if_dual_inst:=Mux(if_excp_en,"h03400000".U(32.W),fs.rc.rdata)
  /*NOTE:当出现例外时，把inst置为0，避免因为残留的inst出现其他没有预料的操作。
    因为if和pf级产生的例外优先级高于指令invalid例外，所以放心大胆的置为0*/

  fs.icache_uncache:=fs.page_uncache||fs.in.bits.direct_uncache
//---------------------------excp---------------------------
  val if_excp_type=Wire(new if_excp_bundle())
  /*NOTE:pf级对内存发起访存的是一个地址返回两个数据，因此tlb只需要一个端口就可以完成访存转换
    因而返回的例外是pc发生的例外而不是pc+4,直接走单发处理流程
    因为pc不能跨cache line双发，所以不会跨页tlb转换
  */
  if_excp_type.ppi :=fs.tlb_excp.ppi
  if_excp_type.pif :=fs.tlb_excp.pif
  if_excp_type.tlbr:=fs.tlb_excp.tlbr
  if_excp_type.num :=fs.in.bits.excp_type  
  if_excp_en  :=if_excp_type.asUInt.orR
  instFetch_bits(0).excp_en:=if_excp_en
  instFetch_bits(0).excp_type:=if_excp_type
  instFetch_bits(1).excp_en:=DontCare
  instFetch_bits(1).excp_type:=DontCare

  fs.tlb_excp_cancel_req:=fs.tlb_excp.ppi|fs.tlb_excp.pif|fs.tlb_excp.tlbr

  instFetch_bits(0).pred := fs.in.bits.pred0
  instFetch_bits(1).pred := fs.in.bits.pred1
//---------------------------excp---------------------------

//------------------------流水级总线------------------------
  fs.to_id.bits:=instFetch_bits
}

object Issue { 
  def SIGL=1.U  //全称SINGLE（单发）
  def DUAL=3.U
  def ZERO=0.U
}