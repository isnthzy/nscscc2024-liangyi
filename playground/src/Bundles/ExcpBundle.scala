import chisel3._
import chisel3.util._  
import config.Configs._


class pf_excp_bundle extends Bundle{
  val num=UInt(1.W)
}

class if_excp_bundle extends Bundle{
  val ppi=UInt(1.W)
  val pif=UInt(1.W)
  val tlbr=UInt(1.W)
  val num=new pf_excp_bundle()
}

class id_excp_bundle extends Bundle{
  val ipe=UInt(1.W)
  val ine=UInt(1.W)
  val break=UInt(1.W)
  val syscall=UInt(1.W)
  val num=new if_excp_bundle()
  val has_int=UInt(1.W)
}


class ex_excp_bundle extends Bundle{
  val ale=UInt(1.W)
  val num=new id_excp_bundle()
}

class ls_excp_bundle extends Bundle{
  val pil=UInt(1.W)
  val pis=UInt(1.W)
  val ppi=UInt(1.W)
  val pme=UInt(1.W)
  val tlbr=UInt(1.W)
  val zero=UInt(1.W)
  val num=new ex_excp_bundle()
}

class excp_result_bundle extends Bundle{
  val ecode    =UInt(6.W)
  val va_error =Bool()
  val va_bad_addr=UInt(ADDR_WIDTH.W)
  val esubcode =UInt(9.W)
  val tlbrefill=Bool()
  val tlb_excp =Bool()
}

object ECODE{
  val INT=0.U(6.W)
  val PIL=1.U(6.W)
  val PIS=2.U(6.W)
  val PIF=3.U(6.W)
  val PME=4.U(6.W)
  val PPI=7.U(6.W)
  val ADEF=8.U(6.W)
  val ALE=9.U(6.W)
  val SYS=11.U(6.W)
  val BRK=12.U(6.W)
  val INE=13.U(6.W)
  val IPE=14.U(6.W)
  val FPD=15.U(6.W)
  val TLBR=63.U(6.W)
}

object ESUBCODE{
  val ADEF=0.U(9.W)
}