/* 
NOTE:谨记，涉及到ex级牵扯到的东西较多，如果发生异常，一定要及时取消(一些随着流水线刷的不需要考虑是否取消)

 */
import chisel3._
import chisel3.util._
import config.Configs._
import config.BtbParams._
import Control._
import config.GenCtrl

class EXU extends Module {
  val ex=IO(new Bundle {
    val in=Flipped(new ih_to_ex_bus_bundle())
    val to_ls=new ex_to_ls_bus_bundle()
    val to_mdu=new to_mul_div_bundle() //NOTE:乘除法器流水化，最多支持同时发射两条mul/div指令

    val fw_pf=Output(new pf_from_ex_bus_bundle())
    val fw_if=Output(new if_from_ex_bus_bundle())
    val fw_id=Output(new id_from_ex_bus_bundle())
    val fw_ih=Output(new ih_from_ex_bus_bundle())
    val from_ls=Input(new ex_from_ls_bus_bundle())
    // val from_csr=Flipped(new ex_with_csr())
    val to_tlb=if(GenCtrl.USE_TLB) Some(Output(new ex_to_mmuctrl_bundle())) else None
    val ctrl_cache=new ctrl_cache_bundle()
    val sc=new DataAxiBridgeSendCannel()
  })
  val ex_flush=ex.from_ls.flush.asUInt.orR
  val ex_valid_r=RegInit(VecInit(Seq.fill(2)(false.B)))
  val ex_excp_en=Wire(Vec(2,Bool()))
  val ex_clog=dontTouch(Wire(Vec(2,Bool())))
  val ex_valid=dontTouch(Wire(Vec(2,Bool())))
  val ex_bits=Wire(Vec(2,new ex_to_ls_bus_data_bundle()))
  val ex_ready_go=Wire(Vec(2,Bool()))
  val br_b_taken=Wire(Vec(2,Bool()))
  val flush_second_inst = WireInit(false.B)
  for(i<-0 until 2){
    when(ex_flush){
      ex_valid_r(i):=false.B
    }.elsewhen(ex.in.allowin){
      ex_valid_r(i):=ex.in.dualmask(i)
    }
  } //NOTE:不写到一个for里是因为上面是时序逻辑，下边是组合逻辑，容易搞混
  for(i<-0 until 2){
    ex_valid(i):=ex_valid_r(i) && ~ex_flush
  }
  ex_ready_go(0):= ~ex_clog(0)||ex_excp_en(0)
  ex_ready_go(1):= ~ex_clog(1)||ex_excp_en.asUInt.orR
  ex.in.allowin:= ~(ex_valid_r(0)||ex_valid_r(1))|| ex_ready_go.asUInt.andR && ex.to_ls.allowin
  ex.to_ls.dualmask(0):=ex_valid(0)&&ex_ready_go.asUInt.andR
  ex.to_ls.dualmask(1):=ex_valid(1)&&ex_ready_go.asUInt.andR&& ~ex_excp_en(0)&& ~flush_second_inst
//------------------------ 计算单元 ------------------------
  val Alu=Array.fill(2)(Module(new Alu()).io)
  val mul_en=Wire(Vec(2,Bool()))
  val div_en=Wire(Vec(2,Bool()))
  val mdu_signed=Wire(Vec(2,Bool()))
  for(i<-0 until 2){  
    Alu(i).op  :=ex.in.bits(i).alu_op
    Alu(i).src1:=ex.in.bits(i).src1
    Alu(i).src2:=ex.in.bits(i).src2
    mdu_signed(i):=ex.in.bits(i).alu_op===SDEF(MDU_SIGN)
    div_en(i):=ex.in.bits(i).inst_op===SDEF(OP_DIV)&&ex_valid(i) 
    mul_en(i):=ex.in.bits(i).inst_op===SDEF(OP_MUL)&&ex_valid(i) 
    ex_bits(i).result :=Mux(ex.in.bits(i).br_type(OneHotDef(J_JIRL)),ex.in.bits(i).pc+4.U,
      Mux(ex.in.bits(i).inst_op===SDEF(OP_CSR)||ex.in.bits(i).inst_op===SDEF(OP_CNT),ex.in.bits(i).csr.rdata,Alu(i).result))
  }
  ex.to_mdu.div_en:=div_en.asUInt.orR
  ex.to_mdu.signed:=MuxPriorA(div_en(0)||mul_en(0),mdu_signed)
  ex.to_mdu.src1  :=MuxPriorA(div_en(0)||mul_en(0),ex.in.bits).src1
  ex.to_mdu.src2  :=MuxPriorA(div_en(0)||mul_en(0),ex.in.bits).src2
  ex.to_mdu.divFastSimFlush:=ex_flush //NOTE:由于快速仿真模块是计数器模拟的，因此遇到冲刷要重置计数器避免出错
//------------------------ 计算单元 ------------------------
  for(i<-0 until 2){
    ex.fw_ih.bits(i).fw.wen:=ex_valid(i)&&ex.in.bits(i).rf_wen
    ex.fw_ih.bits(i).fw.waddr:=ex.in.bits(i).dest
    ex.fw_ih.bits(i).fw.wdata:=ex_bits(i).result
    ex.fw_ih.bits(i).bypass_unready:=(ex_bits(i).ld_en||ex_bits(i).st_en||ex.in.bits(i).st_type(OneHotDef(ST_SCW))
                                   || mul_en(i)||div_en(i)
                                   || ex_bits(i).relate_src1||ex_bits(i).relate_src2)&&ex_valid(i)
  }
//-----------------------条件分支单元-----------------------
  //NOTE:跳转同一时刻只有一个跳转（即不能同时发射两个跳转）
  val br_target=Wire(Vec(2,UInt(ADDR_WIDTH.W)))
  for(i<-0 until 2){
    var cmp_eq  =(ex.in.bits(i).src1 === ex.in.bits(i).src2)
    var cmp_slt =(ex.in.bits(i).src1.asSInt < ex.in.bits(i).src2.asSInt)
    var cmp_sltu=(ex.in.bits(i).src1  <  ex.in.bits(i).src2)
    br_b_taken(i):=(((ex.in.bits(i).br_type(OneHotDef(BR_BEQ))) &&  cmp_eq)
                  | ((ex.in.bits(i).br_type(OneHotDef(BR_BNE))) && !cmp_eq)
                  | ((ex.in.bits(i).br_type(OneHotDef(BR_BLT))) &&  cmp_slt)
                  | ((ex.in.bits(i).br_type(OneHotDef(BR_BLTU)))&&  cmp_sltu)
                  | ((ex.in.bits(i).br_type(OneHotDef(BR_BGE))) && !cmp_slt)
                  | ((ex.in.bits(i).br_type(OneHotDef(BR_BGEU)))&& !cmp_sltu)
                  | ( ex.in.bits(i).br_type(OneHotDef(J_JIRL))))&&ex_valid(i)
    br_target(i):=Mux(ex.in.bits(i).br_type(OneHotDef(J_JIRL)),ex.in.bits(i).src1+ex.in.bits(i).imm,ex.in.bits(i).pc+ex.in.bits(i).imm)
  }
  
