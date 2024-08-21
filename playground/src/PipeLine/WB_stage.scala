import chisel3._
import chisel3.util._
import config.Configs._
import Control._
import config.GenCtrl

//NOTE:CMU三相合一，承担接受访存结果，ex2执行级，commit提交
class WBU extends Module {
  val wb=IO(new Bundle {
    val in=Flipped(new ls_to_wb_bus_bundle())
  
    val fw_ih=Output(new ih_from_wb_bus_bundle())
    val sys_quit=Output(Bool())
    val diffInstrCommit=Output(Vec(2,new DiffInstrBundle()))
    val diffLoadCommit=Output(Vec(2,new DiffLoadBundle()))
    val diffStoreCommit=Output(Vec(2,new DiffStoreBundle()))
    val diffExcpCommit=Output(new DiffExcpBundle())
  })
  val wb_valid=dontTouch(RegInit(VecInit(Seq.fill(2)(false.B))))
  val wb_ready_go=true.B
  wb.in.allowin:= ~(wb_valid(0)||wb_valid(1))|| wb_ready_go
  when(wb.in.allowin){
    wb_valid(0):=wb.in.dualmask(0)
    wb_valid(1):=wb.in.dualmask(1)
  }
  val real_valid=Wire(Vec(2,Bool()))
  real_valid:=Cat(wb_valid(1) && ~wb.in.bits(1).excp_en,
                  wb_valid(0) && ~wb.in.bits(0).excp_en).asTypeOf(Vec(2,Bool()))

  val wb_final_result=Wire(Vec(2,UInt(DATA_WIDTH.W)))
  for(i<-0 until 2){
    // wb_final_result(i):=Mux1hMap(wb.in.bits(i).wb_sel,Map(
    //   SDEF(WB_ALU) ->  wb.in.bits(i).alu_result,
    //   SDEF(WB_MEM) ->  wb.in.bits(i).mem_result,
    //   SDEF(WB_DIV) ->  wb.in.bits(i).div_result,
    //   SDEF(WB_MOD) ->  wb.in.bits(i).mod_result,
    //   SDEF(WB_MULL)->  wb.in.bits(i).mul_result(31,0),
    //   SDEF(WB_MULH)->  wb.in.bits(i).mul_result(63,32),
    //   SDEF(WB_SCW) ->  (wb.in.bits(i).is_scw&real_valid(i))
    // ))
    wb_final_result(i):=wb.in.bits(i).result
  }
  for(i<-0 until 2){
    wb.fw_ih.bits(i).rf.wen:=wb.in.bits(i).rf_wen&&real_valid(i)
    wb.fw_ih.bits(i).rf.waddr:=wb.in.bits(i).rf_addr
    wb.fw_ih.bits(i).rf.wdata:=wb_final_result(i)
  }


