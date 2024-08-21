import chisel3._
import chisel3.util._
import config.Configs._
import Control._

class DiffCommit extends Module {
  val io = IO(new Bundle {
    //DifftestInstrCommit
    val instr=Input(Vec(2,new DiffInstrBundle()))

    //DifftestExcpEvent
    val excp=Input(new DiffExcpBundle())


    //DifftestTrapEvent
    //空

    //DifftestStoreEvent
    val store=Input(Vec(2,new DiffStoreBundle()))

    //DifftestLoadEvent
    val load=Input(Vec(2,new DiffLoadBundle()))

    //DifftestCSRRegState
    val csr=Input(new DiffCsrBundle())

    val reg=Input(Vec(32, UInt(32.W)))
  })
  
  val DiffBridge=Module(new DiffBridge())
  DiffBridge.io.clock:=clock
  DiffBridge.io.coreid:=0.U

  DiffBridge.io.index_0:=0.U
  DiffBridge.io.Instrvalid_0:=RegNext(io.instr(0).valid,0.U)
  DiffBridge.io.the_pc_0:=RegNext(io.instr(0).pc,0.U)
  DiffBridge.io.instr_0:=RegNext(io.instr(0).instr,0.U)
  DiffBridge.io.skip_0:=0.U
  DiffBridge.io.is_TLBFILL_0:=RegNext(io.instr(0).is_TLBFILL,0.U)
  DiffBridge.io.TLBFILL_index_0:=RegNext(io.instr(0).TLBFILL_index,0.U)
  DiffBridge.io.is_CNTinst_0:=RegNext(io.instr(0).is_CNTinst,0.U)
  DiffBridge.io.timer_64_value_0:=RegNext(io.instr(0).timer_64_value,0.U)
  DiffBridge.io.wen_0:=RegNext(io.instr(0).wen,0.U)
  DiffBridge.io.wdest_0:=RegNext(io.instr(0).wdest,0.U)
  DiffBridge.io.wdata_0:=RegNext(io.instr(0).wdata,0.U)
  DiffBridge.io.csr_rstat_0:=RegNext(io.instr(0).csr_rstat,0.U)
  DiffBridge.io.csr_data_0:=RegNext(io.instr(0).csr_data,0.U)

  DiffBridge.io.index_1:=1.U
  DiffBridge.io.Instrvalid_1:=RegNext(io.instr(1).valid,0.U)
  DiffBridge.io.the_pc_1:=RegNext(io.instr(1).pc,0.U)
  DiffBridge.io.instr_1:=RegNext(io.instr(1).instr,0.U)
  DiffBridge.io.skip_1:=0.U
  DiffBridge.io.is_TLBFILL_1:=RegNext(io.instr(1).is_TLBFILL,0.U)
  DiffBridge.io.TLBFILL_index_1:=RegNext(io.instr(1).TLBFILL_index,0.U)
  DiffBridge.io.is_CNTinst_1:=RegNext(io.instr(1).is_CNTinst,0.U)
  DiffBridge.io.timer_64_value_1:=RegNext(io.instr(1).timer_64_value,0.U)
  DiffBridge.io.wen_1:=RegNext(io.instr(1).wen,0.U)
  DiffBridge.io.wdest_1:=RegNext(io.instr(1).wdest,0.U)
  DiffBridge.io.wdata_1:=RegNext(io.instr(1).wdata,0.U)
  DiffBridge.io.csr_rstat_1:=RegNext(io.instr(1).csr_rstat,0.U)
  DiffBridge.io.csr_data_1:=RegNext(io.instr(1).csr_data,0.U)

  DiffBridge.io.excp_valid:=RegNext(io.excp.valid,0.U)
  DiffBridge.io.eret      :=RegNext(io.excp.eret,0.U)
  DiffBridge.io.intrNo    :=RegNext(io.excp.intrNo,0.U) //NOTE:estat也是csr寄存器，在ls级结束，因此也要delay
  DiffBridge.io.cause     :=RegNext(io.excp.cause,0.U)
  DiffBridge.io.exceptionPC  :=RegNext(io.excp.pc,0.U)
  DiffBridge.io.exceptionInst:=RegNext(io.excp.inst,0.U)