  val taken = br_b_taken.asUInt.orR
  val target = Mux(br_b_taken(0),br_target(0),br_target(1))
  // val pred = Mux(ex_valid(0), ex.in.bits(0).pred, 0.U.asTypeOf(new PredictorOutput)) //TOD: is right?
  // val pred = ex.in.bits(0).pred
  val pred = Wire(Vec(FetchWidth, new PredictorOutput()))
  for( i <- 0 until FetchWidth ){
    pred(i) := ex.in.bits(i).pred
  }

  val predictorUpdate = Wire(Vec(FetchWidth, new PredictorUpdate()))
  for( i <- 0 until FetchWidth ){
    predictorUpdate(i).valid := (ex.in.bits(i).isBrCond && ex_valid(i)) //if inst is valid, need update
    predictorUpdate(i).pc := ex.to_ls.bits(i).pc
    predictorUpdate(i).brTaken := br_b_taken(i)
    predictorUpdate(i).entry.brTarget := br_target(i)
    predictorUpdate(i).entry.brType   := Mux(ex.in.bits(i).isBrCond, 1.U, 0.U)
  }

  /*
ih          pred      pred_target   redirect
|           |         |             |
taken       taken     target_right  nothing
                      target_wrong  target
            not taken               target
not taken   taken                   snpc   add 4 or 8?
            not taken               nothing
*/
  val bp_direction_right = Wire(Vec(FetchWidth, Bool()))
  val bp_target_right = Wire(Vec(FetchWidth, Bool()))
  val bp_right = Wire(Vec(FetchWidth, Bool()))
  val bp_wrong = Wire(Vec(FetchWidth, Bool()))
  val bp_wrong_redirect = Wire(Vec(FetchWidth, UInt(ADDR_WIDTH.W)))
  dontTouch(bp_wrong_redirect)
  val bpWRedirectTaken = Wire(Bool())
  val bpWRedirect = WireInit(0.U(ADDR_WIDTH.W))
  for( i <- 0 until FetchWidth ){
    bp_direction_right(i) := pred(i).brTaken === br_b_taken(i)
    bp_target_right(i) := br_b_taken(i) && bp_direction_right(i) && (pred(i).entry.brTarget === br_target(i))

    bp_right(i) := bp_direction_right(i) && (!br_b_taken(i) || bp_target_right(i))
    bp_wrong(i) :=(!bp_direction_right(i) || (br_b_taken(i) && !bp_target_right(i)))&&ex.in.bits(i).isBrCond
    bp_wrong_redirect(i) := Mux(bp_wrong(i) && br_b_taken(i), br_target(i),
                              Mux(bp_wrong(i) && !br_b_taken(i), ex.to_ls.bits(i).pc + 4.U, 0.U)) //
  }
  bpWRedirectTaken := (0 until FetchWidth).map{i => bp_wrong(i) && ex_valid(i) && ex.in.bits(i).isBrCond}.reduce(_ || _)
  bpWRedirect := Mux(bp_wrong(0) && ex_valid(0), bp_wrong_redirect(0), bp_wrong_redirect(1))

