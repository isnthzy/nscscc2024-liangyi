import chisel3._
import chisel3.util._  
import config.Configs._
import java.util.spi.ResourceBundleProvider


class csr_crmd_bundle extends Bundle{
  val zero=UInt(23.W)
  val datm=UInt(2.W)
  val datf=UInt(2.W)
  val pg  =UInt(1.W)
  val da  =UInt(1.W)
  val ie  =UInt(1.W)
  val plv =UInt(2.W)
}

class csr_prmd_bundle extends Bundle{
  val zero=UInt(29.W)
  val pie =UInt(1.W)
  val pplv=UInt(2.W)
}

class csr_estat_bundle extends Bundle{
  val zero31   =UInt(1.W)
  val esubcode =UInt(9.W)
  val ecode    =UInt(6.W)
  val zero15_13=UInt(3.W)
  val is12     =UInt(1.W)
  val is11     =UInt(1.W)
  val zero10   =UInt(1.W)
  val is9_2    =UInt(8.W)
  val is1_0    =UInt(2.W)
}

class csr_eentry_bundle extends Bundle{
  val va  =UInt(26.W)
  val zero=UInt(6.W)
}

class csr_tcfg_bundle extends Bundle{
  val initval =UInt(30.W)
  val periodic=UInt(1.W)
  val en      =UInt(1.W)
}

class csr_ecfg_bundle extends Bundle{
  val zero31_13=UInt(19.W)
  val lie12_11 =UInt(2.W)
  val zero10   =UInt(1.W)
  val lie9_0   =UInt(10.W)
}



class csr_tlbidx_bundle extends Bundle{
  val ne=UInt(1.W)
  val zero30=UInt(1.W)
  val ps=UInt(6.W)
  val zero23_16=UInt(8.W)
  val idx=UInt(16.W)
}

class csr_tlbehi_bundle extends Bundle{
  val vppn    =UInt(19.W)
  val zero12_0=UInt(13.W)
}

class csr_tlbelo_bundle extends Bundle{
  val ppn  =UInt(24.W)
  val zero7=UInt(1.W)
  val g    =UInt(1.W)
  val mat  =UInt(2.W)
  val plv  =UInt(2.W)
  val d    =UInt(1.W)
  val v    =UInt(1.W)
}

class csr_asid_bundle extends Bundle{
  val zero31_24=UInt(8.W)
  val asidbits =UInt(8.W)
  val zero15_10=UInt(6.W)
  val asid     =UInt(10.W)
}

class csr_pgdx_bundle extends Bundle{
  //NOTE:pgd_x -> pgdl or pgdh or pgd
  val base    =UInt(20.W)
  val zero11_0=UInt(12.W)
}

class csr_tlbrentry_bundle extends Bundle{
  val pa      =UInt(26.W)
  val zero5_0 =UInt(6.W)
}

class csr_dmw_bundle extends Bundle{
  val vseg    =UInt(3.W)
  val zero28  =UInt(1.W)
  val pseg    =UInt(3.W)
  val zero24_6=UInt(19.W)
  val mat     =UInt(2.W)
  val plv3    =UInt(1.W)
  val zero2_1 =UInt(2.W)
  val plv0    =UInt(1.W)
}

class csr_llbctl_bundle extends Bundle{
  val zero31_3=UInt(29.W)
  val klo     =UInt(1.W)
  val wcllb   =UInt(1.W)
  val rollb   =UInt(1.W)
}

class pipeline_from_csr_bundle extends Bundle{
  val rd_addr =Input(UInt(14.W))
  val has_int =Output(Bool())
  val llbit_out=Output(Bool())
  val rd_data =Output(UInt(32.W))
  val timer_out=Output(UInt((DATA_WIDTH*2).W))
}

class ls_to_csr_bundle extends Bundle{
  val csr_wen=Bool()
  val wr_addr=UInt(14.W)
  val wr_data=UInt(32.W)
  val excp_flush=Bool()
  val ertn_flush=Bool()
  val era_in    =UInt(ADDR_WIDTH.W)
  val excp_result =new excp_result_bundle()
  val llbit_set_en=Bool()
  val llbit_in    =Bool()
}

class csr_entrise_bundle extends Bundle{
  val entry=UInt(ADDR_WIDTH.W)
  val tlbentry=UInt(ADDR_WIDTH.W)
  val era=UInt(ADDR_WIDTH.W)
}

class ex_to_ls_pipeline_csr_data_bundle extends Bundle{
  val op  =UInt(Control.CSR_XXXX.length.W)
  val wen =Bool()
  val addr=UInt(14.W)
  val wdata=UInt(DATA_WIDTH.W)
}
