import chisel3._
import chisel3.util._

class MyICache(val SIZE: Int, val LINE_SIZE: Int, val WAY_NUM: Int, val UseLRU: Boolean)
    extends Module {
  val INDEX_WIDTH   = log2Ceil(SIZE)
  val OFFSET_WIDTH  = log2Ceil(LINE_SIZE)
  val WAY_NUM_WIDTH = log2Ceil(WAY_NUM)
  val LINE_BIT_SIZE = LINE_SIZE * 8
  val LINE_WORD_NUM = LINE_SIZE / 4
  println("Inst Cache Size: " + SIZE)
  println("Inst Cache Line Size   : " + LINE_WORD_NUM + " Word | " + LINE_SIZE + " Byte | " + LINE_BIT_SIZE + " Bit")
  println("Inst Cache Way Number  : " + WAY_NUM)
  assert(
    (INDEX_WIDTH + OFFSET_WIDTH <= 12).asBool,
    "Index windth + offset width should be less than or equal 12, but given is %d\n",
    (INDEX_WIDTH + OFFSET_WIDTH).asUInt
  )

  val io = IO(new Bundle {

    // cpu
    val valid     = Input(Bool())
    val op        = Input(Bool())
    val tag       = Input(UInt(20.W))
    val index     = Input(UInt(INDEX_WIDTH.W))
    val offset    = Input(UInt(OFFSET_WIDTH.W))
    // val size      = Input(UInt(2.W))
    // val wstrb     = Input(UInt(4.W))
    // val wdata     = Input(UInt(32.W))
    val addr_ok   = Output(Bool())
    val data_ok   = Output(Bool())
    val rdata_l   = Output(UInt(32.W))
    val rdata_h   = Output(UInt(32.W))
    val uncached  = Input(Bool())

    val cacop_en         = Input(Bool())
    val cacop_op         = Input(UInt(2.W))
    val icache_unbussy        = Output(Bool())
    val tlb_excp_cancel_req   = Input(Bool())

    // axi
    val rd_req      = Output(Bool())
    val rd_type     = Output(UInt(3.W))
    val rd_addr     = Output(UInt(32.W))
    val rd_rdy      = Input(Bool())
    val ret_valid   = Input(Bool())
    val ret_last    = Input(Bool())
    val ret_data    = Input(UInt(32.W))
    val wr_req      = Output(Bool())
    val wr_type     = Output(UInt(3.W))
    val wr_addr     = Output(UInt(32.W))
    val wr_wstrb    = Output(UInt(4.W))
    val wr_data     = Output(UInt(LINE_BIT_SIZE.W))
    val wr_rdy      = Input(Bool())

    // // to perf_counter
    // val cache_miss       = Output(Bool())

  })
  
 
  // val lru = Array.fill(SIZE)(Module(new TreeLRU(WAY_NUM)).io)
  // val lruWays = Wire(Vec(SIZE, UInt(log2Ceil(WAY_NUM).W)))
  val total_req_counter   = RegInit(0.U(32.W))
  val total_miss_counter  = RegInit(0.U(32.W))
  val dcache_counter      = RegInit(0.U(32.W))
  dcache_counter := dcache_counter + 1.U
  
  
  // val Dirty = RegInit(VecInit.fill(SIZE, WAY_NUM)(false.B))
  

  val DataBank                = Array.fill(WAY_NUM)(Module(new DataRAM(SIZE, LINE_BIT_SIZE)).io)
  val TagvBank                = Array.fill(WAY_NUM)(Module(new TagvRAM(SIZE, 20)).io)
  val Valid                   = RegInit(VecInit(Seq.fill(SIZE)(0.U(WAY_NUM.W))))

  val way_hit                 = Wire(Vec(WAY_NUM, Bool()))
  // val way_hit_buffer          = RegInit(0.U(WAY_NUM.W))
  // val way_hit_info            = Wire(UInt(WAY_NUM.W))
  val cache_hit               = Wire(Bool())
  val cache_hit_way           = Wire(UInt(WAY_NUM_WIDTH.W))

  val replaced_way_buffer     = RegInit(0.U(WAY_NUM_WIDTH.W))
  // val replaced_index_buffer   = RegInit(0.U(INDEX_WIDTH.W))
  val replaced_way            = Wire(UInt(WAY_NUM_WIDTH.W))
  // val replaced_data           = Wire(UInt(LINE_BIT_SIZE.W))
  // val replaced_tag            = Wire(UInt(20.W))
  // val replaced_index          = Wire(UInt(INDEX_WIDTH.W))
  // val replaced_addr           = Wire(UInt(32.W))
  // val replaced_dirty          = Wire(Bool())
  // val replaced_valid          = Wire(Bool())

  val need_write_axi      = RegInit(false.B)
  val need_read_axi       = RegInit(false.B)

  val read_data           = Wire(Vec(WAY_NUM, UInt(LINE_BIT_SIZE.W)))
  // val read_data_buffer    = RegInit(VecInit.fill(WAY_NUM)(0.U(LINE_BIT_SIZE.W)))
  val target_data         = Wire(Vec(LINE_WORD_NUM, UInt(32.W)))
  val read_tagv           = Wire(Vec(WAY_NUM, UInt(21.W)))
  val read_tag            = Wire(Vec(WAY_NUM, UInt(20.W)))
  val read_valid          = Wire(Vec(WAY_NUM, Bool()))
  // val read_tag_buffer     = RegInit(Vec(WAY_NUM, UInt(20.W)))
  // val read_valid_buffer   = RegInit(Vec(WAY_NUM, Bool()))

  val receive_request     = Wire(Bool())
  val uncached_write      = Wire(Bool())
  val uncached_read       = Wire(Bool())
  val uncached_en         = Wire(Bool())
  val read_hit            = Wire(Bool())
  val write_hit           = Wire(Bool())
  val cache_unbussy       = Wire(Bool())

  

  object CacheState extends ChiselEnum {
    val IDLE, LOOKUP, WRITEHIT, MISS, REPLACE, REFILL, RESPOND = Value
    val correct_annotation_map = Map[String, UInt](
      "IDLE" -> 0.U,
      "LOOKUP" -> 1.U,
      "WRITEHIT" -> 2.U,
      "MISS" -> 3.U,
      "REPLACE" -> 4.U,
      "REFILL" -> 5.U,
      "RESPOND" -> 6.U
    )
  }
  val main_state  = RegInit(CacheState.IDLE)

  val is_idle     = WireInit(main_state === CacheState.IDLE)
  val is_lookup   = WireInit(main_state === CacheState.LOOKUP)
  val is_writehit = WireInit(main_state === CacheState.WRITEHIT)
  val is_miss     = WireInit(main_state === CacheState.MISS)
  val is_replace  = WireInit(main_state === CacheState.REPLACE)
  val is_refill   = WireInit(main_state === CacheState.REFILL)
  val is_respond  = WireInit(main_state === CacheState.RESPOND)
  dontTouch(is_refill)
  val rd_type_buffer    = RegInit(0.U(3.W))
  val rd_addr_buffer    = RegInit(0.U(32.W))

  val wr_type_buffer    = RegInit(0.U(3.W))
  val wr_addr_buffer    = RegInit(0.U(32.W))
  val wr_wstrb_buffer   = RegInit(0.U(4.W))
  val wr_data_buffer    = RegInit(0.U(LINE_BIT_SIZE.W))

  val cache_hit_buffer          = RegInit(false.B)
  val request_cacop_en_buffer   = RegInit(false.B)
  val request_cacop_op_buffer   = RegInit(0.U(2.W))
  val request_op_buffer         = RegInit(false.B)
  val request_tag_buffer        = RegInit(0.U(20.W))
  val request_index_buffer      = RegInit(0.U(INDEX_WIDTH.W))
  val request_offset_buffer     = RegInit(0.U(OFFSET_WIDTH.W))
  val request_uncached_buffer   = RegInit(false.B)
  val target_word_id            = RegInit(0.U((OFFSET_WIDTH-2).W))
  // val request_wdata_buffer      = Reg(UInt(32.W))
  // val request_wstrb_buffer      = Reg(UInt(4.W))
  // val request_size_buffer       = Reg(UInt(2.W))
  val request_addr              = Cat(Mux(is_lookup, io.tag, request_tag_buffer), request_index_buffer, request_offset_buffer)
  val request_line_addr         = Cat(Mux(is_lookup, io.tag, request_tag_buffer), request_index_buffer, 0.U(OFFSET_WIDTH.W))
  val normal_request            = Wire(Bool())

  val ret_data_buffer   = RegInit(VecInit.fill(LINE_WORD_NUM)(0.U(32.W)))
  val miss_data_ret_cnt = RegInit(0.U((OFFSET_WIDTH - 2).W))


  val cacop_op_0  = request_cacop_en_buffer && request_cacop_op_buffer === 0.U
  val cacop_op_1  = request_cacop_en_buffer && request_cacop_op_buffer === 1.U
  val cacop_op_2  = request_cacop_en_buffer && request_cacop_op_buffer === 2.U
  val cacop_way   = request_addr(WAY_NUM_WIDTH-1, 0)
  

  // need_read_axi       := uncached_read || normal_request
  // need_write_axi      := (cacop_op_2 && cache_hit_buffer || cacop_op_1 || normal_request) && replaced_dirty && replaced_valid ||
  //                      uncached_write
  replaced_way    := Mux(cacop_op_2 && cache_hit, cache_hit_way, 
                        Mux(cacop_op_0 || cacop_op_1, cacop_way, dcache_counter(WAY_NUM_WIDTH-1, 0)))                     
  // replaced_data   := read_data(replaced_way)
  // replaced_dirty  := Dirty(replaced_index)(replaced_way)
  // replaced_index  := request_index_buffer
  // replaced_tag    := read_tag(replaced_way)
  // replaced_valid  := read_valid(replaced_way)
  // replaced_addr   := Cat(replaced_tag, replaced_index, 0.U(OFFSET_WIDTH.W))

  for (i <- 0 until WAY_NUM) {
    read_tag(i)   := read_tagv(i)(20, 1)
    read_valid(i) := read_tagv(i)(0)
  }
  for (i <- 0 until WAY_NUM) {
    way_hit(i) := (read_tag(i) === io.tag) && read_valid(i)
    // way_hit(i) := (read_tag(i) === request_tag_buffer) && read_valid(i)
  }

  // total_data_count := Mux(uncached_en, 0.U, target_word_id)
  // ret_data_finish  := miss_data_ret_cnt === total_data_count

  target_data     := read_data(cache_hit_way).asTypeOf(target_data)
  cache_hit       := way_hit.reduce(_||_) && !io.tlb_excp_cancel_req
  // way_hit_info    := Mux(is_lookup, way_hit.asUInt, way_hit_buffer)
  cache_hit_way   := OHToUInt(way_hit)
  uncached_en     := Mux(is_lookup, io.uncached, request_uncached_buffer) && !request_cacop_en_buffer
  uncached_read   := uncached_en && !request_op_buffer
  uncached_write  := uncached_en && request_op_buffer
  normal_request  := !Mux(is_lookup, io.uncached, request_uncached_buffer) && !request_cacop_en_buffer
  read_hit        := cache_hit && !request_op_buffer && normal_request
  write_hit       := cache_hit && request_op_buffer && normal_request
  cache_unbussy   := is_idle || is_lookup && cache_hit && normal_request
  receive_request := cache_unbussy && (io.valid || io.cacop_en)
  io.icache_unbussy:= cache_unbussy
  io.addr_ok      := cache_unbussy
  io.data_ok      := is_lookup && (cache_hit || request_op_buffer) && !io.uncached || is_respond
  io.rdata_l      := Mux(is_respond, ret_data_buffer(target_word_id), target_data(target_word_id))
  io.rdata_h      := Mux(is_respond, ret_data_buffer(target_word_id+1.U), target_data(target_word_id+1.U))

  io.rd_req       := is_miss && need_read_axi 
  io.rd_type      := rd_type_buffer
  io.rd_addr      := rd_addr_buffer

  io.wr_req       := false.B
  io.wr_type      := 0.U
  io.wr_wstrb     := 0.U
  io.wr_addr      := 0.U
  io.wr_data      := 0.U


  val conflcit      = Wire(Vec(WAY_NUM, Bool()))
  val conflcit_flag = RegInit(VecInit.fill(WAY_NUM)(false.B))
  val conflcit_data = RegInit(VecInit.fill(WAY_NUM)(0.U(LINE_BIT_SIZE.W)))

  for (i <- 0 until WAY_NUM) {
    
    read_data(i)        := Mux(conflcit_flag(i), conflcit_data(i), DataBank(i).doutb)
    DataBank(i).clka    := clock
    DataBank(i).clkb    := clock
    DataBank(i).addra   := request_index_buffer
    DataBank(i).addrb   := Mux(conflcit(i), io.index + 1.U, io.index)
    DataBank(i).dina    := ret_data_buffer.asUInt
    DataBank(i).wea     := is_respond && normal_request && (i.asUInt === replaced_way_buffer)  
    conflcit(i)         := request_index_buffer === io.index && DataBank(i).wea
    when(conflcit(i))
    {
      conflcit_flag(i) := true.B;
      conflcit_data(i) := DataBank(i).dina;
    }
    .otherwise
    {
      conflcit_flag(i) := false.B;
      conflcit_data(i) := 0.U(LINE_BIT_SIZE.W);
    }                  
  }
  val valid_in = Wire(Vec(WAY_NUM, UInt(1.W)))
  for (i <- 0 until WAY_NUM) {
    read_tagv(i)        := Cat(TagvBank(i).douta, Valid(request_index_buffer)(i))
    TagvBank(i).clka    := clock
    TagvBank(i).addra   := Mux(receive_request, io.index, request_index_buffer)
    TagvBank(i).dina    := Mux(request_cacop_en_buffer, 0.U(20.W), request_tag_buffer)
    TagvBank(i).wea     := is_respond && !uncached_en && (i.asUInt === replaced_way_buffer)
    valid_in(i)         := Mux(TagvBank(i).wea, !request_cacop_en_buffer, Valid(request_index_buffer)(i))
  }
  Valid(request_index_buffer) := valid_in.asUInt
  // target_data := read_data(target_word_id).asTypeOf(target_data)
  // read_tagv := read_tagv
    // for(i <-0 until SIZE)
    // {
    //   lru(i).access := Mux(is_lookup, cache_hit_way, replaced_way)
    //   lru(i).update := (is_lookup && cache_hit || is_refill) && i.asUInt === request_index_buffer 
    //   lruWays(i.asUInt) := lru(i).lruWay 
    // }
    

  when(is_idle) { // Idle, receive request
    when(receive_request) 
    {

      request_cacop_en_buffer := io.cacop_en
      request_cacop_op_buffer := io.cacop_op
      request_op_buffer       := io.op
      request_index_buffer    := io.index
      request_offset_buffer   := io.offset
      target_word_id          := io.offset(OFFSET_WIDTH-1, 2)
      // request_size_buffer     := io.size
      // request_wstrb_buffer    := io.wstrb
      // request_wdata_buffer    := io.wdata

      main_state := CacheState.LOOKUP
    }
  }
  .elsewhen(is_lookup) 
  {

    total_req_counter := total_req_counter + 1.U

    request_uncached_buffer   := io.uncached
    request_tag_buffer        := io.tag

    cache_hit_buffer          := cache_hit

    when(io.tlb_excp_cancel_req) 
    {
      main_state := CacheState.IDLE
    }
    .elsewhen(!cache_hit || request_cacop_en_buffer || uncached_en)
    { 
      when(uncached_en) { target_word_id := 0.U }
      replaced_way_buffer   := replaced_way
      // replaced_index_buffer := replaced_index

      need_read_axi       := uncached_read || normal_request
      // rd_req_buffer       := is_replace && need_read_axi 
      rd_type_buffer      := Mux(uncached_en, "b010".U, "b100".U)
      rd_addr_buffer      := Mux(uncached_en, request_addr, request_line_addr)
      main_state := Mux(cacop_op_2 && !cache_hit, CacheState.IDLE, CacheState.MISS) 
      total_miss_counter := total_miss_counter + 1.U
    }
    .elsewhen(receive_request) {
      // Let addr_ok judge if to receive new request
      // Only read hit can receive new request
      request_cacop_en_buffer := io.cacop_en
      request_cacop_op_buffer := io.cacop_op
      request_op_buffer       := io.op
      request_index_buffer    := io.index
      request_offset_buffer   := io.offset
      target_word_id          := io.offset(OFFSET_WIDTH-1, 2)
      // request_size_buffer     := io.size
      // request_wstrb_buffer    := io.wstrb
      // request_wdata_buffer    := io.wdata
      
      main_state := CacheState.LOOKUP

    }
    .otherwise { main_state := CacheState.IDLE }

  }
  .elsewhen(is_miss) 
  { 
    when(need_read_axi)
    {
      when(io.rd_rdy)
      { 
        main_state := CacheState.REFILL
        miss_data_ret_cnt := 0.U
        ret_data_buffer   := (0.U).asTypeOf(ret_data_buffer)
      }
    }
    .otherwise { main_state := CacheState.IDLE }

  }
  .elsewhen(is_refill) 
  {
    when(io.ret_valid) 
    {
      miss_data_ret_cnt := miss_data_ret_cnt + 1.U
      ret_data_buffer(miss_data_ret_cnt) := io.ret_data
      
      when(io.ret_last) { main_state := CacheState.RESPOND }

    }
  }
  .elsewhen(is_respond) { main_state := CacheState.IDLE }
  // when(dcache_counter(15,0).asUInt===0.U)
  // {
  //   printf(cf"[DATA] cache miss rate: $total_miss_counter%d $total_req_counter%d\n")
  // }
}