  //miss pred and is first inst
  flush_second_inst := bp_wrong(0) && ex.in.bits(0).isBrCond
  //TODO: flush need change
  ex.fw_pf.br_b.taken := bpWRedirectTaken
  ex.fw_pf.br_b.target := bpWRedirect
  ex.fw_if.br_flush := bpWRedirectTaken
  ex.fw_id.br_flush := bpWRedirectTaken
  ex.fw_ih.br_flush := bpWRedirectTaken
//-----------------------条件分支单元-----------------------
  //predictor prefcounter
  val bp_right_counter = RegInit(0.U(ADDR_WIDTH.W))
  val bp_wrong_counter = RegInit(0.U(ADDR_WIDTH.W))
  val bp_isCond_counter = RegInit(0.U(ADDR_WIDTH.W))
  dontTouch(bp_isCond_counter)
  dontTouch(bp_wrong_counter)
  if(GenCtrl.PERF_CNT){
    if(GenCtrl.PERF_CNT){
      when(ex.in.bits(0).isBrCond && ex.to_ls.dualmask(0)){
        bp_isCond_counter := bp_isCond_counter + 1.U
        bp_wrong_counter := Mux(bp_wrong(0) && pred(0).entry.brType === 1.U, bp_wrong_counter + 1.U, bp_wrong_counter)
      }.elsewhen(ex.in.bits(0).isBrCond && ex.to_ls.dualmask(1)){
        bp_isCond_counter := bp_isCond_counter+ 2.U
        bp_wrong_counter := Mux(bp_wrong(0) && pred(0).entry.brType === 1.U, bp_wrong_counter + 2.U, bp_wrong_counter)
      }
      for( i <- 0 until FetchWidth ){
        when(ex.in.bits(i).perf_sys_quit){
          printf("cond_cnt=%d cond_wrong_cnt=%d\n",bp_isCond_counter,bp_wrong_counter)
          printf("miss_rate=%d%%\n",((bp_wrong_counter.asSInt *100.asSInt)/bp_isCond_counter.asSInt))
          printf("---------------------------BTB_PERF---------------------------\n")
        }
      }
    }
  }


//-------------------------- CACOP-------------------------
  val ex_final_data_addr=Wire(UInt(ADDR_WIDTH.W))
  val ex_cacop_en=Wire(Vec(2,Bool()))
  val icacop_inst=Wire(Vec(2,Bool()))
  val dcacop_inst=Wire(Vec(2,Bool()))
  val cacop_mode =Wire(Vec(2,UInt(2.W)))
  val cache_tran_en=Wire(Vec(2,Bool())) 
  val is_icacop2=Wire(Vec(2,Bool()))
  //NOTE:cache_tran_en这个信号用于控制cacop指令发起时间。vipt需要在addr_ok后再发请求
  for(i<-0 until 2){
    ex_cacop_en(i):=(ex.in.bits(i).inst_op===SDEF(OP_CACP))
    cache_tran_en(i):=ex_valid(i)&& ~ex_excp_en(i)&&ex_ready_go.asUInt.andR && ex.to_ls.allowin
    icacop_inst(i):=ex_cacop_en(i)&&(ex.in.bits(i).dest(2,0)===0.U)
    dcacop_inst(i):=ex_cacop_en(i)&&(ex.in.bits(i).dest(2,0)===1.U)
    cacop_mode(i):=ex.in.bits(i).dest(4,3)
    ex_bits(i).cacop_en:=ex_cacop_en(i)
    is_icacop2(i):=(ex.in.bits(i).dest(4,3)===2.U)&&icacop_inst(i)
  }
  // ex.ctrl_cache.icacop_en:=icacop_inst(0)&&cache_tran_en(0)||icacop_inst(1)&&cache_tran_en(1)
  ex.ctrl_cache.dcacop_en:=dcacop_inst(0)&&cache_tran_en(0)||dcacop_inst(1)&&cache_tran_en(1)
  ex.ctrl_cache.cacop_mode:=MuxPriorA(ex_cacop_en(0),cacop_mode)
  ex.fw_pf.icacop_en:=icacop_inst(0)&&ex.to_ls.dualmask(0)||icacop_inst(1)&&ex.to_ls.dualmask(1)
  ex.fw_pf.icacop_addr:=ex_final_data_addr
  ex.fw_pf.icacop_mode:=MuxPriorA(icacop_inst(0),cacop_mode)
//-------------------------- CACOP-------------------------
//------------------------ 访存单元 ------------------------
  val ex_data_addr=Wire(Vec(2,UInt(ADDR_WIDTH.W)))
  for(i<-0 until 2){
    ex_bits(i).ld_en:= ex.in.bits(i).ld_en 
    ex_bits(i).st_en:= ex.in.bits(i).st_en  
    ex_data_addr(i):=ex.in.bits(i).src1+ex.in.bits(i).imm
    //TODO:修改发射条件使访存指令不再等待src2_ok
    ex_bits(i).addr_low2bit:=Alu(i).result
  }
//NOTE:ex级同时只能发起一个访存请求
  ex_final_data_addr:=Mux(ex.in.bits(0).ld_en||ex.in.bits(0).st_en||ex_cacop_en(0),ex_data_addr(0),ex_data_addr(1))
  val ex_st_type=Mux(ex.in.bits(0).st_en,ex.in.bits(0).st_type,ex.in.bits(1).st_type)
  val ex_st_src =Mux(ex.in.bits(0).st_en,ex.in.bits(0).src2,ex.in.bits(1).src2)
  val mem_size=Wire(Vec(2,UInt(2.W)))
  for(i<-0 until 2){
    var mem_b_size=(ex_bits(i).ld_type(OneHotDef(LD_LB))||ex_bits(i).ld_type(OneHotDef(LD_LBU))||ex.in.bits(i).st_type(OneHotDef(ST_SB)))
    var mem_h_size=(ex_bits(i).ld_type(OneHotDef(LD_LH))||ex_bits(i).ld_type(OneHotDef(LD_LHU))||ex.in.bits(i).st_type(OneHotDef(ST_SH)))
    mem_size(i):=Cat(mem_h_size,mem_b_size)
  }
  val ex_mem_size=Mux(ex.in.bits(0).ld_en||ex.in.bits(0).st_en,mem_size(0),mem_size(1))

