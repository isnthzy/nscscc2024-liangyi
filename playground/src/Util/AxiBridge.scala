import chisel3._
import chisel3.util._  
import config.CacheConfig._
import config.Configs._
import config.AxiBridgeConfig._

class AxiBridge extends Module {
  val io = IO(new Bundle {
    val arid = Output(UInt(4.W))
    val araddr = Output(UInt(ADDR_WIDTH.W))
    val arlen = Output(UInt(8.W))
    val arsize = Output(UInt(3.W))
    val arburst = Output(UInt(2.W))
    val arlock = Output(UInt(2.W))
    val arcache = Output(UInt(4.W))
    val arprot = Output(UInt(3.W))
    val arvalid = Output(Bool())    
    val arready = Input(Bool())

    val rid = Input(UInt(4.W))
    val rdata = Input(UInt(DATA_WIDTH.W))
    val rresp = Input(UInt(2.W))
    val rlast = Input(Bool())
    val rvalid = Input(Bool())
    val rready = Output(Bool()) 

    val awid = Output(UInt(4.W))
    val awaddr = Output(UInt(ADDR_WIDTH.W))
    val awlen = Output(UInt(8.W))
    val awsize = Output(UInt(3.W))
    val awburst = Output(UInt(2.W))
    val awlock = Output(UInt(2.W))
    val awcache = Output(UInt(4.W))
    val awprot = Output(UInt(3.W))
    val awvalid = Output(Bool())
    val awready = Input(Bool())

    val wid = Output(UInt(4.W))
    val wdata = Output(UInt(DATA_WIDTH.W))
    val wstrb = Output(UInt(4.W))
    val wlast = Output(Bool())
    val wvalid = Output(Bool())
    val wready = Input(Bool())

    val bid = Input(UInt(4.W))
    val bresp = Input(UInt(2.W))
    val bvalid = Input(Bool())
    val bready = Output(Bool())

    // Cache signals
    val inst=new cache_bridge_axi_bundle()
    val data=new cache_bridge_axi_bundle()
    val write_buffer_empty = Output(Bool())
  })
  io.arburst:= 1.U
  io.arlock := 0.U
  io.arcache:= 0.U
  io.arprot := 0.U
  io.awid   := 1.U
  io.awburst:= 1.U
  io.awlock := 0.U
  io.awcache:= 0.U
  io.awprot := 0.U
  io.wid    := 1.U
  io.inst.wr_rdy:=1.U
  val rd_req_idle :: rd_req_rdy :: Nil = Enum(2)
  val rd_resp_idle :: rd_resp_transfer :: Nil = Enum(2)
  val wr_idle :: wr_transfer :: wr_wait_b :: Nil =Enum(3)

  val write_wait_enable=Wire(Bool())
  val write_queue_last=Wire(Bool())
  val data_rd_cache_line=io.data.rd_type==="b100".U(3.W)
  val data_real_rd_size=Mux(data_rd_cache_line,"b10".U(3.W),io.data.rd_type) 
  val data_real_rd_len =Mux(data_rd_cache_line,LINE_WORD_NUM.U-1.U,0.U(8.W)) 

  val data_wr_cache_line=io.data.wr_type==="b100".U(3.W)
  val data_real_wr_size=Mux(data_wr_cache_line,"b10".U(3.W),io.data.wr_type) 
  val data_real_wr_len =Mux(data_wr_cache_line,LINE_WORD_NUM.U-1.U,0.U(8.W)) 

  val inst_rd_cache_line=io.inst.rd_type==="b100".U(3.W)
  val inst_real_rd_size=Mux(inst_rd_cache_line,"b10".U(3.W),io.inst.rd_type) 
  val inst_real_rd_len =Mux(inst_rd_cache_line,LINE_WORD_NUM.U-1.U,0.U(8.W)) 

  val aridReg   =RegInit(0.U(4.W))
  val araddrReg =RegInit(0.U(32.W))
  val arlenReg  =RegInit(0.U(8.W))
  val arsizeReg =RegInit(0.U(3.W))
  val arvalidReg=RegInit(false.B)
  io.arid  :=aridReg
  io.araddr:=araddrReg
  io.arlen :=arlenReg
  io.arsize:=arsizeReg
  io.arvalid:=arvalidReg
  val read_requst_state=RegInit(rd_req_idle)
  switch(read_requst_state){
    is(rd_req_idle){
      when(io.data.rd_req){
        when(write_wait_enable){
          when(io.bvalid&&io.bready&&write_queue_last){
            read_requst_state:=rd_req_rdy
            aridReg:=1.U
            araddrReg:=io.data.rd_addr
            arsizeReg:=data_real_rd_size
            arlenReg :=data_real_rd_len
            arvalidReg:=true.B
          }
        }.otherwise{
          read_requst_state:=rd_req_rdy
          aridReg:=1.U
          araddrReg:=io.data.rd_addr
          arsizeReg:=data_real_rd_size
          arlenReg :=data_real_rd_len
          arvalidReg:=true.B
        }
      }.elsewhen(io.inst.rd_req){
        when(write_wait_enable){
          when(io.bvalid&&io.bready&&write_queue_last){
            read_requst_state:=rd_req_rdy
            aridReg:=0.U
            araddrReg:=io.inst.rd_addr
            arsizeReg:=inst_real_rd_size
            arlenReg :=inst_real_rd_len
            arvalidReg:=true.B
          }
        }.otherwise{
            read_requst_state:=rd_req_rdy
            aridReg:=0.U
            araddrReg:=io.inst.rd_addr
            arsizeReg:=inst_real_rd_size
            arlenReg :=inst_real_rd_len
            arvalidReg:=true.B
        }
      }
    }
    is(rd_req_rdy){
      when(io.arready){
        read_requst_state:=rd_req_idle
        arvalidReg:=false.B
      }
    }
  }


