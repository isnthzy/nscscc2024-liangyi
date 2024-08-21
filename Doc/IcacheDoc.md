# IcacheDoc

设计形式，阻塞式（非阻塞cache顺序核似乎没什么用）

端口：

```verilog
module icache
(
    input               clk            ,
    input               reset          ,
    //to from cpu
    input               valid          ,
    input               op             ,
    input  [ 7:0]       index          ,
    input  [19:0]       tag            ,
    input  [ 3:0]       offset         ,
    input  [ 3:0]       wstrb          ,
    input  [31:0]       wdata          ,
    output              addr_ok        ,
    output              data_ok        ,
    output [31:0]       rdata_l        ,
    output [31:0]       rdata_h        ,
    input               uncache_en     ,
    input               icacop_op_en   ,
    input  [ 1:0]       cacop_op_mode  ,  
    input  [ 7:0]       cacop_op_addr_index , //this signal from mem stage's va
    input  [19:0]       cacop_op_addr_tag   , 
    input  [ 3:0]       cacop_op_addr_offset,
    output              icache_unbusy,
    input               tlb_excp_cancel_req,
    //to from axi
    output              rd_req       ,
    output [ 2:0]       rd_type      ,
    output [31:0]       rd_addr      ,
    input               rd_rdy       ,
    input               ret_valid    ,
    input               ret_last     ,
    input  [31:0]       ret_data     ,
    output reg          wr_req       ,
    output [ 2:0]       wr_type      ,
    output [31:0]       wr_addr      ,
    output [ 3:0]       wr_wstrb     ,
    output [127:0]      wr_data      ,
    input               wr_rdy       ,
    //to perf_counter
    output              cache_miss  
); 
```

形式：阻塞式cache

要求：
tag要经过mmu_tlb转换，延后一拍给出

uncache要通过mmu判断，延后一拍给出

uncache条件下，rdata_h返回0