  val ex_stb_sel=Cat(
    ex_final_data_addr(1,0)===3.U,
    ex_final_data_addr(1,0)===2.U,
    ex_final_data_addr(1,0)===1.U,
    ex_final_data_addr(1,0)===0.U
  )
  val ex_sth_sel=Cat(
    ex_final_data_addr(1,0)===2.U,
    ex_final_data_addr(1,0)===2.U,
    ex_final_data_addr(1,0)===0.U,
    ex_final_data_addr(1,0)===0.U
  )
  val ex_stb_cont=Cat(
    Fill(8,ex_stb_sel(3))&ex_st_src(7,0),
    Fill(8,ex_stb_sel(2))&ex_st_src(7,0),
    Fill(8,ex_stb_sel(1))&ex_st_src(7,0),
    Fill(8,ex_stb_sel(0))&ex_st_src(7,0)
  )
  val ex_sth_cont=Cat(
    Fill(16,ex_sth_sel(3))&ex_st_src(15,0),
    Fill(16,ex_sth_sel(0))&ex_st_src(15,0), 
  )
  val data_wstrb_with_size=(Fill(7,ex_mem_size(0))&Cat(ex_stb_sel,0.U(3.W))|
                            Fill(7,ex_mem_size(1))&Cat(ex_sth_sel,1.U(3.W))|
                            Fill(7,(!ex_mem_size))&Cat(15.U(4.W) ,2.U(3.W)))
  val ex_data_size=data_wstrb_with_size(2,0)
  val ex_data_wstrb=data_wstrb_with_size(6,3)
  val ex_data_wdata=(Fill(ADDR_WIDTH,ex_mem_size(0))&ex_stb_cont|
                     Fill(ADDR_WIDTH,ex_mem_size(1))&ex_sth_cont|
                     Fill(ADDR_WIDTH,(!ex_mem_size))&ex_st_src)