  DiffBridge.io.storeValid_0:=RegNext(io.store(0).valid,0.U)
  DiffBridge.io.storeIndex_0:=0.U
  DiffBridge.io.storePaddr_0:=RegNext(io.store(0).paddr,0.U)
  DiffBridge.io.storeVaddr_0:=RegNext(io.store(0).vaddr,0.U)
  DiffBridge.io.storeData_0:=RegNext(io.store(0).data,0.U)

  DiffBridge.io.storeValid_1:=RegNext(io.store(1).valid,0.U)
  DiffBridge.io.storeIndex_1:=1.U
  DiffBridge.io.storePaddr_1:=RegNext(io.store(1).paddr,0.U)
  DiffBridge.io.storeVaddr_1:=RegNext(io.store(1).vaddr,0.U)
  DiffBridge.io.storeData_1:=RegNext(io.store(1).data,0.U)

  DiffBridge.io.loadValid_0:=RegNext(io.load(0).valid,0.U)
  DiffBridge.io.loadIndex_0:=0.U
  DiffBridge.io.loadPaddr_0:=RegNext(io.load(0).paddr,0.U)
  DiffBridge.io.loadVaddr_0:=RegNext(io.load(0).vaddr,0.U)

  DiffBridge.io.loadValid_1:=RegNext(io.load(1).valid,0.U)
  DiffBridge.io.loadIndex_1:=1.U
  DiffBridge.io.loadPaddr_1:=RegNext(io.load(1).paddr,0.U)
  DiffBridge.io.loadVaddr_1:=RegNext(io.load(1).vaddr,0.U)

  DiffBridge.io.crmd:=RegNext(io.csr.crmd,0.U) //NOTE:csr加延迟因为csr在ls级被修改
  DiffBridge.io.prmd:=RegNext(io.csr.prmd,0.U)
  DiffBridge.io.euen:=RegNext(io.csr.euen,0.U)
  DiffBridge.io.ecfg:=RegNext(io.csr.ecfg,0.U)
  DiffBridge.io.estat :=RegNext(io.csr.estat,0.U)
  DiffBridge.io.era   :=RegNext(io.csr.era,0.U)
  DiffBridge.io.badv  :=RegNext(io.csr.badv,0.U)
  DiffBridge.io.eentry:=RegNext(io.csr.eentry,0.U)
  DiffBridge.io.tlbidx:=RegNext(io.csr.tlbidx,0.U)
  DiffBridge.io.tlbehi:=RegNext(io.csr.tlbehi,0.U)
  DiffBridge.io.tlbelo0:=RegNext(io.csr.tlbelo0,0.U)
  DiffBridge.io.tlbelo1:=RegNext(io.csr.tlbelo1,0.U)
  DiffBridge.io.asid:=RegNext(io.csr.asid,0.U)
  DiffBridge.io.pgdl:=RegNext(io.csr.pgdl,0.U)
  DiffBridge.io.pgdh:=RegNext(io.csr.pgdh,0.U)
  DiffBridge.io.save0:=RegNext(io.csr.save0,0.U)
  DiffBridge.io.save1:=RegNext(io.csr.save1,0.U)
  DiffBridge.io.save2:=RegNext(io.csr.save2,0.U)
  DiffBridge.io.save3:=RegNext(io.csr.save3,0.U)
  DiffBridge.io.tid :=RegNext(io.csr.tid,0.U)
  DiffBridge.io.tcfg:=RegNext(io.csr.tcfg,0.U)
  DiffBridge.io.tval:=RegNext(io.csr.tval,0.U)
  DiffBridge.io.ticlr :=RegNext(io.csr.ticlr,0.U)
  DiffBridge.io.llbctl:=RegNext(io.csr.llbctl,0.U)
  DiffBridge.io.tlbrentry:=RegNext(io.csr.tlbrentry,0.U)
  DiffBridge.io.dmw0:=RegNext(io.csr.dmw0,0.U)
  DiffBridge.io.dmw1:=RegNext(io.csr.dmw1,0.U)

  DiffBridge.io.REG:=io.reg
}


