
import chisel3._
import chisel3.util._  
import config.Configs._


class DiffInstrBundle extends Bundle{
    val valid=Bool()
    val pc=UInt(32.W)
    val instr=UInt(32.W)
    val skip=Bool()
    val is_TLBFILL=Bool()
    val TLBFILL_index=UInt(log2Ceil(TLB_NUM).W)
    val is_CNTinst=Bool()
    val timer_64_value=UInt(64.W)
    val wen=Bool()
    val wdest=UInt(8.W)
    val wdata=UInt(32.W)
    val csr_rstat=Bool()
    val csr_data=UInt(32.W)
}

class DiffCsrBundle extends Bundle{
    val crmd=UInt(32.W)
    val prmd=UInt(32.W)
    val euen=UInt(32.W)
    val ecfg=UInt(32.W)
    val estat=UInt(32.W)
    val era=UInt(32.W)
    val badv=UInt(32.W)
    val eentry=UInt(32.W)
    val tlbidx=UInt(32.W)
    val tlbehi=UInt(32.W)
    val tlbelo0=UInt(32.W)
    val tlbelo1=UInt(32.W)
    val asid=UInt(32.W)
    val pgdl=UInt(32.W)
    val pgdh=UInt(32.W)
    val save0=UInt(32.W)
    val save1=UInt(32.W)
    val save2=UInt(32.W)
    val save3=UInt(32.W)
    val tid=UInt(32.W)
    val tcfg=UInt(32.W)
    val tval=UInt(32.W)
    val ticlr=UInt(32.W)
    val llbctl=UInt(32.W)
    val tlbrentry=UInt(32.W)
    val dmw0=UInt(32.W)
    val dmw1=UInt(32.W)
}

class DiffStoreBundle extends Bundle{
    val valid=UInt(8.W)
    val paddr=UInt(32.W)
    val vaddr=UInt(32.W)
    val data=UInt(32.W)
}

class DiffLoadBundle extends Bundle{
    val valid=UInt(8.W)
    val paddr=UInt(32.W)
    val vaddr=UInt(32.W)
}

class DiffExcpBundle extends Bundle{
    val valid=Bool()
    val eret=Bool()
    val intrNo=UInt(32.W)
    val cause=UInt(32.W)
    val pc=UInt(32.W)
    val inst=UInt(32.W)
}

class DiffExcpEventBundle extends Bundle{
    val valid=Bool()
    val eret=Bool()
    val ecode=UInt(6.W)
    val pc=UInt(32.W)
    val inst=UInt(32.W)
} //NOTE:单独创立这个bundle是为了在ws充当线的作用
