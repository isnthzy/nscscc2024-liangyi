import chisel3._
import chisel3.util._  
import config.Configs._
import config.GenCtrl._
import coursier.Fetch
import config.BtbParams.FetchWidth
import config.GenCtrl

class IDU extends Module{
  val id=IO(new Bundle {
    val in=Flipped(new if_to_id_bus_bundle())
    val to_ih=new id_to_ih_bus_bundle()
    val QinstAllowIn=Output(Bool()) //NOTE:前端握手与后端握手机制不一
    val fw_pf=Output(new pf_from_id_bus_bundle())
    val fw_if=Output(new if_from_id_bus_bundle())
    // val from_ih=Input(new id_from_ih_bus_bundle())
    val from_ex=Input(new id_from_ex_bus_bundle())
    val from_ls=Input(new id_from_ls_bus_bundle())
  })
  val id_flush=(id.from_ex.br_flush || id.from_ls.flush.asUInt.orR) //NOTE:用来flush流水线 
  val queue_data_ready=Wire(Vec(2,Bool()))
  val bpflush_fifo=Wire(Bool())
  val flush_second_inst=Wire(Bool())
  id.to_ih.dualmask(0):=queue_data_ready(0)
  id.to_ih.dualmask(1):=queue_data_ready(1)&& ~flush_second_inst
  val InstFIFO=Module(new DualBankFIFO(id.in.bits(0).getWidth,INST_QUEUE_SIZE))
  InstFIFO.io.input_size:=Cat(id.in.dualmask(1),id.in.dualmask.asUInt.xorR)
  InstFIFO.io.input_data0:=id.in.bits(0).asUInt
  InstFIFO.io.input_data1:=id.in.bits(1).asUInt
  id.QinstAllowIn:=InstFIFO.io.fifo_length<=(INST_QUEUE_SIZE-4).U //if流水槽里还有两条指令
  InstFIFO.io.flush_fifo:=id_flush||bpflush_fifo
  queue_data_ready(0):=InstFIFO.io.fifo_length>=1.U&&id.to_ih.allowin&& ~id_flush
  queue_data_ready(1):=InstFIFO.io.fifo_length>=2.U&&id.to_ih.allowin&& ~id_flush
  InstFIFO.io.output_size:=Cat(queue_data_ready(1),queue_data_ready.asUInt.xorR)

  val inst_queue_bits=dontTouch(Wire(Vec(2,new if_to_id_bus_data_bundle())))
  inst_queue_bits(0):=InstFIFO.io.output_data0.asTypeOf(new if_to_id_bus_data_bundle())
  inst_queue_bits(1):=InstFIFO.io.output_data1.asTypeOf(new if_to_id_bus_data_bundle())