  for(i<-0 until 2){
    ex_bits(i).is_ll_w:=ex.in.bits(i).ld_type(OneHotDef(LD_LLW))
    ex_bits(i).is_sc_w:=ex.in.bits(i).st_type(OneHotDef(ST_SCW))&&ex.in.bits(i).st_en
  }//NOTE:这俩信号是后边判断是否写入llbit的，因此不用加valid
//----------------------- AXI BRIDEG ----------------------
  ex.sc.wen:=((ex_bits(0).st_en&&ex_valid(0)&& ~ex_excp_en(0))
            ||(ex_bits(1).st_en&&ex_valid(1)&& ~ex_excp_en.asUInt.orR&& ~flush_second_inst))
  ex.sc.size:=ex_data_size
  ex.sc.addr:=ex_final_data_addr
  ex.sc.wstrb:=ex_data_wstrb
  ex.sc.wdata:=ex_data_wdata
  ex.sc.ren:=((ex_bits(0).ld_en&&ex_valid(0)&& ~ex_excp_en(0))
            ||(ex_bits(1).ld_en&&ex_valid(1)&& ~ex_excp_en.asUInt.orR && ~flush_second_inst))
  //NOTE:为了提升双发率。默认允许第一条指令为bru指令时双发，只不过要主动取消
  //TODO:假设有个bug，第一条指令时excp第二条指令是访存，此时取消第二条指令的访存请求
  for(i<-0 until 2){
    ex_clog(i):=((( ~ex.sc.addr_ok&&ex_bits(i).st_en&& ~flush_second_inst)
                ||( ~ex.sc.addr_ok&&ex_bits(i).ld_en&& ~flush_second_inst)
                ||( ~ex.sc.addr_ok&&dcacop_inst(i)  && ~flush_second_inst)
                ||( div_en(i)&& ~ex.to_mdu.div_ok))&&ex_valid(i))
  }
  ex.sc.addr_en:=(((ex.sc.wen||ex.sc.ren)||dcacop_inst.asUInt.orR)&&ex.sc.addr_ok 
                ||(is_icacop2.asUInt.orR||ex.to_tlb.get.tlbsrch_en&&ex_ready_go.asUInt.orR&&ex.to_ls.allowin))
  //当addr_ok时才对tlb发起访存请求，保证了tlb得出tag后cache正好需要对比tag
//----------------------- AXI BRIDEG ----------------------
//--------------------------  CSR -------------------------
  for(i<-0 until 2){
    ex_bits(i).csr.op :=ex.in.bits(i).csr.op
    ex_bits(i).csr.wen:=ex.in.bits(i).csr.wen
    ex_bits(i).csr.addr:=ex.in.bits(i).csr.addr
    var csrWdataMap=Map(
      SDEF(CSR_WR)    -> ex.in.bits(i).src2,
      SDEF(CSR_XCHG)  -> ((ex.in.bits(i).src2&ex.in.bits(i).src1)|(ex.in.bits(i).csr.rdata& ~ex.in.bits(i).src1)),
    )
    ex_bits(i).csr.wdata:=Mux1hMap(ex_bits(i).csr.op,csrWdataMap)
    ex_bits(i).csr.ertn_en:=ex.in.bits(i).csr.op===SDEF(CSR_ERTN)&&ex_valid(i)
    ex_bits(i).csr.idle_en:=ex.in.bits(i).inst_op===SDEF(OP_IDLE)&&ex_valid(i)
  }
//--------------------------  CSR -------------------------
//--------------------------  TLB -------------------------
  if(GenCtrl.USE_TLB){
    ex.to_tlb.get.tlbsrch_en:=(ex.in.bits(0).csr.op===SDEF(TLB_SRCH)&&ex_valid(0)
                             ||ex.in.bits(1).csr.op===SDEF(TLB_SRCH)&&ex_valid(1))&& ~ex_excp_en(0)
    ex.to_tlb.get.is_tlbsrch:=(ex.in.bits(0).csr.op===SDEF(TLB_SRCH)||ex.in.bits(1).csr.op===SDEF(TLB_SRCH))
  }
  for(i<-0 until 2){
    ex_bits(i).invtlb.asid:=ex.in.bits(i).src1(9,0)
    ex_bits(i).invtlb.va  :=ex.in.bits(i).src2(31,13)
    ex_bits(i).tlb_refetch:=ex.in.bits(i).inst_op===SDEF(OP_TLB)
  }
//--------------------------  TLB -------------------------
//------------------------ 访存单元 ------------------------
  val ex_excp_type=Wire(Vec(2,new ex_excp_bundle()))
  for(i<-0 until 2){
    ex_excp_en(i):=ex_excp_type(i).asUInt.orR
    ex_excp_type(i).ale:=(((mem_size(i)(1)&&ex_data_addr(i)(0)) ||
                          (!mem_size(i)   &&ex_data_addr(i)(1,0).asUInt.orR))&&
                          (ex_bits(i).ld_en||ex_bits(i).st_en)&& ~flush_second_inst)
    ex_excp_type(i).num:=ex.in.bits(i).excp_type
  }

