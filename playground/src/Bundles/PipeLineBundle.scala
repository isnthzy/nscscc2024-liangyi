import chisel3._
import chisel3.util._  
import config.Configs._
import Control.CNT_XX
//TODO:重构流水握手，更优雅的方式支持stall，flush等


class branch_taken_bundle extends Bundle{
  val taken=Bool()
  val target=UInt(ADDR_WIDTH.W)
}

class PredictorUpdate extends Bundle {
    val valid   = Bool()
    val pc    = UInt(ADDR_WIDTH.W)
    val brTaken   = Bool()
    // val brType    = UInt(BrTypeWidth.W)
    val entry = new BTBEntry
}

class regfile_wdata_bundle extends Bundle{
  val wen  =Bool()
  val waddr=UInt(5.W)
  val wdata=UInt(DATA_WIDTH.W) 
}

class pf_to_if_bus_bundle extends Bundle{
  val pc=UInt(ADDR_WIDTH.W)
  val nextpc=UInt(ADDR_WIDTH.W)
  val excp_en=Bool()
  val excp_type=new pf_excp_bundle()
  val direct_uncache=Bool()
  val pred0 = new PredictorOutput()
  val pred1 = new PredictorOutput()
}

class pf_from_if_bus_bundle extends Bundle{
  val pre_uncache_miss=Bool()
  val miss_pc=UInt(ADDR_WIDTH.W)
}

class pf_from_id_bus_bundle extends Bundle{
  val br_j=new branch_taken_bundle()
}

class pf_from_ih_bus_bundle extends Bundle{
  val has_int=Bool()
}

class pf_from_ex_bus_bundle extends Bundle{
  val br_b=new branch_taken_bundle()
  val icacop_en  =Bool()
  val icacop_addr=UInt(ADDR_WIDTH.W)
  val icacop_mode=UInt(2.W)
}

class pf_from_ls_bus_bundle extends Bundle{
  val flush=new pipeline_flushs_bundle()
  val refetch_pc=UInt(ADDR_WIDTH.W)
}

class pf_invalid_if_inst_bundle extends Bundle{
  val invalid = Bool()
}

class if_to_id_bus_data_bundle extends Bundle{
  val excp_en=Bool()
  val excp_type=new if_excp_bundle()
  val pc=UInt(ADDR_WIDTH.W)
  val inst=UInt(INST_WIDTH.W)
  val pred = new PredictorOutput()
}

class if_to_id_bus_bundle extends Bundle{
  val bits=Output(Vec(2,new if_to_id_bus_data_bundle()))
  val dualmask=Output(Vec(2,Bool()))
}

class if_from_id_bus_bundle extends Bundle{
  val br_flush=Bool()
}


class if_from_ex_bus_bundle extends Bundle{
  val br_flush=Bool()
}

class if_from_ls_bus_bundle extends Bundle{
  val flush=new pipeline_flushs_bundle()
}

class id_to_ih_bus_bundle extends Bundle{
  val bits=Output(Vec(2,new decode_data_bundle()))
  val dualmask=Output(Vec(2,Bool()))
  val allowin=Input(Bool())  //IssueQueueAllowIn 允许入队
}

// class id_from_ih_bus_bundle extends Bundle{
//   val br_flush=Bool()
// }

class id_from_ex_bus_bundle extends Bundle{
  val br_flush=Bool()
}

class id_from_ls_bus_bundle extends Bundle{
  val flush=new pipeline_flushs_bundle()
}

class ih_to_ex_bus_data_bundle extends Bundle{
  val diffInstr=new Bundle{
    val is_CNTinst=Bool()
    val csr_rstat=Bool()
    val timer64value=UInt(64.W)
  }
  val perf_sys_quit=Bool()
  val perf_branch=new perf_branch_bundle()
  val isBrJmp =Bool()
  val isBrCond=Bool()
  val brjump_result = new PredictorUpdate()
  val relate_src1=Bool()
  val relate_src2=Bool()

  val csr=new Bundle{
    val op  =UInt(Control.CSR_XXXX.length.W)
    val wen =Bool()
    val addr=UInt(14.W)
    val rdata=UInt(DATA_WIDTH.W)
  }
  val excp_en=Bool()
  val excp_type=new id_excp_bundle()
  val cnt_op=UInt(Control.CNT_XX.length.W)
  val ld_en  =Bool()
  val st_en  =Bool()
  val st_type=UInt(Control.ST_XX.length.W)
  val ld_type=UInt(Control.LD_XXX.length.W)
  val wb_sel =UInt(Control.WB_ALU.length.W)
  val rf_wen =Bool()
  val br_type=UInt(Control.BR_XXX.length.W)
  val alu_op =UInt(Control.ALU_XXX.length.W)
  val inst_op=UInt(Control.OP_XXXX.length.W)
  val imm =UInt(32.W)
  val src1=UInt(32.W)
  val src2=UInt(32.W)
  val dest=UInt(5.W)
  val pc  =UInt(ADDR_WIDTH.W)
  val inst=UInt(32.W)
  val pred = new PredictorOutput()
}

class ih_to_ex_bus_bundle extends Bundle{
  val bits=Output(Vec(2,new ih_to_ex_bus_data_bundle()))
  val dualmask=Output(Vec(2,Bool()))
  val allowin=Input(Bool())
}