  val decode_bits=Wire(Vec(2,new decode_data_bundle()))
  id.to_ih.bits:=decode_bits
  import Control._
  val Decode=Array.fill(2)(Module(new Decode()).io)
  val ImmGen=Array.fill(2)(Module(new ImmGen()).io)
  for(i<-0 until 2){
    Decode(i).inst:=inst_queue_bits(i).inst
    ImmGen(i).inst:=inst_queue_bits(i).inst
    ImmGen(i).itype:=Decode(i).imm_type
    decode_bits(i).imm:=ImmGen(i).out
    var rj=inst_queue_bits(i).inst( 9, 5)
    var rk=inst_queue_bits(i).inst(14,10)
    var rd=inst_queue_bits(i).inst( 4, 0)
    decode_bits(i).cnt_op  :=((Fill(CNT_XX.length,Decode(i).cnt_op=/=SDEF(CNT_VLID))&Decode(i).cnt_op)|
                              (Fill(CNT_XX.length,Decode(i).cnt_op===SDEF(CNT_VLID)&&rd===0.U)&SDEF(CNT_ID))|
                              (Fill(CNT_XX.length,Decode(i).cnt_op===SDEF(CNT_VLID)&&rd=/=0.U)&SDEF(CNT_VL)))
    decode_bits(i).csr_addr:=Mux(Decode(i).cnt_op===SDEF(CNT_VLID)&&inst_queue_bits(i).inst( 4, 0)===0.U,CsrName.TID,inst_queue_bits(i).inst(23,10))
    decode_bits(i).csr_wen :=(Decode(i).csr_op===SDEF(CSR_WR)||Decode(i).csr_op===SDEF(CSR_XCHG))
    decode_bits(i).r1:=rj
    decode_bits(i).r2:=Mux(Decode(i).B_sel===SDEF(B_RD)||decode_bits(i).csr_wen
                          ,rd,rk)
    decode_bits(i).rd:=((Fill(5,Decode(i).br_type(OneHotDef(J_BL))&1.U(5.W))|
                        (Fill(5,Decode(i).cnt_op===SDEF(CNT_VLID)&&rd===0.U)&rj)|
                        (Fill(5,Decode(i).cnt_op===SDEF(CNT_VLID)&&rd=/=0.U)&rd)|
                        (Fill(5,Decode(i).cnt_op=/=SDEF(CNT_VLID)&&(~Decode(i).br_type(OneHotDef(J_BL))))&rd)))
    decode_bits(i).csr_op :=Decode(i).csr_op
    decode_bits(i).st_type:=Decode(i).st_type
    decode_bits(i).ld_type:=Decode(i).ld_type
    decode_bits(i).wb_sel :=Decode(i).wb_sel
    decode_bits(i).rf_wen :=Decode(i).rf_wen
    decode_bits(i).br_type:=Decode(i).br_type
    decode_bits(i).alu_op :=Decode(i).alu_op
    decode_bits(i).inst_op:=Decode(i).inst_op
    decode_bits(i).r1_sel :=Decode(i).A_sel
    decode_bits(i).r2_sel :=Decode(i).B_sel
    decode_bits(i).pc  :=inst_queue_bits(i).pc
    decode_bits(i).inst:=inst_queue_bits(i).inst

    decode_bits(i).excp_en:=decode_bits(i).excp_type.asUInt.orR
    decode_bits(i).excp_type.ipe:=false.B
    decode_bits(i).excp_type.ine:=Decode(i).csr_op===SDEF(CSR_INE)||(Decode(i).csr_op===SDEF(TLB_INV)&&(rd>6.U).asBool)
    decode_bits(i).excp_type.break:=Decode(i).csr_op===SDEF(CSR_BREAK)
    decode_bits(i).excp_type.syscall:=Decode(i).csr_op===SDEF(CSR_SYS)
    decode_bits(i).excp_type.num    :=inst_queue_bits(i).excp_type

    decode_bits(i).perf_sys_quit:=decode_bits(i).csr_op===SDEF(CSR_SYS)&&decode_bits(i).rd===17.U
  }
  decode_bits(0).excp_type.has_int:=DontCare
  decode_bits(1).excp_type.has_int:=DontCare

//-----------------------------Jump跳转-----------------------------
  val allow_taken=Wire(Vec(2,Bool()))
  val isBrJmp=Wire(Vec(2,Bool()))
  val isBrCond=Wire(Vec(2,Bool()))
  val j_taken=Wire(Vec(2,Bool()))
  val j_target=Wire(Vec(2,UInt(ADDR_WIDTH.W)))
  allow_taken(0):= InstFIFO.io.fifo_length>=1.U&&id.to_ih.allowin
  allow_taken(1):= InstFIFO.io.fifo_length>=2.U&&id.to_ih.allowin
//在issue可能会发生跳转s
  for(i<-0 until 2){
    isBrJmp(i):=inst_queue_bits(i).inst(31,27)==="b01010".U(5.W)
    isBrCond(i):=Decode(i).inst_op===SDEF(OP_BRU)
    j_taken(i):=isBrJmp(i)&&allow_taken(i)
    j_target(i):=decode_bits(i).pc+Sext(Cat(inst_queue_bits(i).inst(9,0),inst_queue_bits(i).inst(25,10),0.U(2.W)), 32)
  }
  val taken=j_taken.asUInt.orR
  val target=Mux(j_taken(0),j_target(0),j_target(1))

  /** brType解析
  * 001 条件分支beq、bne ...
  * 010 无条件分支b bl jirl
  * 100 ras ? 待实现
  */
  /**预测逻辑
    * 当前包含jmp指令时，更新
    */

  val pred = Wire(Vec(FetchWidth, new PredictorOutput()))
  for( i <- 0 until FetchWidth ){
    pred(i) := inst_queue_bits(i).pred
  }