class DiffBridge extends BlackBox with HasBlackBoxPath{
  val io=IO(new Bundle {
    val clock =Input(Clock())
    val coreid=Input(UInt(8.W))
    val index_0=Input(UInt(8.W))
    val Instrvalid_0=Input(Bool())
    val the_pc_0=Input(UInt(64.W))
    val instr_0=Input(UInt(32.W))
    val skip_0=Input(Bool())
    val is_TLBFILL_0=Input(Bool())
    val TLBFILL_index_0=Input(UInt(5.W))
    val is_CNTinst_0=Input(Bool())
    val timer_64_value_0=Input(UInt(64.W))
    val wen_0=Input(Bool())
    val wdest_0=Input(UInt(8.W))
    val wdata_0=Input(UInt(64.W))
    val csr_rstat_0=Input(Bool())          
    val csr_data_0=Input(UInt(32.W))       

    val index_1=Input(UInt(8.W))
    val Instrvalid_1=Input(Bool())
    val the_pc_1=Input(UInt(64.W))
    val instr_1=Input(UInt(32.W))
    val skip_1=Input(Bool())
    val is_TLBFILL_1=Input(Bool())
    val TLBFILL_index_1=Input(UInt(5.W))
    val is_CNTinst_1=Input(Bool())
    val timer_64_value_1=Input(UInt(64.W))
    val wen_1=Input(Bool())
    val wdest_1=Input(UInt(8.W))
    val wdata_1=Input(UInt(64.W))
    val csr_rstat_1=Input(Bool())          
    val csr_data_1=Input(UInt(32.W))       

    //DifftestExcpEvent
    val excp_valid=Input(Bool())         
    val eret=Input(Bool())               
    val intrNo=Input(UInt(32.W))         
    val cause=Input(UInt(32.W))          
    val exceptionPC=Input(UInt(64.W))    //给pc
    val exceptionInst=Input(UInt(32.W))  //给inst

    //DifftestTrapEvent
    //空

    //DifftestStoreEvent
    val storeIndex_0=Input(UInt(8.W))
    val storeValid_0=Input(UInt(8.W))
    val storePaddr_0=Input(UInt(64.W))
    val storeVaddr_0=Input(UInt(64.W))
    val storeData_0=Input(UInt(64.W))

    val storeIndex_1=Input(UInt(8.W))
    val storeValid_1=Input(UInt(8.W))
    val storePaddr_1=Input(UInt(64.W))
    val storeVaddr_1=Input(UInt(64.W))
    val storeData_1=Input(UInt(64.W))

    //DifftestLoadEvent
    val loadIndex_0=Input(UInt(8.W))
    val loadValid_0=Input(UInt(8.W))
    val loadPaddr_0=Input(UInt(64.W))
    val loadVaddr_0=Input(UInt(64.W))

    val loadIndex_1=Input(UInt(8.W))
    val loadValid_1=Input(UInt(8.W))
    val loadPaddr_1=Input(UInt(64.W))
    val loadVaddr_1=Input(UInt(64.W))

    //DifftestCSRRegState
    val crmd=Input(UInt(64.W))
    val prmd=Input(UInt(64.W))
    val euen=Input(UInt(64.W))
    val ecfg=Input(UInt(64.W))
    val estat=Input(UInt(64.W))
    val era=Input(UInt(64.W))
    val badv=Input(UInt(64.W))
    val eentry=Input(UInt(64.W))
    val tlbidx=Input(UInt(64.W))
    val tlbehi=Input(UInt(64.W))
    val tlbelo0=Input(UInt(64.W))
    val tlbelo1=Input(UInt(64.W))
    val asid=Input(UInt(64.W))
    val pgdl=Input(UInt(64.W))
    val pgdh=Input(UInt(64.W))
    val save0=Input(UInt(64.W))
    val save1=Input(UInt(64.W))
    val save2=Input(UInt(64.W))
    val save3=Input(UInt(64.W))
    val tid=Input(UInt(64.W))
    val tcfg=Input(UInt(64.W))
    val tval=Input(UInt(64.W))
    val ticlr=Input(UInt(64.W))
    val llbctl=Input(UInt(64.W))
    val tlbrentry=Input(UInt(64.W))
    val dmw0=Input(UInt(64.W))
    val dmw1=Input(UInt(64.W))

    val REG=Input(Vec(32, UInt(64.W)))
  })
  addPath("playground/src/Util/DiffTest/DiffBridge.v")
}

