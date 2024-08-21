import chisel3._
import chisel3.util._
import config.Configs._
import config.GenCtrl._
import coursier.Fetch
import config.BtbParams.FetchWidth


class IHU extends Module{
  val ih=IO(new Bundle {
    val in=Flipped(new id_to_ih_bus_bundle())
    val to_ex=new ih_to_ex_bus_bundle()
  
    val fw_pf=Output(new pf_from_ih_bus_bundle())
    val from_ex=Input(new ih_from_ex_bus_bundle())
    val from_ls=Input(new ih_from_ls_bus_bundle())
    val from_wb=Input(new ih_from_wb_bus_bundle()) //NOTE:commit
    val from_csr=Flipped(new pipeline_from_csr_bundle())

    val diff_reg=Output(Vec(32, UInt(DATA_WIDTH.W)))
  })
  val ih_flush=(ih.from_ls.flush.asUInt.orR || ih.from_ex.br_flush  )
  val a_allow_issue=dontTouch(Wire(Bool()))
  val b_allow_issue=dontTouch(Wire(Bool()))
  ih.to_ex.dualmask(0):=a_allow_issue
  ih.to_ex.dualmask(1):=b_allow_issue
  //NOTE:发射机制，如果后端发生阻塞，前端只需保持fifo队列的tail指针不变
  //即可buff住要issue的数据



  val IssueFIFO=Module(new DualBankFIFO(ih.in.bits(0).getWidth,ISSUE_QUEUE_SIZE))
  IssueFIFO.io.input_size:=Cat(ih.in.dualmask(1),ih.in.dualmask.asUInt.xorR)
  IssueFIFO.io.input_data0:=ih.in.bits(0).asUInt
  IssueFIFO.io.input_data1:=ih.in.bits(1).asUInt
  ih.in.allowin:=IssueFIFO.io.fifo_length<=(ISSUE_QUEUE_SIZE-2).U
  IssueFIFO.io.flush_fifo:=ih_flush
  IssueFIFO.io.output_size:=Cat(b_allow_issue,a_allow_issue^b_allow_issue)
  val queue_data_ready=IssueFIFO.io.fifo_length>=2.U
  val issue_bits=dontTouch(Wire(Vec(2,new ih_to_ex_bus_data_bundle())))

  val decode_bits=Wire(Vec(2,new decode_data_bundle()))
  decode_bits(0):=IssueFIFO.io.output_data0.asTypeOf(new decode_data_bundle())
  decode_bits(1):=IssueFIFO.io.output_data1.asTypeOf(new decode_data_bundle())

  decode_bits(0).excp_type.has_int:=ih.from_csr.has_int
  decode_bits(1).excp_type.has_int:=false.B

  ih.fw_pf.has_int:=ih.from_csr.has_int


  val RegFile=Module(new RegFile())
  RegFile.io.wen1  :=ih.from_wb.bits(0).rf.wen
  RegFile.io.waddr1:=ih.from_wb.bits(0).rf.waddr
  RegFile.io.wdata1:=ih.from_wb.bits(0).rf.wdata
  RegFile.io.wen2  :=ih.from_wb.bits(1).rf.wen
  RegFile.io.waddr2:=ih.from_wb.bits(1).rf.waddr
  RegFile.io.wdata2:=ih.from_wb.bits(1).rf.wdata
  RegFile.io.raddr1:=decode_bits(0).r1
  RegFile.io.raddr2:=decode_bits(0).r2
  RegFile.io.raddr3:=decode_bits(1).r1
  RegFile.io.raddr4:=decode_bits(1).r2

