import chisel3._
import chisel3.util._
import config.Configs._
import config._
import Control._
import chisel3.experimental.BundleLiterals._

object CsrName {
  val CRMD  =0.U(14.W)
  val PRMD  =1.U(14.W)
  val ECFG  =4.U(14.W)
  val ESTAT =5.U(14.W)
  val ERA   =6.U(14.W)
  val BADV  =7.U(14.W)
  val EENTRY=12.U(14.W)
  val TLBIDX=16.U(14.W)
  val TLBEHI=17.U(14.W)
  val TLBELO0=18.U(14.W)
  val TLBELO1=19.U(14.W)
  val ASID  =24.U(14.W)
  val PGDL  =25.U(14.W)
  val PGDH  =26.U(14.W)
  val PGD   =27.U(14.W)
  val SAVE0 =48.U(14.W)
  val SAVE1 =49.U(14.W)
  val SAVE2 =50.U(14.W)
  val SAVE3 =51.U(14.W)
  val TID   =64.U(14.W)
  val TCFG  =65.U(14.W)
  val TVAL  =66.U(14.W)
  val CNTC  =67.U(14.W)
  val TICLR =68.U(14.W)
  val LLBCTL=96.U(14.W)
  val TLBRENTRY=136.U(14.W)
  val DMW0  =384.U(14.W)
  val DMW1  =385.U(14.W)
}

import CsrName._
class CsrFile extends Module{
  val io=IO(new Bundle{
    val to_csr=Input(new ls_to_csr_bundle())
    val from_csr=new pipeline_from_csr_bundle()
    val csr_entries=Output(new csr_entrise_bundle())
    //interrupt
    val interrupt=Input(UInt(8.W))
    //tlb_Bus
    val tlbtranIO=if(GenCtrl.USE_TLB) Some(new tlbtran_io_bundle()) else None
    val mmutranIO=new mmutran_io_bundle()
    //to_diff
    val diff_csr=Output(new DiffCsrBundle())
  }) //NOTE:rd和wr的命名可读性较差  
  
  //reset and assign
  val csr_crmd  =RegInit((new csr_crmd_bundle()).Lit(
    _.plv ->0.U,
    _.ie  ->0.U,
    _.da  ->1.U,
    _.pg  ->0.U,
    _.datf->0.U,
    _.datm->0.U,
    _.zero->0.U
  ))
  val csr_prmd      =RegInit(0.U.asTypeOf(new csr_prmd_bundle()))//Reg(new csr_prmd_bundle())
  val csr_ecfg      =RegInit(0.U.asTypeOf(new csr_ecfg_bundle()))//Reg(new csr_ecfg_bundle())
  val csr_estat     =RegInit(0.U.asTypeOf(new csr_estat_bundle()))
  val csr_era       =RegInit(0.U(32.W))
  val csr_badv      =RegInit(0.U(32.W))
  val csr_eentry    =RegInit(0.U.asTypeOf(new csr_eentry_bundle()))//Reg(new csr_eentry_bundle())
  val csr_tlbidx    =RegInit(0.U.asTypeOf(new csr_tlbidx_bundle()))//Reg(new csr_tlbidx_bundle())
  val csr_tlbehi    =RegInit(0.U.asTypeOf(new csr_tlbehi_bundle()))//Reg(new csr_tlbehi_bundle())
  val csr_tlbelo0   =RegInit(0.U.asTypeOf(new csr_tlbelo_bundle()))//Reg(new csr_tlbelo_bundle())
  val csr_tlbelo1   =RegInit(0.U.asTypeOf(new csr_tlbelo_bundle()))//Reg(new csr_tlbelo_bundle())
  val csr_asid      =RegInit(0.U.asTypeOf(new csr_asid_bundle()))//Reg(new csr_asid_bundle())
  val csr_pgdl      =RegInit(0.U.asTypeOf(new csr_pgdx_bundle()))//Reg(new csr_pgdx_bundle())
  val csr_pgdh      =RegInit(0.U.asTypeOf(new csr_pgdx_bundle()))//Reg(new csr_pgdx_bundle())
  val csr_pgd       =RegInit(0.U.asTypeOf(new csr_pgdx_bundle()))//Reg(new csr_pgdx_bundle())
  val csr_save0     =RegInit(0.U(32.W))
  val csr_save1     =RegInit(0.U(32.W))
  val csr_save2     =RegInit(0.U(32.W))
  val csr_save3     =RegInit(0.U(32.W))
  val csr_tid       =RegInit(0.U(32.W))
  val csr_tcfg      =RegInit(0.U.asTypeOf(new csr_tcfg_bundle()))
  val csr_tval      =RegInit(0.U(32.W))
  val csr_cntc      =RegInit(0.U(32.W))
  val csr_ticlr     =RegInit(0.U(32.W))
  val csr_llbctl    =RegInit(0.U.asTypeOf(new csr_llbctl_bundle()))     //(new csr_llbctl_bundle())
  val csr_tlbrentry =RegInit(0.U.asTypeOf(new csr_tlbrentry_bundle()))  //Reg(new csr_tlbrentry_bundle())
  val csr_dmw0      =RegInit(0.U.asTypeOf(new csr_dmw_bundle()))
  val csr_dmw1      =RegInit(0.U.asTypeOf(new csr_dmw_bundle()))