  val rreadyReg=RegInit(true.B)
  io.rready:=rreadyReg
  val read_respond_state=RegInit(rd_resp_idle)
  when(read_respond_state===rd_resp_idle){
    when(io.rvalid&&io.rready){
      read_respond_state:=rd_resp_transfer
    }
  }.elsewhen(read_respond_state===rd_resp_transfer){
    when(io.rlast&&io.rvalid){
      read_respond_state:=rd_req_idle
    }
  }

  val write_state=RegInit(wr_idle)
  val wr_queue_bits=RegInit(VecInit(Seq.fill(WR_QUEUE_SIZE)(0.U.asTypeOf(new write_queue_bundle()))))
  val wr_queue_head=RegInit(0.U(log2Ceil(WR_QUEUE_SIZE).W))
  val wr_queue_tail=RegInit(0.U(log2Ceil(WR_QUEUE_SIZE).W)) 
  val wr_queue_cacheline_cnt=RegInit(0.U(log2Ceil(LINE_WORD_NUM).W))
  val wr_queue_full =(wr_queue_head+1.U)===wr_queue_tail
  val wr_queue_empty=wr_queue_head===wr_queue_tail
  val wr_queue_last =wr_queue_head===(wr_queue_tail+1.U)
  write_wait_enable:= ~wr_queue_empty
  write_queue_last :=  wr_queue_last
  when(~wr_queue_full&&io.data.wr_req){
    wr_queue_bits(wr_queue_head).addr:=io.data.wr_addr
    wr_queue_bits(wr_queue_head).len :=data_real_wr_len
    wr_queue_bits(wr_queue_head).size:=data_real_wr_size
    wr_queue_bits(wr_queue_head).strb:=io.data.wr_wstrb
    wr_queue_bits(wr_queue_head).cacheline:=io.data.wr_data
    wr_queue_head:=wr_queue_head+1.U
  }
  io.awaddr:=wr_queue_bits(wr_queue_tail).addr
  io.awsize:=wr_queue_bits(wr_queue_tail).size
  io.awlen :=wr_queue_bits(wr_queue_tail).len
  io.wdata:=wr_queue_bits(wr_queue_tail).cacheline(31,0)
  io.wstrb:=wr_queue_bits(wr_queue_tail).strb
  
  io.awvalid:= ~wr_queue_empty&&(write_state===wr_idle)
  io.wlast  := (write_state===wr_transfer)&&(wr_queue_cacheline_cnt===0.U)
  val wvalidReg=RegInit(false.B)
  val breadyReg=RegInit(false.B)
  io.wvalid:=wvalidReg
  io.bready:=breadyReg

  switch(write_state){
    is(wr_idle){
      when(io.awready&& ~wr_queue_empty){
        write_state:=wr_transfer
        wvalidReg:=true.B
      }
      when(io.awlen===(LINE_WORD_NUM.U-1.U)){
        wr_queue_cacheline_cnt:=(LINE_WORD_NUM.U-1.U)
      }.otherwise{
        wr_queue_cacheline_cnt:=0.U //一个WORD
      }
    }
    is(wr_transfer){
      when(io.wready){
        when(io.wlast){
          write_state:=wr_wait_b
          breadyReg:=true.B
          wvalidReg:=false.B
        }.otherwise{
          wr_queue_cacheline_cnt:=wr_queue_cacheline_cnt-1.U
          wr_queue_bits(wr_queue_tail).cacheline:={
            Cat(0.U(32.W),wr_queue_bits(wr_queue_tail).cacheline(LINE_WIDTH-1,32))}
        }
      }
    }
    is(wr_wait_b){
      when(io.bvalid&&io.bready){
        write_state:=wr_idle
        breadyReg:=false.B
        wr_queue_tail:=wr_queue_tail+1.U
      }
    }
  }
  io.write_buffer_empty:=wr_queue_cacheline_cnt===0.U && ~write_wait_enable
  val rd_requst_can_receive=(read_requst_state===rd_req_idle)&&(~write_wait_enable||(io.bvalid&&io.bready&&write_queue_last))
  io.inst.rd_rdy:=rd_requst_can_receive&& ~io.data.rd_req
  io.inst.ret_valid:=io.rvalid& ~io.rid(0)
  io.inst.ret_last:=io.rlast  & ~io.rid(0)
  io.inst.ret_data:=io.rdata

  io.data.rd_rdy  := rd_requst_can_receive
  io.data.ret_valid:=io.rvalid& io.rid(0)
  io.data.ret_last:= io.rlast & io.rid(0)
  io.data.ret_data:= io.rdata
  io.data.wr_rdy  := ~wr_queue_full
}

class write_queue_bundle extends Bundle{
  val addr=UInt(ADDR_WIDTH.W)
  val len =UInt(8.W)
  val size=UInt(3.W)
  val strb=UInt(4.W)
  val cacheline=UInt(LINE_WIDTH.W)
}

class cache_bridge_axi_bundle extends Bundle{
  val rd_req = Input(Bool())
  val rd_type = Input(UInt(3.W))
  val rd_addr = Input(UInt(32.W))
  val rd_rdy = Output(Bool())
  val ret_valid = Output(Bool())
  val ret_last = Output(Bool())
  val ret_data = Output(UInt(32.W))
  val wr_req = Input(Bool())
  val wr_type = Input(UInt(3.W))
  val wr_addr = Input(UInt(32.W))
  val wr_wstrb = Input(UInt(4.W))
  val wr_data = Input(UInt(LINE_WIDTH.W))
  val wr_rdy = Output(Bool())
}