  ih.diff_reg:=RegFile.io.RegToDiff
  val regdata=dontTouch(Wire(Vec(2,new RegFileDataBundle())))
  regdata(0).rdata1:=RegFile.io.rdata1
  regdata(0).rdata2:=RegFile.io.rdata2
  regdata(1).rdata1:=RegFile.io.rdata3
  regdata(1).rdata2:=RegFile.io.rdata4


//NOTE:Bypass Network
  import Control._
  val inst_r1_ready=Wire(Vec(2,Bool()))
  val inst_r2_ready=Wire(Vec(2,Bool()))
  for(i<-0 until 2){
    when(decode_bits(i).r1_sel===SDEF(A_PC)){
      issue_bits(i).src1:=decode_bits(i).pc
      inst_r1_ready(i):=true.B
    }.otherwise{
      when(decode_bits(i).r1===0.U){
        issue_bits(i).src1:=0.U
        inst_r1_ready(i):=true.B
      }.elsewhen(ih.from_ex.bits(1).fw.wen&&ih.from_ex.bits(1).fw.waddr===decode_bits(i).r1){
        issue_bits(i).src1:=ih.from_ex.bits(1).fw.wdata
        inst_r1_ready(i):= ~ih.from_ex.bits(1).bypass_unready 
      }.elsewhen(ih.from_ex.bits(0).fw.wen&&ih.from_ex.bits(0).fw.waddr===decode_bits(i).r1){
        issue_bits(i).src1:=ih.from_ex.bits(0).fw.wdata
        inst_r1_ready(i):= ~ih.from_ex.bits(0).bypass_unready 
      }.elsewhen(ih.from_ls.bits(1).rf.wen&&ih.from_ls.bits(1).rf.waddr===decode_bits(i).r1){
        issue_bits(i).src1:=ih.from_ls.bits(1).rf.wdata
        inst_r1_ready(i):= ~ih.from_ls.bits(1).bypass_unready 
      }.elsewhen(ih.from_ls.bits(0).rf.wen&&ih.from_ls.bits(0).rf.waddr===decode_bits(i).r1){
        issue_bits(i).src1:=ih.from_ls.bits(0).rf.wdata
        inst_r1_ready(i):= ~ih.from_ls.bits(0).bypass_unready 
      }.elsewhen(ih.from_wb.bits(1).rf.wen&&ih.from_wb.bits(1).rf.waddr===decode_bits(i).r1){
        issue_bits(i).src1:=ih.from_wb.bits(1).rf.wdata
        inst_r1_ready(i):= true.B 
      }.elsewhen(ih.from_wb.bits(0).rf.wen&&ih.from_wb.bits(0).rf.waddr===decode_bits(i).r1){
        issue_bits(i).src1:=ih.from_wb.bits(0).rf.wdata
        inst_r1_ready(i):= true.B 
      }.otherwise{
        issue_bits(i).src1:=regdata(i).rdata1
        inst_r1_ready(i):= true.B 
      }
    }

    when(decode_bits(i).r2_sel===SDEF(B_IMM)){
      issue_bits(i).src2:=decode_bits(i).imm
      inst_r2_ready(i):=true.B
      //TODO:CSR
    }.otherwise{
      when(decode_bits(i).r2===0.U){
        issue_bits(i).src2:=0.U
        inst_r2_ready(i):=true.B
      }.elsewhen(ih.from_ex.bits(1).fw.wen&&ih.from_ex.bits(1).fw.waddr===decode_bits(i).r2){
        issue_bits(i).src2:=ih.from_ex.bits(1).fw.wdata
        inst_r2_ready(i):= ~ih.from_ex.bits(1).bypass_unready 
      }.elsewhen(ih.from_ex.bits(0).fw.wen&&ih.from_ex.bits(0).fw.waddr===decode_bits(i).r2){
        issue_bits(i).src2:=ih.from_ex.bits(0).fw.wdata
        inst_r2_ready(i):= ~ih.from_ex.bits(0).bypass_unready 
      }.elsewhen(ih.from_ls.bits(1).rf.wen&&ih.from_ls.bits(1).rf.waddr===decode_bits(i).r2){
        issue_bits(i).src2:=ih.from_ls.bits(1).rf.wdata
        inst_r2_ready(i):= ~ih.from_ls.bits(1).bypass_unready 
      }.elsewhen(ih.from_ls.bits(0).rf.wen&&ih.from_ls.bits(0).rf.waddr===decode_bits(i).r2){
        issue_bits(i).src2:=ih.from_ls.bits(0).rf.wdata
        inst_r2_ready(i):= ~ih.from_ls.bits(0).bypass_unready 
      }.elsewhen(ih.from_wb.bits(1).rf.wen&&ih.from_wb.bits(1).rf.waddr===decode_bits(i).r2){
        //TODO:考虑mem指令？
        issue_bits(i).src2:=ih.from_wb.bits(1).rf.wdata
        inst_r2_ready(i):= true.B
      }.elsewhen(ih.from_wb.bits(0).rf.wen&&ih.from_wb.bits(0).rf.waddr===decode_bits(i).r2){
        //TODO:考虑mem指令？
        issue_bits(i).src2:=ih.from_wb.bits(0).rf.wdata
        inst_r2_ready(i):= true.B
      }.otherwise{
        issue_bits(i).src2:=regdata(i).rdata2
        inst_r2_ready(i):= true.B
      }
    }
  }
//CsrUnit
  ih.from_csr.rd_addr:=MuxPriorA(decode_bits(0).inst_op===SDEF(OP_CSR)||decode_bits(0).inst_op===SDEF(OP_CNT),decode_bits).csr_addr
  //TODO:cnt类型指令该怎么双发呢
  for(i<-0 until 2){
    var csr_result=Mux1hMap(decode_bits(i).cnt_op,Map(
      SDEF(CNT_VL)   -> ih.from_csr.timer_out(31,0),
      SDEF(CNT_VH)   -> ih.from_csr.timer_out(63,32),
      SDEF(CNT_ID)   -> ih.from_csr.rd_data,
      SDEF(CNT_XX)   -> ih.from_csr.rd_data,
    ))
    issue_bits(i).csr.op:=decode_bits(i).csr_op
    issue_bits(i).csr.wen:=decode_bits(i).csr_wen
    issue_bits(i).csr.addr:=decode_bits(i).csr_addr
    issue_bits(i).csr.rdata:=csr_result
  }