  //NOTE:TIMER64定时器
  val timer64   =RegInit(0.U(64.W))
  timer64:=timer64+1.U
  val tval_en  =RegInit(false.B)
  //NOTE:tval_en为1时，定时器开始倒数

  val tlbrd_empty=Wire(Bool())
  val tlbrd_unempty=Wire(Bool())
  io.mmutranIO.to_mmu.dmw0  :=csr_dmw0
  io.mmutranIO.to_mmu.dmw1  :=csr_dmw1
  io.mmutranIO.to_mmu.da    :=csr_crmd.da
  io.mmutranIO.to_mmu.pg    :=csr_crmd.pg
  io.mmutranIO.to_mmu.plv   :=csr_crmd.plv
  io.mmutranIO.to_mmu.datf:=csr_crmd.datf
  io.mmutranIO.to_mmu.datm:=csr_crmd.datm
  if(GenCtrl.USE_TLB){
    tlbrd_empty  :=io.tlbtranIO.get.to_csr.tlbrd_en&&  io.tlbtranIO.get.to_csr.tlbidx.ne.asBool
    tlbrd_unempty:=io.tlbtranIO.get.to_csr.tlbrd_en&& ~io.tlbtranIO.get.to_csr.tlbidx.ne.asBool 
    io.tlbtranIO.get.to_tlb.rand_idx:=timer64(3,0)
    io.tlbtranIO.get.to_tlb.ecode3f:=csr_estat.ecode==="h3f".U
    io.tlbtranIO.get.to_tlb.tlbidx:=csr_tlbidx
    io.tlbtranIO.get.to_tlb.tlbehi:=csr_tlbehi
    io.tlbtranIO.get.to_tlb.tlbelo0:=csr_tlbelo0
    io.tlbtranIO.get.to_tlb.tlbelo1:=csr_tlbelo1
    io.tlbtranIO.get.to_tlb.asid   :=csr_asid
  }else{
    tlbrd_empty  :=false.B
    tlbrd_unempty:=false.B
  }

  //
  io.from_csr.has_int:=(((Cat(csr_ecfg.lie12_11,csr_ecfg.zero10,csr_ecfg.lie9_0) //csr_ecfg_lie12_0
                        &Cat(csr_estat.is12,csr_estat.is11,csr_estat.zero10,csr_estat.is9_2,csr_estat.is1_0))=/=0.U(13.W)) //csr_estat_12_0
                        &csr_crmd.ie)
  io.csr_entries.era:=csr_era
  io.csr_entries.entry:=csr_eentry.asUInt
  io.csr_entries.tlbentry:=csr_tlbrentry.asUInt
  io.from_csr.llbit_out:=csr_llbctl.rollb


