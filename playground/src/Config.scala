package config
import chisel3._
import chisel3.util.log2Ceil

object Configs {
  def START_ADDR = "h1c000000".U(32.W)
  def ADDR_WIDTH = 32 // 地址位宽
  def ADDR_BYTE_WIDTH = ADDR_WIDTH / 8    // 地址位宽按字节算
  def INST_WIDTH = 32 // 指令位宽
  def DATA_WIDTH = 32 // 数据位宽
  def DATA_WIDTH_H = 16   // 半字数据位宽
  def DATA_WIDTH_B = 8    // 字节数据位宽

  def TLB_NUM=16
  def INST_QUEUE_SIZE=8
  def ISSUE_QUEUE_SIZE=8
  //NOTE:INST_QUEUE_SIZE的大小最小为8(且必须为2的倍数)
  //NOTE:ISSUE_QUEUE_SIZE的大小最小为4(且必须为2的倍数)
}

object CacheConfig{
  def LINE_WIDTH=256
  def LINE_WORD_NUM=(LINE_WIDTH/32)

  def WAY_NUM_I=8
  def WAY_NUM_D=4
  def TAG_WIDTH=20
  def INDEX_WIDTH=12-OFFSET_WIDTH
  def OFFSET_WIDTH=log2Ceil(LINE_WORD_NUM) + 2
  def USE_LRU=false
}


object AxiBridgeConfig{
  def WR_QUEUE_SIZE=2
}

object GenCtrl { 
  def FROCE_ICACHE=false
  def FROCE_DCACHE=false
  //NOTE:完成mmu后，跑lab9及以前的测试可能会导致不开cache跑的状态,需要开启强制cache
  def PERF_CNT=false
  //NOTE:控制性能计数器 true为开启
  def USE_TLB=true
  //NOTE:控制是否生成带有tlb的代码
  def FAST_SIM=true
  //NOTE:乘除法器使用“*” “/”加速仿真速度
  def USE_DIFF=true
  //NOTE:控制是否生成DIFFTEST电路
  def USE_PERFCNT=false
  //NOTE:控制是否性能计数器
}

object BtbParams {
  def FetchWidth = 2
  def BrTypeWidth = 2
  def BTB_Entrys = 256
  def BTB_Ways  = 2
  def BTB_Sets = BTB_Entrys/BTB_Ways //128
  def lower = 2
}