  val predictorUpdate = Wire(Vec(FetchWidth, new PredictorUpdate()))
  for( i <- 0 until FetchWidth ){
    predictorUpdate(i).valid := isBrJmp(i)  //if inst is valid, need update
    predictorUpdate(i).pc := decode_bits(i).pc
    predictorUpdate(i).brTaken := j_taken(i)
    predictorUpdate(i).entry.brTarget := j_target(i)
    predictorUpdate(i).entry.brType   := Mux(isBrJmp(i), 2.U, 0.U)
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
  val bpWRedirectTaken = Wire(Bool())
  val bpWRedirect = WireInit(0.U(ADDR_WIDTH.W))
  for( i <- 0 until FetchWidth ){
    bp_direction_right(i) := pred(i).brTaken === j_taken(i) 
    bp_target_right(i) := j_taken(i) && bp_direction_right(i) && (pred(i).entry.brTarget === j_target(i))

    bp_right(i) := bp_direction_right(i) && (!j_taken(i) || bp_target_right(i))
    bp_wrong(i) :=(!bp_direction_right(i) || (j_taken(i) && !bp_target_right(i)))&& isBrJmp(i)
    bp_wrong_redirect(i) := Mux(bp_wrong(i) && j_taken(i), j_target(i),
                              Mux(bp_wrong(i) && !j_taken(0), inst_queue_bits(i).pc + 4.U, 0.U))
  }
  bpWRedirectTaken := (0 until FetchWidth).map{i => bp_wrong(i) && j_taken(i)}.reduce(_ || _) //每个重定向的指令必须是jmp指令
  bpWRedirect := Mux(bp_wrong(0) && allow_taken(0), bp_wrong_redirect(0), bp_wrong_redirect(1))


  //优先级很重要!!!
  //TODO: as b bl inst miss predicton redirect
  //fallThroughAddr is miss prediction addr default is pc(0) + 8.U, it can be change in IFU.
  id.fw_pf.br_j.taken := bpWRedirectTaken
  id.fw_pf.br_j.target := bpWRedirect
  id.fw_if.br_flush := bpWRedirectTaken
  bpflush_fifo:=bpWRedirectTaken //用于flush fifo
  flush_second_inst := bp_wrong(0) && isBrJmp(0)

  for(i<-0 until 2){
    decode_bits(i).isBrJmp :=isBrJmp(i)
    decode_bits(i).isBrCond:=isBrCond(i)
    decode_bits(i).pred:=pred(i)
    decode_bits(i).brjump_result:=predictorUpdate(i)
  }


  val bp_right_counter = RegInit(0.U(ADDR_WIDTH.W))
  val bp_wrong_counter = RegInit(0.U(ADDR_WIDTH.W))
  val bp_isJmp_counter = RegInit(0.U(ADDR_WIDTH.W))
  dontTouch(bp_isJmp_counter)
  dontTouch(bp_wrong_counter)
  if(GenCtrl.PERF_CNT){
    when(isBrJmp(0) && id.to_ih.dualmask(0)){
      bp_isJmp_counter := bp_isJmp_counter + 1.U
      bp_wrong_counter := Mux(bp_wrong(0) && pred(0).entry.brType === 1.U, bp_wrong_counter + 1.U, bp_wrong_counter)
    }.elsewhen(isBrJmp(1) && id.to_ih.dualmask(1)){
      bp_isJmp_counter := bp_isJmp_counter + 2.U
      bp_wrong_counter := Mux(bp_wrong(0) && pred(0).entry.brType === 1.U, bp_wrong_counter + 2.U, bp_wrong_counter)
    }
    for( i <- 0 until FetchWidth ){
      when(decode_bits(i).perf_sys_quit){
        printf("---------------------------BTB_PERF---------------------------\n")
        printf("jump_cnt=%d jump_wrong_cnt=%d\n",bp_isJmp_counter,bp_wrong_counter)
        printf("miss_rate=%d%%\n",((bp_wrong_counter.asSInt *100.asSInt)/bp_isJmp_counter.asSInt))
      }
    }
  }
}

class decode_data_bundle extends Bundle{
  val perf_sys_quit=Bool()
  val pred=new PredictorOutput()
  val brjump_result=new PredictorUpdate()
  val isBrJmp =Bool()
  val isBrCond=Bool()
  val csr_op  =UInt(Control.CSR_XXXX.length.W)
  val csr_wen =Bool()
  val csr_addr=UInt(14.W)

  val excp_en=Bool()
  val excp_type=new id_excp_bundle()
  val cnt_op =UInt(Control.CNT_XX.length.W)
  val st_type=UInt(Control.ST_XX.length.W)
  val ld_type=UInt(Control.LD_XXX.length.W)
  val wb_sel =UInt(Control.WB_ALU.length.W)
  val rf_wen =Bool()
  val br_type=UInt(Control.BR_XXX.length.W)
  val alu_op =UInt(Control.ALU_XXX.length.W)
  val inst_op=UInt(Control.OP_XXXX.length.W)
  val r1_sel =UInt(Control.A_XXX.length.W)
  val r2_sel =UInt(Control.B_XXX.length.W)
  val imm=UInt(32.W)
  val r1=UInt(5.W)
  val r2=UInt(5.W)
  val rd=UInt(5.W)
  val pc=UInt(ADDR_WIDTH.W)
  val inst=UInt(32.W)
}