  //READ
  val csrMap=Map(
    CRMD  -> csr_crmd.asUInt,
    PRMD  -> csr_prmd.asUInt,
    ECFG  -> csr_ecfg.asUInt,
    ESTAT -> csr_estat.asUInt,
    ERA   -> csr_era,
    BADV  -> csr_badv,
    EENTRY-> csr_eentry.asUInt,
    TLBIDX-> csr_tlbidx.asUInt,
    TLBEHI-> csr_tlbehi.asUInt,
    TLBELO0->csr_tlbelo0.asUInt,
    TLBELO1->csr_tlbelo1.asUInt,
    ASID  -> csr_asid.asUInt,
    PGDL  -> csr_pgdl.asUInt,
    PGDH  -> csr_pgdh.asUInt,
    PGD   -> Mux(csr_badv(31),csr_pgdh.asUInt,csr_pgdl.asUInt),
    SAVE0 -> csr_save0,
    SAVE1 -> csr_save1,
    SAVE2 -> csr_save2,
    SAVE3 -> csr_save3,
    TID   -> csr_tid,
    TCFG  -> csr_tcfg.asUInt,
    TVAL  -> csr_tval,
    CNTC  -> csr_cntc,
    TICLR -> csr_ticlr,
    // LLBCTL-> Cat(csr_llbctl.asUInt(31,1),Mux(io.llbit_set_zero,0.U,csr_llbctl.rollb)),
    LLBCTL-> csr_llbctl.asUInt,
    TLBRENTRY->csr_tlbrentry.asUInt,
    DMW0  -> csr_dmw0.asUInt,
    DMW1  -> csr_dmw1.asUInt,
  )
  //NOTE:with_ex.rd_data的结果在ex级出，因此需要delay一拍
  io.from_csr.rd_data:=Mux1hMap(io.from_csr.rd_addr,csrMap)
  io.from_csr.timer_out:=timer64
//wire assign
  val ticlr_clr_w1=Wire(Bool())

//write
  //NOTE:crmd
  val crmd_wdata=io.to_csr.wr_data.asTypeOf(new csr_crmd_bundle())
  when(io.to_csr.excp_flush){
    csr_crmd.plv :=0.U
    csr_crmd.ie  :=0.U 
    //TODO:TLB例外处理
    when(io.to_csr.excp_result.tlbrefill){
      csr_crmd.da:=1.U
      csr_crmd.pg:=0.U
    }
  }.elsewhen(io.to_csr.ertn_flush){
    csr_crmd.plv :=csr_prmd.pplv
    csr_crmd.ie  :=csr_prmd.pie
    //TODO:TLB例外处理
    when(csr_estat.ecode==="h3f".U){
      csr_crmd.da:=0.U
      csr_crmd.pg:=1.U
    }
  }.elsewhen(io.to_csr.csr_wen&&(io.to_csr.wr_addr===CRMD)){
    csr_crmd.plv :=crmd_wdata.plv
    csr_crmd.ie  :=crmd_wdata.ie 
    csr_crmd.da  :=crmd_wdata.da 
    csr_crmd.pg  :=crmd_wdata.pg 
    csr_crmd.datf:=crmd_wdata.datf
    csr_crmd.datm:=crmd_wdata.datm
  }

  //NOTE:prmd
  val prmd_wdata=io.to_csr.wr_data.asTypeOf(new csr_prmd_bundle())
  when(io.to_csr.excp_flush){
    csr_prmd.pie :=csr_crmd.ie
    csr_prmd.pplv:=csr_crmd.plv
  }.elsewhen(io.to_csr.csr_wen&&io.to_csr.wr_addr===PRMD){
    csr_prmd.pie :=prmd_wdata.pie
    csr_prmd.pplv:=prmd_wdata.pplv
  }