  for(i<-0 until 2){
    issue_bits(i).st_en:=(decode_bits(i).st_type(OneHotDef(ST_SB))
                       || decode_bits(i).st_type(OneHotDef(ST_SH))
                       || decode_bits(i).st_type(OneHotDef(ST_SW))
                       ||(decode_bits(i).st_type(OneHotDef(ST_SCW))&&ih.from_csr.llbit_out))
    issue_bits(i).ld_en:= ~decode_bits(i).ld_type(OneHotDef(LD_XXX))
  }


  val b_conflict_a=(((decode_bits(0).rd===decode_bits(1).r1&&decode_bits(1).r1_sel=/=SDEF(A_PC))
                  ||(decode_bits(0).rd===decode_bits(1).r2&&decode_bits(1).r2_sel=/=SDEF(B_IMM)))
                  &&decode_bits(0).rd=/=0.U&&decode_bits(0).rf_wen)
  val b_can_delay_issue=decode_bits(0).wb_sel(OneHotDef(WB_ALU))&&decode_bits(1).inst_op===SDEF(OP_ALU)
  issue_bits(0).relate_src1:=DontCare
  issue_bits(0).relate_src2:=DontCare
  issue_bits(1).relate_src1:=(b_can_delay_issue&&(decode_bits(0).rd===decode_bits(1).r1&&decode_bits(1).r1_sel=/=SDEF(A_PC))
                              &&decode_bits(0).rd=/=0.U&&decode_bits(0).rf_wen)
  issue_bits(1).relate_src2:=(b_can_delay_issue&&(decode_bits(0).rd===decode_bits(1).r2&&decode_bits(1).r2_sel=/=SDEF(B_IMM))
                              &&decode_bits(0).rd=/=0.U&&decode_bits(0).rf_wen)
  dontTouch(b_conflict_a)

  val only_a_issue=(decode_bits(0).excp_en||(b_conflict_a&& ~b_can_delay_issue)||
                    // decode_bits(0).inst_op===SDEF(OP_JUMP)||decode_bits(0).br_type(OneHotDef(J_JIRL))||
                    decode_bits(0).inst_op===SDEF(OP_TLB)||decode_bits(0).inst_op===SDEF(OP_CSR)||
                    decode_bits(0).inst_op===SDEF(OP_CACP)||decode_bits(0).inst_op===SDEF(OP_CNT)||
                    decode_bits(0).inst_op===SDEF(OP_IDLE)||decode_bits(0).inst_op===SDEF(OP_ATOM)||
                    ((decode_bits(0).inst_op===SDEF(OP_MUL)||decode_bits(0).inst_op===SDEF(OP_DIV))&&
                     (decode_bits(1).inst_op===SDEF(OP_LSU)||decode_bits(1).inst_op===SDEF(OP_ATOM)||
                      decode_bits(1).inst_op===SDEF(OP_MUL)||decode_bits(1).inst_op===SDEF(OP_DIV)))||
                    (decode_bits(0).wb_sel(OneHotDef(WB_MEM))&&(decode_bits(1).inst_op===SDEF(OP_MUL)||
                     decode_bits(1).inst_op===SDEF(OP_DIV)||decode_bits(1).excp_en))||
                    (decode_bits(0).inst_op===SDEF(OP_LSU)&&{(decode_bits(1).inst_op===SDEF(OP_LSU)||
                    decode_bits(1).inst_op===SDEF(OP_CACP)||decode_bits(1).inst_op===SDEF(OP_DIV)||
                    decode_bits(1).inst_op===SDEF(OP_TLB) ||decode_bits(1).inst_op===SDEF(OP_ATOM))}))
  /* 不允许第一条指令为load指令时（wb_sel(OneHotDef(WB_MEM))）双发，
    原因是第二条指令可能是mul,div,以及例外等，load会带来wb级的阻塞，
    此时第二条指令的结果可能会丢失或者是一直保持异常干扰流水线
   */
  //NOTE:IBAR、DBAR、ATOM不对性能测试产生影响但为了启动系统一律单发

