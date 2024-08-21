import chisel3._
import chisel3.util._
import config.Configs._
import chisel3.util.experimental.decode._


object Instructions {
  //Loads
  def LD_W     = BitPat("b0010 100010 ???????????? ????? ?????")
  def LD_H     = BitPat("b0010 100001 ???????????? ????? ?????")
  def LD_HU    = BitPat("b0010 101001 ???????????? ????? ?????")
  def LD_B     = BitPat("b0010 100000 ???????????? ????? ?????")
  def LD_BU    = BitPat("b0010 101000 ???????????? ????? ?????")

  //Stores
  def ST_W     = BitPat("b0010 100110 ???????????? ????? ?????")
  def ST_H     = BitPat("b0010 100101 ???????????? ????? ?????")
  def ST_B     = BitPat("b0010 100100 ???????????? ????? ?????")
  
  //Alus
  //intop
  def ADD_W    = BitPat("b000000000001 00000 ????? ????? ?????")
  def SUB_W    = BitPat("b000000000001 00010 ????? ????? ?????")
  def AND      = BitPat("b000000000001 01001 ????? ????? ?????")
  def OR       = BitPat("b000000000001 01010 ????? ????? ?????")
  def XOR      = BitPat("b000000000001 01011 ????? ????? ?????")
  def NOR      = BitPat("b000000000001 01000 ????? ????? ?????")
  def SLT      = BitPat("b000000000001 00100 ????? ????? ?????")
  def SLTU     = BitPat("b000000000001 00101 ????? ????? ?????")
  def SLL_W    = BitPat("b000000000001 01110 ????? ????? ?????") 
  def SRL_W    = BitPat("b000000000001 01111 ????? ????? ?????")
  def SRA_W    = BitPat("b000000000001 10000 ????? ????? ?????")

  def MUL_W    = BitPat("b000000000001 11000 ????? ????? ?????")
  def MULH_W   = BitPat("b000000000001 11001 ????? ????? ?????")
  def MULH_WU  = BitPat("b000000000001 11010 ????? ????? ?????")
  def DIV_W    = BitPat("b000000000010 00000 ????? ????? ?????")
  def MOD_W    = BitPat("b000000000010 00001 ????? ????? ?????")
  def DIV_WU   = BitPat("b000000000010 00010 ????? ????? ?????")
  def MOD_WU   = BitPat("b000000000010 00011 ????? ????? ?????")

  //int_i12
  def ADDI_W   = BitPat("b0000001 010 ???????????? ????? ?????")
  def SLTI     = BitPat("b0000001 000 ???????????? ????? ?????")
  def ANDI     = BitPat("b0000001 101 ???????????? ????? ?????")
  def ORI      = BitPat("b0000001 110 ???????????? ????? ?????")
  def XORI     = BitPat("b0000001 111 ???????????? ????? ?????")
  def SLTUI    = BitPat("b0000001 001 ???????????? ????? ?????")

  //int_i8
  def SLLI_W   = BitPat("b0000000001 0000001 ????? ????? ?????")
  def SRLI_W   = BitPat("b0000000001 0001001 ????? ????? ?????")
  def SRAI_W   = BitPat("b0000000001 0010001 ????? ????? ?????")

  //int_i20
  def LU12I_W  = BitPat("b00 01010 ??????????????? ????? ?????")
  def PCADDU12I= BitPat("b00 01110 ??????????????? ????? ?????")

  //Branches
  def BGEU     = BitPat("b01 1011 ???????????????? ????? ?????")
  def BNE      = BitPat("b01 0111 ???????????????? ????? ?????")
  def BGE      = BitPat("b01 1001 ???????????????? ????? ?????")
  def BLT      = BitPat("b01 1000 ???????????????? ????? ?????")
  def BEQ      = BitPat("b01 0110 ???????????????? ????? ?????")
  def BLTU     = BitPat("b01 1010 ???????????????? ????? ?????")

  //Jumps
  def B        = BitPat("b01 0100 ???????????????? ????? ?????")
  def BL       = BitPat("b01 0101 ???????????????? ????? ?????")
  def JIRL     = BitPat("b01 0011 ???????????????? ????? ?????")