  //NOTE:ecfg
  val ecfg_wdata=io.to_csr.wr_data.asTypeOf(new csr_ecfg_bundle())
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===ECFG){
    csr_ecfg.lie9_0:=ecfg_wdata.lie9_0
    csr_ecfg.lie12_11:=ecfg_wdata.lie12_11
  }

  //NOTE:estat
  val estat_wdata=io.to_csr.wr_data.asTypeOf(new csr_estat_bundle())
  when(ticlr_clr_w1){
    csr_estat.is11:=0.U
  }.elsewhen(tval_en&&(csr_tval===0.U)){
    csr_estat.is11:=1.U
    tval_en:=csr_tcfg.periodic
    //倒数结束后，置高中断,再由periodic决定是否继续倒数
  }
  csr_estat.is9_2:=io.interrupt
  when(io.to_csr.excp_flush){
    csr_estat.ecode:=io.to_csr.excp_result.ecode
    csr_estat.esubcode:=io.to_csr.excp_result.esubcode
  }.elsewhen(io.to_csr.csr_wen&&io.to_csr.wr_addr===ESTAT){
    csr_estat.is1_0:=estat_wdata.is1_0
  }

  //NOTE:era
  when(io.to_csr.excp_flush){
    csr_era:=io.to_csr.era_in
  }.elsewhen(io.to_csr.csr_wen&&io.to_csr.wr_addr===ERA){
    csr_era:=io.to_csr.wr_data
  }

  //NOTE:badv
  when(io.to_csr.excp_result.va_error){
    csr_badv:=io.to_csr.excp_result.va_bad_addr
  }.elsewhen(io.to_csr.csr_wen&&io.to_csr.wr_addr===BADV){
    csr_badv:=io.to_csr.wr_data
  }

  //NOTE:eentry
  val eentry_wdata=io.to_csr.wr_data.asTypeOf(new csr_eentry_bundle())
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===EENTRY){
    csr_eentry.va:=eentry_wdata.va
  }

  //NOTE:save0-3
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===SAVE0){  csr_save0:=io.to_csr.wr_data }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===SAVE1){  csr_save1:=io.to_csr.wr_data }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===SAVE2){  csr_save2:=io.to_csr.wr_data }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===SAVE3){  csr_save3:=io.to_csr.wr_data }

  //NOTE:tid
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TID  ){  csr_tid  :=io.to_csr.wr_data}

  //NOTE:tcfg
  val tcfg_wdata=io.to_csr.wr_data.asTypeOf(new csr_tcfg_bundle())
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TCFG){
    tval_en:=tcfg_wdata.en
    csr_tcfg.en      :=tcfg_wdata.en
    csr_tcfg.periodic:=tcfg_wdata.periodic
    csr_tcfg.initval :=tcfg_wdata.initval
  }

  //NOTE:tval
  //NOTE:软件只读，需要硬件控制写
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TCFG){
    csr_tval:=Cat(tcfg_wdata.initval,0.U(2.W))
  }.elsewhen(tval_en){
    when(csr_tval=/=0.U){
      csr_tval:=csr_tval-1.U
    }.elsewhen(csr_tval===0.U){
      csr_tval:=Mux(csr_tcfg.periodic.asBool,Cat(csr_tcfg.initval,0.U(2.W)),"hffffffff".U)
    }
  }
  
  //NOTE:ticlr
  ticlr_clr_w1:=io.to_csr.wr_data(0)&&io.to_csr.wr_addr===TICLR&&io.to_csr.csr_wen
  //io.to_csr.wr_data(0)===ticlr.clr

  //NOTE:dmw0-1
  val dmw_wdata=io.to_csr.wr_data.asTypeOf(new csr_dmw_bundle())
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===DMW0){
    csr_dmw0.vseg:=dmw_wdata.vseg
    csr_dmw0.pseg:=dmw_wdata.pseg
    csr_dmw0.plv3:=dmw_wdata.plv3
    csr_dmw0.plv0:=dmw_wdata.plv0
    csr_dmw0.mat :=dmw_wdata.mat
  }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===DMW1){
    csr_dmw1.vseg:=dmw_wdata.vseg
    csr_dmw1.pseg:=dmw_wdata.pseg
    csr_dmw1.plv3:=dmw_wdata.plv3
    csr_dmw1.plv0:=dmw_wdata.plv0
    csr_dmw1.mat :=dmw_wdata.mat
  }

  val llbctl_wdata=io.to_csr.wr_data.asTypeOf(new csr_llbctl_bundle())
  when(io.to_csr.ertn_flush){
    when(csr_llbctl.klo.asBool){
      csr_llbctl.klo:=0.U
    }.otherwise{
      csr_llbctl.rollb:=0.U
    }
  }.elsewhen(io.to_csr.csr_wen&&io.to_csr.wr_addr===LLBCTL){
    csr_llbctl.klo:=llbctl_wdata.klo
    when(llbctl_wdata.wcllb.asBool){
      csr_llbctl.rollb:=0.U
    }
  }.elsewhen(io.to_csr.llbit_set_en){
    csr_llbctl.rollb:=io.to_csr.llbit_in
  }

