import chisel3._
import chisel3.util._
import config.Configs._
import config.CacheConfig._
import config.GenCtrl

class AddrTran extends Module{
  //根据不同级发出的飞线进行绑线操作
  val io=IO(new Bundle { //TODO:打包成bundle
    val tran_en=Input(Bool())
    val vaddr=Input(UInt(ADDR_WIDTH.W))
    val paddr=Output(UInt(ADDR_WIDTH.W))
    val direct_uncache=Output(Bool()) //NOTE:组合逻辑立即给出
    val page_uncache  =Output(Bool()) //NOTE:下一拍给出，依赖tran_en使能查找
    val mode=Input(new Bundle {
      val pg=Bool()
      val da=Bool()
    })
    val plv =Input(UInt(2.W))
    val datx=Input(UInt(2.W))
    val dmw0=Input(new csr_dmw_bundle())
    val dmw1=Input(new csr_dmw_bundle())
    val tlb_ps4kb=if(GenCtrl.USE_TLB) Some(Input(Bool())) else None
    val tlb_ppn  =if(GenCtrl.USE_TLB) Some(Input(UInt(TAG_WIDTH.W))) else None
    val tlb_mat  =if(GenCtrl.USE_TLB) Some(Input(UInt(2.W))) else None
    val is_direct_map=Output(Bool())
    val is_page_map  =Output(Bool())
  })
  val vaddr_next=RegNext(io.vaddr)
  val dmw0_plv_en=((io.dmw0.plv0.asBool&&io.plv===0.U)
                ||(io.dmw0.plv3.asBool&&io.plv===3.U))
  val dmw1_plv_en=((io.dmw1.plv0.asBool&&io.plv===0.U)
                ||(io.dmw1.plv3.asBool&&io.plv===3.U))
  val dmw0_en =dmw0_plv_en&&io.mode.pg&&(io.dmw0.vseg.asUInt===io.vaddr(31,29)) //NOTE:直接映射模式dmw0
  val dmw1_en =dmw1_plv_en&&io.mode.pg&&(io.dmw1.vseg.asUInt===io.vaddr(31,29)) //NOTE:直接映射模式dmw1

  val page_map  = io.mode.pg && ~dmw0_en && ~dmw1_en
  val direct_map= ~page_map
  io.is_page_map:=page_map
  io.is_direct_map:=direct_map
  val direct_map_addr=(
      (Fill(ADDR_WIDTH,dmw0_en)&Cat(io.dmw0.pseg , io.vaddr(28,0)))
    | (Fill(ADDR_WIDTH,dmw1_en)&Cat(io.dmw1.pseg , io.vaddr(28,0)))
    | (Fill(ADDR_WIDTH,~(dmw0_en||dmw1_en))  &     io.vaddr)
  )


  val page_map_addr=Mux(io.tlb_ps4kb.getOrElse(false.B),Cat(io.tlb_ppn.getOrElse(0.U),vaddr_next(11,0)),
                                                        Cat(io.tlb_ppn.getOrElse(0.U)(19,10),vaddr_next(21,0)))
  io.paddr:=(
      (Fill(ADDR_WIDTH,RegNext(direct_map))&RegNext(direct_map_addr))
    | (Fill(ADDR_WIDTH,RegNext(page_map  ))&page_map_addr)
    | (Fill(ADDR_WIDTH,RegNext(io.mode.da))&RegNext(io.vaddr))
  )
  val direct_map_uncache=((io.mode.da&&io.datx===0.U)
                        ||(dmw0_en&&io.dmw0.mat===0.U)
                        ||(dmw1_en&&io.dmw1.mat===0.U)) 

  io.direct_uncache:=direct_map_uncache
  io.page_uncache:=(RegNext(page_map)&&io.tlb_mat.getOrElse(1.U)===0.U)&&RegNext(io.tran_en)
  //NOTE:两个结果并不是同一排出的，为了减少uncache条件下pf重取指的惩罚
  dontTouch(io.direct_uncache)
  dontTouch(io.page_uncache)
} 