  //csr
  def CSRRD    = BitPat("b00 000100 ?????????????? 00000 ?????")
  def CSRWR    = BitPat("b00 000100 ?????????????? 00001 ?????")
  //NOTE:fuck_csrxchg not in here!!!!!!
  def CNTVLID_W= BitPat("b000000000 00000000 11000 ????? ?????") 
  def RDCNTVH_W= BitPat("b000000000 00000000 11001 00000 ?????")
  //NOTE:fuck RDCNTVL, RDCNTID,这是两条指令，不在decoder里判断，放在ih级判断
  //
  def TLBSRCH  = BitPat("b000001100 10010000 01010 00000 00000")
  def TLBRD    = BitPat("b000001100 10010000 01011 00000 00000")
  def TLBWR    = BitPat("b000001100 10010000 01100 00000 00000")
  def TLBFILL  = BitPat("b000001100 10010000 01101 00000 00000")
  def INVTLB   = BitPat("b000001100 10010011 ????? ????? ?????")
  //NOTE:即使是gen没有tlb的电路，依然保留tlb译码，只是作为空指令
  //
  def LL_W     = BitPat("b00100000 ????????? ????? ????? ?????")
  def SC_W     = BitPat("b00100001 ????????? ????? ????? ?????")
  //
  def ERTN     = BitPat("b00000110010 010000 01110 00000 00000")
  def BREAK    = BitPat("b00000000001 010100 ????? ????? ?????")
  def SYSCALL  = BitPat("b00000000001 010110 ????? ????? ?????")
  def CACOP    = BitPat("b0000011000? ?????? ????? ????? ?????")
  def IDLE     = BitPat("b000001100 10010001 ????? ????? ?????")
  //
  def IBAR     = BitPat("b001110000 11100100 ????? ????? ?????")
  def DBAR     = BitPat("b001110000 11100101 ????? ????? ?????")
}


object Control{
  // Imm_sel和Imm Extend的融合
  //不需要Imm的数，第一位（决定拓展是有符号还是无符号）
  //S Sign拓展为有符号拓展
  //Z Zero 0拓展
  //B Branch Sign 分支符号拓展
  val TYPEXX = "0000"
  val TYPE2R = "0000"
  val TYPE3R = "0001"
  val SIMM12 = "0010"
  val ZIMM12 = "0011"
  val SIMM14 = "0100"
  val BIMM16 = "0101"
  val LIMM20 = "0110"
  val SIMM20 = "0111"
  val BIMM26 = "1000"

  //OP_TYPE
  val OP_XXXX= "0000"
  val OP_ALU = "0001"
  val OP_LSU = "0010"
  val OP_MUL = "0011"
  val OP_DIV = "0100"
  val OP_CSR = "0101"
  val OP_BRU = "0110"
  val OP_JUMP= "0111"
  val OP_CNT = "1000"
  val OP_TLB = "1001"
  val OP_ATOM= "1010"
  val OP_IBAR= "1011"
  val OP_DBAR= "1100"
  val OP_IDLE= "1101"
  val OP_CACP= "1110"

   
  // ld_type
  val LD_XXX = "0000001"
  val LD_LW  = "0000010"
  val LD_LH  = "0000100"
  val LD_LB  = "0001000"
  val LD_LHU = "0010000"
  val LD_LBU = "0100000"
  val LD_LLW = "1000000"

  // st_type
  val ST_XX  = "00001"
  val ST_SB  = "00010"
  val ST_SH  = "00100"
  val ST_SW  = "01000"
  val ST_SCW = "10000"

  // A_sel
  val A_XXX = "0"
  val A_PC  = "0"
  val A_RJ  = "1"

  // B_sel
  val B_XXX = "00"
  val B_IMM = "00"
  val B_RK  = "01"
  val B_RD  = "10"
  val B_CSR = "11"


  // Alu_sel
  val ALU_XXX = "000000000000000"
  val ALU_ADD = "000000000000001"
  val ALU_SUB = "000000000000010"
  val ALU_AND = "000000000000100"
  val ALU_OR  = "000000000001000"
  val ALU_XOR = "000000000010000"
  val ALU_NOR = "000000000100000"
  val ALU_SLT = "000000001000000"
  val ALU_SLTU= "000000010000000"
  val ALU_SLL = "000000100000000"
  val ALU_SRL = "000001000000000" 
  val ALU_SRA = "000010000000000"
  val ALU_PC4 = "000100000000000"
  val ALU_CPB = "001000000000000"
  val ALU_EQ  = "010000000000000"
  val MDU_SIGN= "100000000000000"

  //br_type
  val BR_XXX = "0000000001"
  val BR_BNE = "0000000010"
  val BR_BEQ = "0000000100"
  val BR_BGE = "0000001000"
  val BR_BGEU= "0000010000"
  val BR_BLT = "0000100000"
  val BR_BLTU= "0001000000"
  //br_type(jump)
  val J_B    = "0010000000"
  val J_BL   = "0100000000"
  val J_JIRL = "1000000000"

