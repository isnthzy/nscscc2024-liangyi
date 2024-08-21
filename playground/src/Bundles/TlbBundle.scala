import chisel3._
import chisel3.util._  
import config.Configs._

class tlbtran_to_csr_bundle extends Bundle{
  //from_ls
  val tlbsrch_hit=Bool()
  val tlbsrch_wen=Bool()
  val tlbrd_en=Bool()
  val tlbidx =new csr_tlbidx_bundle()
  val tlbehi =new csr_tlbehi_bundle()
  val tlbelo0=new csr_tlbelo_bundle()
  val tlbelo1=new csr_tlbelo_bundle()
  val asid   =new csr_asid_bundle()
}
class tlbtran_to_tlb_bundle extends Bundle{
  val rand_idx=UInt(log2Ceil(TLB_NUM).W)
  val ecode3f=Bool()

  val tlbidx =new csr_tlbidx_bundle()
  val tlbehi =new csr_tlbehi_bundle()
  val tlbelo0=new csr_tlbelo_bundle()
  val tlbelo1=new csr_tlbelo_bundle()
  val asid   =new csr_asid_bundle()

}
class tlbtran_io_bundle extends Bundle{
  val to_csr=Input(new tlbtran_to_csr_bundle())
  val to_tlb=Output(new tlbtran_to_tlb_bundle())
}
class mmutran_to_tlb_bundle extends Bundle{
  val da  =Bool()
  val pg  =Bool()
  val plv =UInt(2.W)
  val dmw0=new csr_dmw_bundle()
  val dmw1=new csr_dmw_bundle()
  val datf   =UInt(2.W)
  val datm   =UInt(2.W)
}

class mmutran_io_bundle extends Bundle{
  val to_mmu=Output(new mmutran_to_tlb_bundle())
}

class inst_tlb_excp_bundle extends Bundle{
  val tlbr=Bool()
  val pif =Bool()
  val ppi =Bool()
}

class data_tlb_excp_bundle extends Bundle{
  val tlbr=Bool()
  val pme =Bool()
  val ppi =Bool()
  val pix =Bool()
}

class inst_vaddr_to_paddr_bundle extends Bundle{
  val addr_en=Input(Bool())
  val vaddr  =Input(UInt(ADDR_WIDTH.W))
  val paddr  =Output(UInt(ADDR_WIDTH.W))
  val excp   =Output(new inst_tlb_excp_bundle())
  val direct_uncache=Output(Bool())
  val page_uncache  =Output(Bool())
} 

class data_vaddr_to_paddr_bundle extends Bundle{
  val addr_en=Input(Bool())
  val vaddr  =Input(UInt(ADDR_WIDTH.W))
  val paddr  =Output(UInt(ADDR_WIDTH.W))
  val excp   =Output(new data_tlb_excp_bundle())
  val uncache=Output(Bool())
} 


class tlb_s_bundle extends Bundle{
  val fetch=Input(Bool())
  val vppn =Input(UInt(19.W))
  val va_12=Input(UInt(1.W))
  val asid =Input(UInt(10.W))

  val hit  =Output(Bool())

  val index=Output(UInt(log2Ceil(TLB_NUM).W))
  val ps4kb=Output(UInt(6.W))
  val ppn  =Output(UInt(20.W))
  val v    =Output(UInt(1.W))
  val d    =Output(UInt(1.W))
  val mat  =Output(UInt(2.W))
  val plv  =Output(UInt(2.W))
}

class tlb_w_and_r_bundle extends Bundle{
  val vppn=UInt(19.W)
  val asid=UInt(10.W)
  val g   =UInt(1.W)
  val ps  =UInt(6.W)
  val e   =UInt(1.W)
  val v0  =UInt(1.W)
  val d0  =UInt(1.W)
  val mat0=UInt(2.W)
  val plv0=UInt(2.W)
  val ppn0=UInt(20.W)
  val v1  =UInt(1.W)
  val d1  =UInt(1.W)
  val mat1=UInt(2.W)
  val plv1=UInt(2.W)
  val ppn1=UInt(20.W)
}

class ls_to_mmuctrl_bundle extends Bundle{
  val tlbrd_en   =Bool()
  val tlbwr_en   =Bool()
  val tlbsrch_wen=Bool()
  val tlbfill_en =Bool()

  val invtlb_en  =Bool()
  val invtlb_op  =UInt(5.W)
  val invtlb_asid=UInt(10.W)
  val invtlb_va  =UInt(ADDR_WIDTH.W)
}

class ex_to_mmuctrl_bundle extends Bundle{
  val tlbsrch_en=Bool()
  val is_tlbsrch=Bool()
}

class mmu_with_tlb_inv_bundle extends Bundle{
  val en=Bool()
  val op=UInt(5.W)
  val asid=UInt(10.W)
  val va=UInt(19.W)
}