  ex_bits(0).delay_alu_op:=DontCare
  ex_bits(0).relate_src1:=DontCare
  ex_bits(0).relate_src2:=DontCare
  ex_bits(0).delay_src1:=DontCare
  ex_bits(0).delay_src2:=DontCare
  ex_bits(1).delay_alu_op:=ex.in.bits(1).alu_op
  ex_bits(1).relate_src1:=ex.in.bits(1).relate_src1
  ex_bits(1).relate_src2:=ex.in.bits(1).relate_src2
  ex_bits(1).delay_src1:=ex.in.bits(1).src1
  ex_bits(1).delay_src2:=ex.in.bits(1).src2

//NOTE:perf
  // dontTouch(ex_bits(0).perf_sys_quit)
  for(i<-0 until 2){
    ex_bits(i).perf_sys_quit:=ex.in.bits(i).perf_sys_quit
    ex_bits(i).perf_branch.taken:=ex.in.bits(i).perf_branch.taken||br_b_taken(i)
    ex_bits(i).perf_branch.br_type:=ex.in.bits(i).br_type 
  }

  for(i<-0 until 2){
    ex_bits(i).isBrJmp :=ex.in.bits(i).isBrJmp
    ex_bits(i).isBrCond:=ex.in.bits(i).isBrCond
    ex_bits(i).brjump_result:=ex.in.bits(i).brjump_result
    ex_bits(i).brcond_result:=predictorUpdate(i)
  }
//------------------------流水级总线------------------------
  val loadValid=Wire(Vec(2,UInt(8.W))) //NOTE:用来diff
  val storeValid=Wire(Vec(2,UInt(8.W)))
  val realLoadValid=Wire(Vec(2,UInt(8.W)))
  val realStoreValid=Wire(Vec(2,UInt(8.W)))
  for(i<-0 until 2){
    loadValid(i):= Cat(0.U(2.W),ex.in.bits(i).ld_type(OneHotDef(LD_LLW)),
                                ex.in.bits(i).ld_type(OneHotDef(LD_LW)),
                                ex.in.bits(i).ld_type(OneHotDef(LD_LHU)),
                                ex.in.bits(i).ld_type(OneHotDef(LD_LH)),
                                ex.in.bits(i).ld_type(OneHotDef(LD_LBU)),
                                ex.in.bits(i).ld_type(OneHotDef(LD_LB)))
    storeValid(i):=Cat(0.U(4.W),ex.in.bits(i).st_type(OneHotDef(ST_SCW))&&ex_bits(i).st_en,
                                ex.in.bits(i).st_type(OneHotDef(ST_SW)),
                                ex.in.bits(i).st_type(OneHotDef(ST_SH)),
                                ex.in.bits(i).st_type(OneHotDef(ST_SB))) 
  }
  realLoadValid(0):=loadValid(0)
  realLoadValid(1):=Mux(ex_bits(0).ld_en||ex_bits(0).st_en,0.U,loadValid(1))
  realStoreValid(0):=storeValid(0)
  realStoreValid(1):=Mux(ex_bits(0).ld_en||ex_bits(0).st_en,false.B,storeValid(1))
  for(i<-0 until 2){
    ex_bits(i).diffLoad.valid:=realLoadValid(i)
    ex_bits(i).diffLoad.paddr:=DontCare
    ex_bits(i).diffLoad.vaddr:=ex_data_addr(i)

    ex_bits(i).diffStore.valid:=realStoreValid(i)
    ex_bits(i).diffStore.paddr:=DontCare
    ex_bits(i).diffStore.vaddr:=ex_data_addr(i)
    ex_bits(i).diffStore.data :=ex_data_wdata
  }