  //------------------------------Commit------------------------------
  for(i<-0 until 2){
    wb.diffInstrCommit(i).valid:=real_valid(i)
    wb.diffInstrCommit(i).pc   :=wb.in.bits(i).pc
    wb.diffInstrCommit(i).instr:=wb.in.bits(i).inst
    wb.diffInstrCommit(i).skip :=false.B
    wb.diffInstrCommit(i).is_TLBFILL:=wb.in.bits(i).diffInstr.is_TLBFILL
    wb.diffInstrCommit(i).TLBFILL_index:=wb.in.bits(i).diffInstr.TLBFILL_index
    wb.diffInstrCommit(i).is_CNTinst:=wb.in.bits(i).diffInstr.is_CNTinst
    wb.diffInstrCommit(i).timer_64_value:=wb.in.bits(i).diffInstr.timer64value
    wb.diffInstrCommit(i).wen  :=wb.in.bits(i).rf_wen&&real_valid(i)
    wb.diffInstrCommit(i).wdest:=wb.in.bits(i).rf_addr
    wb.diffInstrCommit(i).wdata:=wb.fw_ih.bits(i).rf.wdata
    wb.diffInstrCommit(i).csr_rstat:=wb.in.bits(i).diffInstr.csr_rstat
    wb.diffInstrCommit(i).csr_data :=wb.in.bits(i).diffInstr.csr_data

    wb.diffLoadCommit(i):=wb.in.bits(i).diffLoad
    wb.diffStoreCommit(i):=wb.in.bits(i).diffStore
  }
  wb.diffExcpCommit.eret:=wb.in.bits(0).diffExcp.eret&&real_valid(0)||wb.in.bits(1).diffExcp.eret&&real_valid(1)
  wb.diffExcpCommit.cause:=wb.in.bits(0).diffExcp.cause
  wb.diffExcpCommit.intrNo:=wb.in.bits(0).diffExcp.intrNo
  wb.diffExcpCommit.pc:=wb.in.bits(0).diffExcp.pc
  wb.diffExcpCommit.inst:=wb.in.bits(0).diffExcp.inst
  wb.diffExcpCommit.valid:=wb_valid(0)&&wb.in.bits(0).excp_en||wb_valid(1)&&wb.in.bits(1).excp_en

//NOTE:PERF COUNTER
  wb.sys_quit:=RegNext(wb.in.bits(0).perf_sys_quit||wb.in.bits(1).perf_sys_quit)
  if(GenCtrl.PERF_CNT){
    val instr_cnt=RegInit(0.U(32.W))
    val dual_cnt =RegInit(0.U(32.W))
    val br_total_cnt=RegInit(0.U(32.W))
    val br_imm_cnt=RegInit(0.U(32.W))
    val br_cond_cnt=RegInit(0.U(32.W))
    val br_jirl_cnt=RegInit(0.U(32.W))
    val br_inst_cnt=RegInit(0.U(32.W))
    val excp_cnt   =RegInit(0.U(32.W))
    val load_cnt   =RegInit(0.U(32.W))
    val store_cnt  =RegInit(0.U(32.W))
    val dead_timer =RegInit(0.U(64.W))
    val timer_cnt  =RegInit(0.U(64.W))
    timer_cnt:=timer_cnt+1.U
    when(wb.in.dualmask.asUInt.orR){
//----------------------------------双发统计----------------------------------
      when(wb.in.dualmask(1)){
        instr_cnt:=instr_cnt+2.U
        dual_cnt:=dual_cnt+1.U //NOTE:双发指令的数量
        dead_timer:=dead_timer+2.U
      }.otherwise{
        dead_timer:=dead_timer+1.U
        instr_cnt:=instr_cnt+1.U
      }
//----------------------------------分支统计----------------------------------
      var br_cond=(wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_BNE))
                || wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_BEQ))
                || wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_BGE))
                || wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_BGEU))
                || wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_BLT))
                || wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_BLTU)))&&wb.in.bits(0).perf_branch.taken
      var br_imm =(wb.in.bits(0).perf_branch.br_type(OneHotDef(J_B))
                || wb.in.bits(0).perf_branch.br_type(OneHotDef(J_BL))) &&wb.in.bits(0).perf_branch.taken
      var br_jirl= wb.in.bits(0).perf_branch.br_type(OneHotDef(J_JIRL))&&wb.in.bits(0).perf_branch.taken
      var br_inst=(~wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_XXX)))&&wb.in.bits(0).perf_branch.taken
      when(br_inst){
        br_total_cnt:=br_total_cnt+1.U
      }
      when(br_imm){
        br_imm_cnt:=br_imm_cnt+1.U
      }
      when(br_cond){
        br_cond_cnt:=br_cond_cnt+1.U
      }
      when(br_jirl){
        br_jirl_cnt:=br_jirl_cnt+1.U
      }
      when(~wb.in.bits(0).perf_branch.br_type(OneHotDef(BR_XXX))){
        br_inst_cnt:=br_inst_cnt+1.U
      }

//----------------------------------例外统计----------------------------------
      when(wb.in.bits(0).excp_en||wb.in.bits(1).excp_en){
        excp_cnt:=excp_cnt+1.U
      }
    
//----------------------------------访存统计----------------------------------
      when(wb.in.bits(0).diffStore.valid.asUInt.orR||wb.in.bits(1).diffStore.valid.asUInt.orR){
        store_cnt:=store_cnt+1.U
      }
      when(wb.in.bits(0).diffLoad.valid.asUInt.orR||wb.in.bits(1).diffLoad.valid.asUInt.orR){
        load_cnt:=load_cnt+1.U
      }
    }

    when(wb.sys_quit){
      printf("---------------------------CORE_PERF---------------------------\n")
      printf("total_cnt=%d\n",instr_cnt)
      printf("dual_cnt=%d rate=%d%%\n",dual_cnt,((dual_cnt.asSInt *100.asSInt)/instr_cnt.asSInt))
      printf("br_inst=%d br_taken=%d\n br_imm=%d br_cond=%d br_jirl=%d\n",
              br_inst_cnt,br_total_cnt,br_imm_cnt,br_cond_cnt,br_jirl_cnt)
      printf("excp=%d load=%d store=%d\n",excp_cnt,load_cnt,store_cnt)
      printf("---------------------------CORE_PERF---------------------------\n")
    }
    when(dead_timer>380000000.U){
      when((timer_cnt%100000.U)===0.U){
        printf("\npc0=%x inst0=%x\npc1=%x inst1=%x\ninst_cnt=%d\n",wb.in.bits(0).pc,wb.in.bits(0).inst,wb.in.bits(1).pc,wb.in.bits(1).inst,dead_timer)
      }
    }
  }
}