  /*NOTE:只有以下条件成立时才可发射b指令：
    b指令不依赖a指令的结果
    a指令没有产生异常
    a指令不是跳转
    a指令不是访存指令
    a指令不是tlb和csr指令
    待更新，这是最初设计的想法，随着更新这个注释不再有效力性
    TODO:b指令依赖a指令结果时可以延迟发射？
  */
  val a_src_ready=inst_r1_ready(0)&&inst_r2_ready(0)
  val b_src_ready=inst_r1_ready(1)&&inst_r2_ready(1)
  a_allow_issue:=  ~ih_flush&& a_src_ready&&queue_data_ready&&ih.to_ex.allowin
  b_allow_issue:=( ~ih_flush&& a_src_ready && b_src_ready&&queue_data_ready&&ih.to_ex.allowin&& ~only_a_issue )

  for(i<-0 until 2){
    issue_bits(i).diffInstr.csr_rstat:=((decode_bits(i).csr_op===SDEF(CSR_WR)||decode_bits(i).csr_op===SDEF(CSR_XCHG)||decode_bits(i).csr_op===SDEF(CSR_RD))
                                      &&decode_bits(i).csr_addr===5.U(14.W))
    issue_bits(i).diffInstr.is_CNTinst:=decode_bits(i).cnt_op=/=0.U
    issue_bits(i).diffInstr.timer64value:=ih.from_csr.timer_out
  }

//NOTE:perf
  for(i<-0 until 2){
    issue_bits(i).perf_sys_quit:=decode_bits(i).perf_sys_quit
    issue_bits(i).perf_branch.taken:=decode_bits(i).brjump_result.brTaken
    issue_bits(i).perf_branch.br_type:=issue_bits(i).br_type
  }
  if(PERF_CNT){
    when(decode_bits(0).inst_op===SDEF(OP_TLB)||decode_bits(1).inst_op===SDEF(OP_TLB)){
      printf("have tlb insts")
    }
  }


//------------------------流水级总线------------------------
  issue_bits(0).brjump_result:=decode_bits(0).brjump_result
  issue_bits(1).brjump_result:=decode_bits(1).brjump_result
  for(i<-0 until 2){
    issue_bits(i).isBrJmp :=decode_bits(i).isBrJmp
    issue_bits(i).isBrCond:=decode_bits(i).isBrCond
    issue_bits(i).excp_en:=decode_bits(i).excp_en
    issue_bits(i).excp_type:=decode_bits(i).excp_type
    issue_bits(i).cnt_op :=decode_bits(i).cnt_op
    issue_bits(i).st_type:=decode_bits(i).st_type
    issue_bits(i).ld_type:=decode_bits(i).ld_type
    issue_bits(i).wb_sel :=decode_bits(i).wb_sel
    issue_bits(i).rf_wen :=decode_bits(i).rf_wen
    issue_bits(i).br_type:=decode_bits(i).br_type
    issue_bits(i).alu_op :=decode_bits(i).alu_op
    issue_bits(i).inst_op:=decode_bits(i).inst_op
    issue_bits(i).imm    :=decode_bits(i).imm
    issue_bits(i).dest   :=decode_bits(i).rd
    issue_bits(i).pc     :=decode_bits(i).pc
    issue_bits(i).inst   :=decode_bits(i).inst
  }
  issue_bits(0).pred := decode_bits(0).pred
  issue_bits(1).pred := decode_bits(1).pred
  ih.to_ex.bits:=issue_bits

}

class RegFileDataBundle extends Bundle{
  val rdata1=UInt(DATA_WIDTH.W)
  val rdata2=UInt(DATA_WIDTH.W)
}