  for(i<-0 until 2){
    ex_bits(i).diffInstr.is_CNTinst:=ex.in.bits(i).diffInstr.is_CNTinst
    ex_bits(i).diffInstr.csr_rstat :=ex.in.bits(i).diffInstr.csr_rstat
    ex_bits(i).diffInstr.csr_data  :=ex.in.bits(i).csr.rdata
    ex_bits(i).diffInstr.timer64value:=ex.in.bits(i).diffInstr.timer64value

    ex_bits(i).excp_en:=ex_excp_en(i)
    ex_bits(i).excp_type:=ex_excp_type(i)
    ex_bits(i).bad_addr:=ex_data_addr(i)
    ex_bits(i).ld_type:=ex.in.bits(i).ld_type
    ex_bits(i).wb_sel :=ex.in.bits(i).wb_sel
    ex_bits(i).rf_wen :=ex.in.bits(i).rf_wen
    ex_bits(i).rf_addr:=ex.in.bits(i).dest
    ex_bits(i).inst   :=ex.in.bits(i).inst
    ex_bits(i).pc     :=ex.in.bits(i).pc  
  }
  ex_bits(0).pred := pred(0)
  ex_bits(1).pred := pred(1)
  ex.to_ls.bits:=ex_bits

}

class ctrl_cache_bundle() extends Bundle{
  val dcacop_en=Output(Bool())
  val cacop_mode=Output(UInt(2.W))
}