class ih_from_ex_bus_data_bundle extends Bundle{
  val fw=new regfile_wdata_bundle() //NOTE:forword的缩写
  val bypass_unready=Bool()
}

class ih_from_ex_bus_bundle extends Bundle{
  val bits=Vec(2,new ih_from_ex_bus_data_bundle())
  val br_flush=Bool()
}

class ih_from_ls_bus_data_bundle extends Bundle{
  val rf=new regfile_wdata_bundle()
  val bypass_unready=Bool()
}

class ih_from_ls_bus_bundle extends Bundle{
  val bits=Vec(2,new ih_from_ls_bus_data_bundle())
  val flush=new pipeline_flushs_bundle()
}

class ih_from_wb_bus_data_bundle extends Bundle{
  val rf=new regfile_wdata_bundle()
}

class ih_from_wb_bus_bundle extends Bundle{
  val bits=Vec(2,new ih_from_wb_bus_data_bundle())
}

class ex_to_ls_bus_data_bundle extends Bundle{
  val diffLoad=new DiffLoadBundle()
  val diffStore=new DiffStoreBundle()
  val diffInstr=new Bundle{
    val timer64value=UInt(64.W)
    val is_CNTinst=Bool()
    val csr_rstat=Bool()
    val csr_data=UInt(DATA_WIDTH.W)
  }
  val perf_sys_quit=Bool()
  val perf_branch=new perf_branch_bundle()
  val isBrJmp =Bool()
  val isBrCond=Bool()
  val brjump_result = new PredictorUpdate()
  val brcond_result = new PredictorUpdate()
  val relate_src1=Bool()
  val relate_src2=Bool()
  val tlb_refetch=Bool()
  val invtlb=new Bundle {
    val asid=UInt(10.W)
    val va  =UInt(19.W)
  }
  val csr=new Bundle{
    val op  =UInt(Control.CSR_XXXX.length.W)
    val wen =Bool()
    val addr=UInt(14.W)
    val wdata=UInt(DATA_WIDTH.W)
    val ertn_en=Bool()
    val idle_en=Bool()
  }
  val excp_en=Bool()
  val excp_type=new ex_excp_bundle()
  val delay_alu_op=UInt(Control.ALU_XXX.length.W)
  val delay_src1=UInt(DATA_WIDTH.W)
  val delay_src2=UInt(DATA_WIDTH.W)

  val addr_low2bit=UInt(2.W)
  val bad_addr=UInt(32.W)
  val ld_en=Bool() //不需要st_type原因是st_type在ex级被处理
  val st_en=Bool()
  val is_sc_w=Bool()
  val is_ll_w=Bool()
  val cacop_en=Bool()
  val ld_type=UInt(Control.LD_XXX.length.W)
  val wb_sel =UInt(Control.WB_ALU.length.W)
  val rf_wen =Bool()
  val rf_addr=UInt(5.W)
  val result=UInt(DATA_WIDTH.W)
  val pc  =UInt(ADDR_WIDTH.W)
  val inst=UInt(32.W)
  val pred = new PredictorOutput()
}

class ex_to_ls_bus_bundle extends Bundle{
  val bits=Output(Vec(2,new ex_to_ls_bus_data_bundle()))
  val dualmask=Output(Vec(2,Bool()))
  val allowin=Input(Bool())
}

class ex_from_ls_bus_bundle extends Bundle{
  val flush=new pipeline_flushs_bundle()
}

class ls_to_wb_bus_data_bundle extends Bundle{
  val diffLoad=new DiffLoadBundle()
  val diffStore=new DiffStoreBundle()
  val diffExcp=new DiffExcpBundle()
  val diffInstr=new Bundle{
    val is_TLBFILL=Bool()
    val TLBFILL_index=UInt(5.W)
    val timer64value=UInt(64.W)
    val is_CNTinst=Bool()
    val csr_rstat=Bool()
    val csr_data=UInt(DATA_WIDTH.W)
  }
  val perf_sys_quit=Bool()
  val perf_branch=new perf_branch_bundle() 

  val excp_en=Bool()
  val rf_wen =Bool()
  val rf_addr=UInt(5.W)
  val wb_sel =UInt(Control.WB_ALU.length.W)
  val is_scw =UInt(1.W)
  val alu_result=UInt(DATA_WIDTH.W)
  val mem_result=UInt(DATA_WIDTH.W)
  val div_result=UInt(DATA_WIDTH.W)
  val mod_result=UInt(DATA_WIDTH.W)
  val mul_result=UInt((DATA_WIDTH*2).W)
  val result=UInt(DATA_WIDTH.W)
  val pc  =UInt(ADDR_WIDTH.W)
  val inst=UInt(32.W)
}

class ls_to_wb_bus_bundle extends Bundle{
  val bits=Output(Vec(2,new ls_to_wb_bus_data_bundle()))
  val dualmask=Output(Vec(2,Bool()))
  val allowin=Input(Bool())
}

class pipeline_flushs_bundle extends Bundle{
  val refetch=Bool()
  val excp=Bool()
  val idle=Bool()
  val ertn=Bool()
  val tlbrefill=Bool()
}