//NOTE:单独维护一个tlb组
  // val 
  val tlbidx_wdata=io.to_csr.wr_data.asTypeOf(new csr_tlbidx_bundle())
  val tlbehi_wdata=io.to_csr.wr_data.asTypeOf(new csr_tlbehi_bundle())
  val tlbelo_wdata=io.to_csr.wr_data.asTypeOf(new csr_tlbelo_bundle())
  val asid_wdata  =io.to_csr.wr_data.asTypeOf(new csr_asid_bundle())
  val pgdx_wdata  =io.to_csr.wr_data.asTypeOf(new csr_pgdx_bundle())
  val tlbrentry_wdata=io.to_csr.wr_data.asTypeOf(new csr_tlbrentry_bundle())
  if(GenCtrl.USE_TLB){ //NOTE:
    when(tlbrd_unempty){
      csr_tlbidx.ne  :=0.U
      csr_asid.asid  :=io.tlbtranIO.get.to_csr.asid.asid
      csr_tlbidx.ps  :=io.tlbtranIO.get.to_csr.tlbidx.ps
      csr_tlbehi.vppn:=io.tlbtranIO.get.to_csr.tlbehi.vppn
      csr_tlbelo0.v  :=io.tlbtranIO.get.to_csr.tlbelo0.v
      csr_tlbelo0.d  :=io.tlbtranIO.get.to_csr.tlbelo0.d
      csr_tlbelo0.plv:=io.tlbtranIO.get.to_csr.tlbelo0.plv
      csr_tlbelo0.mat:=io.tlbtranIO.get.to_csr.tlbelo0.mat
      csr_tlbelo0.g  :=io.tlbtranIO.get.to_csr.tlbelo0.g
      csr_tlbelo0.ppn:=io.tlbtranIO.get.to_csr.tlbelo0.ppn
      csr_tlbelo1.v  :=io.tlbtranIO.get.to_csr.tlbelo1.v
      csr_tlbelo1.d  :=io.tlbtranIO.get.to_csr.tlbelo1.d
      csr_tlbelo1.plv:=io.tlbtranIO.get.to_csr.tlbelo1.plv
      csr_tlbelo1.mat:=io.tlbtranIO.get.to_csr.tlbelo1.mat
      csr_tlbelo1.g  :=io.tlbtranIO.get.to_csr.tlbelo1.g
      csr_tlbelo1.ppn:=io.tlbtranIO.get.to_csr.tlbelo1.ppn
    }.elsewhen(tlbrd_empty){
      csr_tlbidx.ne:=1.U
      csr_tlbidx.ps:=0.U
      csr_asid.asid:=0.U
      csr_tlbehi.vppn:=0.U
      csr_tlbelo0:=0.U.asTypeOf(new csr_tlbelo_bundle())
      csr_tlbelo1:=0.U.asTypeOf(new csr_tlbelo_bundle())
    }
    when(io.tlbtranIO.get.to_csr.tlbsrch_wen){
      when(io.tlbtranIO.get.to_csr.tlbsrch_hit){
        csr_tlbidx.idx:=io.tlbtranIO.get.to_csr.tlbidx.idx
        csr_tlbidx.ne :=0.U
      }.otherwise{
        csr_tlbidx.ne :=1.U
      }
    }
  }

  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TLBIDX){
    csr_tlbidx.idx:=tlbidx_wdata.idx
    csr_tlbidx.ps :=tlbidx_wdata.ps
    csr_tlbidx.ne :=tlbidx_wdata.ne
  }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TLBEHI){ 
    csr_tlbehi.vppn:=tlbehi_wdata.vppn 
  }.elsewhen(io.to_csr.excp_result.tlb_excp){
    csr_tlbehi.vppn:=io.to_csr.excp_result.va_bad_addr.asTypeOf(new csr_tlbehi_bundle()).vppn
  }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TLBELO0){
    csr_tlbelo0.v  :=tlbelo_wdata.v
    csr_tlbelo0.d  :=tlbelo_wdata.d
    csr_tlbelo0.plv:=tlbelo_wdata.plv
    csr_tlbelo0.mat:=tlbelo_wdata.mat
    csr_tlbelo0.g  :=tlbelo_wdata.g
    csr_tlbelo0.ppn:=tlbelo_wdata.ppn
  }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TLBELO1){
    csr_tlbelo1.v  :=tlbelo_wdata.v
    csr_tlbelo1.d  :=tlbelo_wdata.d
    csr_tlbelo1.plv:=tlbelo_wdata.plv
    csr_tlbelo1.mat:=tlbelo_wdata.mat
    csr_tlbelo1.g  :=tlbelo_wdata.g
    csr_tlbelo1.ppn:=tlbelo_wdata.ppn
  }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===ASID){ 
    csr_asid.asid:=asid_wdata.asid 
    csr_asid.asidbits:=asid_wdata.asidbits
  }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===PGDL){ csr_pgdl.base:=pgdx_wdata.base }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===PGDH){ csr_pgdh.base:=pgdx_wdata.base }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===PGD ){ csr_pgd .base:=pgdx_wdata.base }
  when(io.to_csr.csr_wen&&io.to_csr.wr_addr===TLBRENTRY){ csr_tlbrentry.pa:=tlbrentry_wdata.pa }



  io.diff_csr.crmd:=csr_crmd.asUInt
  io.diff_csr.prmd:=csr_prmd.asUInt
  io.diff_csr.euen:=0.U
  io.diff_csr.ecfg:=csr_ecfg.asUInt
  io.diff_csr.estat:=csr_estat.asUInt
  io.diff_csr.era:=csr_era
  io.diff_csr.badv:=csr_badv
  io.diff_csr.eentry :=csr_eentry.asUInt
  io.diff_csr.tlbidx :=csr_tlbidx.asUInt
  io.diff_csr.tlbehi :=csr_tlbehi.asUInt
  io.diff_csr.tlbelo0:=csr_tlbelo0.asUInt
  io.diff_csr.tlbelo1:=csr_tlbelo1.asUInt
  io.diff_csr.asid:=csr_asid.asUInt
  io.diff_csr.pgdl:=csr_pgdl.asUInt
  io.diff_csr.pgdh:=csr_pgdh.asUInt
  io.diff_csr.save0:=csr_save0
  io.diff_csr.save1:=csr_save1
  io.diff_csr.save2:=csr_save2
  io.diff_csr.save3:=csr_save3
  io.diff_csr.tid :=csr_tid
  io.diff_csr.tcfg:=csr_tcfg.asUInt
  io.diff_csr.tval:=csr_tval
  io.diff_csr.ticlr :=csr_ticlr
  io.diff_csr.llbctl:=csr_llbctl.asUInt
  io.diff_csr.tlbrentry:=csr_tlbrentry.asUInt
  io.diff_csr.dmw0:=csr_dmw0.asUInt
  io.diff_csr.dmw1:=csr_dmw1.asUInt
}