  //WB TYPE
  val WB_ALU = "0000001"
  val WB_MEM = "0000010"
  val WB_DIV = "0000100"
  val WB_MOD = "0001000"
  val WB_MULL= "0010000" //NOTE:L->low代表低位
  val WB_MULH= "0100000" //NOTE:H->High代表高位
  val WB_SCW = "1000000"

  //CSR OPERATE
  val CSR_XXXX = "0000"
  val CSR_RD   = "0001"
  val CSR_WR   = "0010"
  val CSR_XCHG = "0011"
  val CSR_ERTN = "0100"
  val CSR_BREAK= "0101"
  val CSR_SYS  = "0110"
  val CSR_INE  = "0111" //指令不存在例外
  //TLB OPERATE
  val TLB_SRCH = "1000" //NOTE:TLB共用csr的decode
  val TLB_RD   = "1001"
  val TLB_WR   = "1010"
  val TLB_FILL = "1011"
  val TLB_INV  = "1100"
  //TODO:独热码，拆开tlb

  //CNT_OP
  val CNT_XX  = "000"
  val CNT_VLID= "001"
  val CNT_VH  = "010"
  val CNT_VL  = "011"
  val CNT_ID  = "100"


  val N      = "0"
  val Y      = "1"

  import Instructions._
  val decode_default: String =
  //
                Seq(OP_XXXX,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,CSR_INE  ,CNT_XX ).reduce(_ + _)
  //                OP_TYPE A_SEL B_SEL ALU_SEL  load   store  TYPEXX  BR_TYPE  wb_sel  wen  csr_op    bar_op  cnt_op
  val decode_table: TruthTable = TruthTable(Map(
    LD_W     -> Seq(OP_LSU ,A_RJ ,B_IMM,ALU_ADD ,LD_LW  ,ST_XX ,SIMM12 ,BR_XXX ,WB_MEM  ,Y  ,CSR_XXXX ,CNT_XX ),
    LD_H     -> Seq(OP_LSU ,A_RJ ,B_IMM,ALU_ADD ,LD_LH  ,ST_XX ,SIMM12 ,BR_XXX ,WB_MEM  ,Y  ,CSR_XXXX ,CNT_XX ),
    LD_HU    -> Seq(OP_LSU ,A_RJ ,B_IMM,ALU_ADD ,LD_LHU ,ST_XX ,SIMM12 ,BR_XXX ,WB_MEM  ,Y  ,CSR_XXXX ,CNT_XX ),
    LD_B     -> Seq(OP_LSU ,A_RJ ,B_IMM,ALU_ADD ,LD_LB  ,ST_XX ,SIMM12 ,BR_XXX ,WB_MEM  ,Y  ,CSR_XXXX ,CNT_XX ),
    LD_BU    -> Seq(OP_LSU ,A_RJ ,B_IMM,ALU_ADD ,LD_LBU ,ST_XX ,SIMM12 ,BR_XXX ,WB_MEM  ,Y  ,CSR_XXXX ,CNT_XX ),
    //
    ST_B     -> Seq(OP_LSU ,A_RJ ,B_RD ,ALU_XXX ,LD_XXX ,ST_SB ,SIMM12 ,BR_XXX ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    ST_H     -> Seq(OP_LSU ,A_RJ ,B_RD ,ALU_XXX ,LD_XXX ,ST_SH ,SIMM12 ,BR_XXX ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    ST_W     -> Seq(OP_LSU ,A_RJ ,B_RD ,ALU_XXX ,LD_XXX ,ST_SW ,SIMM12 ,BR_XXX ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    //NOTE:store指令操作数很多，为了减少bypass network复杂度，单独计算rj+imm
    ADD_W    -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_ADD ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SUB_W    -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_SUB ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    AND      -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_AND ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    OR       -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_OR  ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    XOR      -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_XOR ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    NOR      -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_NOR ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SLT      -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_SLT ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SLTU     -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_SLTU,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SLL_W    -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_SLL ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SRL_W    -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_SRL ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SRA_W    -> Seq(OP_ALU ,A_RJ ,B_RK ,ALU_SRA ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    //
    MUL_W    -> Seq(OP_MUL ,A_RJ ,B_RK ,MDU_SIGN,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_MULL ,Y  ,CSR_XXXX ,CNT_XX ),
    MULH_W   -> Seq(OP_MUL ,A_RJ ,B_RK ,MDU_SIGN,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_MULH ,Y  ,CSR_XXXX ,CNT_XX ),
    MULH_WU  -> Seq(OP_MUL ,A_RJ ,B_RK ,ALU_XXX ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_MULH ,Y  ,CSR_XXXX ,CNT_XX ),
    DIV_W    -> Seq(OP_DIV ,A_RJ ,B_RK ,MDU_SIGN,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_DIV  ,Y  ,CSR_XXXX ,CNT_XX ),
    MOD_W    -> Seq(OP_DIV ,A_RJ ,B_RK ,MDU_SIGN,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_MOD  ,Y  ,CSR_XXXX ,CNT_XX ), 
    DIV_WU   -> Seq(OP_DIV ,A_RJ ,B_RK ,ALU_XXX ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_DIV  ,Y  ,CSR_XXXX ,CNT_XX ),
    MOD_WU   -> Seq(OP_DIV ,A_RJ ,B_RK ,ALU_XXX ,LD_XXX ,ST_XX ,TYPE3R ,BR_XXX ,WB_MOD  ,Y  ,CSR_XXXX ,CNT_XX ),
    //
    ADDI_W   -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_ADD ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SLTI     -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_SLT ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    ANDI     -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_AND ,LD_XXX ,ST_XX ,ZIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    ORI      -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_OR  ,LD_XXX ,ST_XX ,ZIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    XORI     -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_XOR ,LD_XXX ,ST_XX ,ZIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SLTUI    -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_SLTU,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    //
    SLLI_W   -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_SLL ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SRLI_W   -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_SRL ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    SRAI_W   -> Seq(OP_ALU ,A_RJ ,B_IMM,ALU_SRA ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    //TODO:b跳转使用alu计算
    BEQ      -> Seq(OP_BRU ,A_RJ ,B_RD ,ALU_EQ  ,LD_XXX ,ST_XX ,BIMM16 ,BR_BEQ ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    BNE      -> Seq(OP_BRU ,A_RJ ,B_RD ,ALU_EQ  ,LD_XXX ,ST_XX ,BIMM16 ,BR_BNE ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    BGE      -> Seq(OP_BRU ,A_RJ ,B_RD ,ALU_SLT ,LD_XXX ,ST_XX ,BIMM16 ,BR_BGE ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    BGEU     -> Seq(OP_BRU ,A_RJ ,B_RD ,ALU_SLTU,LD_XXX ,ST_XX ,BIMM16 ,BR_BGEU,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    BLT      -> Seq(OP_BRU ,A_RJ ,B_RD ,ALU_SLT ,LD_XXX ,ST_XX ,BIMM16 ,BR_BLT ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    BLTU     -> Seq(OP_BRU ,A_RJ ,B_RD ,ALU_SLTU,LD_XXX ,ST_XX ,BIMM16 ,BR_BLTU,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    //
    LU12I_W  -> Seq(OP_ALU ,A_XXX,B_IMM,ALU_CPB ,LD_XXX ,ST_XX ,SIMM20 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    PCADDU12I-> Seq(OP_ALU ,A_PC ,B_IMM,ALU_ADD ,LD_XXX ,ST_XX ,SIMM20 ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    //
    B        -> Seq(OP_JUMP,A_XXX,B_XXX,ALU_ADD ,LD_XXX ,ST_XX ,BIMM26 ,J_B    ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    BL       -> Seq(OP_JUMP,A_PC ,B_XXX,ALU_PC4 ,LD_XXX ,ST_XX ,BIMM26 ,J_BL   ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    JIRL     -> Seq(OP_BRU ,A_RJ ,B_XXX,ALU_PC4 ,LD_XXX ,ST_XX ,BIMM16 ,J_JIRL ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_XX ),
    //NOTE:划分依据是在哪一级处理
    //
    CSRRD    -> Seq(OP_CSR ,A_XXX,B_CSR,ALU_CPB ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,Y  ,CSR_RD   ,CNT_XX ),
    CSRWR    -> Seq(OP_CSR ,A_XXX,B_CSR,ALU_CPB ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,Y  ,CSR_WR   ,CNT_XX ),
    //
    CNTVLID_W-> Seq(OP_CNT ,A_XXX,B_CSR,ALU_CPB ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_VLID),
    RDCNTVH_W-> Seq(OP_CNT ,A_XXX,B_CSR,ALU_CPB ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,Y  ,CSR_XXXX ,CNT_VH ),
    //
    TLBSRCH  -> Seq(OP_TLB ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,TLB_SRCH ,CNT_XX ),
    TLBRD    -> Seq(OP_TLB ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,TLB_RD   ,CNT_XX ),
    TLBWR    -> Seq(OP_TLB ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,TLB_WR   ,CNT_XX ),
    TLBFILL  -> Seq(OP_TLB ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,TLB_FILL ,CNT_XX ),
    INVTLB   -> Seq(OP_TLB ,A_RJ ,B_RK ,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,TLB_INV  ,CNT_XX ),
    //
    LL_W     -> Seq(OP_ATOM,A_RJ ,B_IMM,ALU_ADD ,LD_LLW ,ST_XX ,SIMM14 ,BR_XXX ,WB_MEM  ,Y  ,CSR_XXXX ,CNT_XX ),
    SC_W     -> Seq(OP_ATOM,A_RJ ,B_RD ,ALU_ADD ,LD_XXX ,ST_SCW,SIMM14 ,BR_XXX ,WB_SCW  ,Y  ,CSR_XXXX ,CNT_XX ),
    //
    IBAR     -> Seq(OP_IBAR,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    DBAR     -> Seq(OP_DBAR,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    //
    BREAK    -> Seq(OP_CSR ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,CSR_BREAK,CNT_XX ),
    ERTN     -> Seq(OP_CSR ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,CSR_ERTN ,CNT_XX ),
    CACOP    -> Seq(OP_CACP,A_RJ ,B_IMM,ALU_ADD ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,N  ,CSR_XXXX ,CNT_XX ),
    IDLE     -> Seq(OP_IDLE,A_RJ ,B_IMM,ALU_ADD ,LD_XXX ,ST_XX ,SIMM12 ,BR_XXX ,WB_ALU  ,N  ,OP_IDLE  ,CNT_XX ),
    SYSCALL  -> Seq(OP_CSR ,A_XXX,B_XXX,ALU_XXX ,LD_XXX ,ST_XX ,TYPEXX ,BR_XXX ,WB_ALU  ,N  ,CSR_SYS  ,CNT_XX ))
    //NOTE:注意别忘了修改CSRXCHG的译码，fuck csrxchg
    .map({ case (k, v) => k -> BitPat(s"b${v.reduce(_ + _)}") }), BitPat(s"b$decode_default"));
  // format: on

}


import Control._
class DecodeSignals extends Bundle{
  val inst    =Input(UInt(32.W))

  val inst_op =Output(UInt(OP_XXXX.length.W))
  val A_sel   =Output(UInt(A_XXX.length.W))
  val B_sel   =Output(UInt(B_XXX.length.W))
  val alu_op  =Output(UInt(ALU_XXX.length.W))
  val ld_type =Output(UInt(LD_XXX.length.W))
  val st_type =Output(UInt(ST_XX.length.W))
  val imm_type=Output(UInt(TYPE2R.length.W))
  val br_type =Output(UInt(BR_XXX.length.W))
  val wb_sel  =Output(UInt(WB_ALU.length.W))
  val rf_wen  =Output(Bool())
  val csr_op  =Output(UInt(CSR_XXXX.length.W))
  val cnt_op  =Output(UInt(CNT_XX.length.W))
}

class Decode extends Module{
  val io = IO(new DecodeSignals)
  //NOTE:我恨你csrxchg，让我代码如此丑陋
  val is_fuck_csrxchg=((io.inst(31,24).asUInt===4.U)
                    &&((io.inst(9,5).asUInt=/=0.U)&&(io.inst(9,5).asUInt=/=1.U)))
  val fuck_csrxchg_decode_result=SDEF(List(
    OP_CSR ,A_RJ ,B_CSR,ALU_CPB ,LD_XXX ,ST_XX ,TYPE2R ,BR_XXX ,WB_ALU ,Y ,CSR_XCHG ,CNT_XX 
  ).mkString)
  //NOTE:手动实现一个表，再转化为UInt类型

  val decode_result=Mux(is_fuck_csrxchg,fuck_csrxchg_decode_result,
                                        decoder(QMCMinimizer,io.inst,Control.decode_table))

  val entries = Seq( 
    io.inst_op,
    io.A_sel,
    io.B_sel,
    io.alu_op,
    io.ld_type,
    io.st_type,
    io.imm_type,
    io.br_type,
    io.wb_sel,
    io.rf_wen,
    io.csr_op,
    io.cnt_op,
  ) //NOTE:chisel魔法，自动连线,自动匹配位宽
  var i = 0
  for (entry <- entries.reverse) {
    val entry_width = entry.getWidth
    if (entry_width == 1) {
      entry := decode_result(i)
    } else {
      entry := decode_result(i + entry_width - 1, i)
    }
    i += entry_width
  }
}




