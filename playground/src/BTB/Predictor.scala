import chisel3._
import chisel3.util._
import config.Configs._


trait PredParams {
    def nPHT = 128
    def PHTLen = log2Ceil(nPHT)
    def nBHT = 128
    def BHTLen = log2Ceil(nBHT)
    def BHRLen = log2Ceil(nPHT)
}

class PredictorEntry extends Bundle {
    val valid = Bool()
    val brTaken = Bool()
    val pc = UInt(ADDR_WIDTH.W)
}

class BHTEntry extends Bundle with PredParams{
    val valid = Bool()
    val bhr = UInt(BHRLen.W)
}

class PHTEntry extends Bundle{
    val valid = Bool()
    val ctr = UInt(2.W)
}

class Predictor extends Module with BPUtils with PredParams{
    val io = IO(new Bundle{
        val pc_in0  = Input(UInt(ADDR_WIDTH.W))
        val pc_in1  = Input(UInt(ADDR_WIDTH.W))

        val brTaken0 = Output(Bool())
        val brTaken1 = Output(Bool())

        val update = Input(new PredictorEntry)
    })

    val bhtBank =RegInit(VecInit(Seq.fill(nBHT)(0.U.asTypeOf(new BHTEntry()))))

    val phtBank =RegInit(VecInit(Seq.fill(nPHT)(0.U.asTypeOf(new PHTEntry()))))

    val bht_idx_0, bht_idx_1, bht_idx_u = Wire(UInt(BHTLen.W))
    val pht_idx_0, pht_idx_1, pht_idx_u = Wire(UInt(PHTLen.W))

    bht_idx_0 := compute_folded_idx(io.pc_in0, ADDR_WIDTH, BHTLen)
    bht_idx_1 := compute_folded_idx(io.pc_in1, ADDR_WIDTH, BHTLen)

    val bhr0 = bhtBank(bht_idx_0).bhr
    val bhr1 = bhtBank(bht_idx_1).bhr
    
    pht_idx_0 := bhr0 ^ compute_folded_idx(io.pc_in0, ADDR_WIDTH, PHTLen)
    pht_idx_1 := bhr1 ^ compute_folded_idx(io.pc_in1, ADDR_WIDTH, PHTLen)

    io.brTaken0 := phtBank(pht_idx_0).valid && phtBank(pht_idx_0).ctr(1)
    io.brTaken1 := phtBank(pht_idx_1).valid && phtBank(pht_idx_1).ctr(1)
    
    //update
    bht_idx_u := compute_folded_idx(io.update.pc, ADDR_WIDTH, BHTLen)
    val bht_r = bhtBank(bht_idx_u).bhr
    pht_idx_u := bht_r ^ compute_folded_idx(io.update.pc, ADDR_WIDTH, PHTLen)

    when(io.update.valid){
        when(bhtBank(bht_r).valid){
            bhtBank(bht_r).bhr := Cat(bhtBank(bht_r).bhr(BHRLen - 2, 0), io.update.brTaken)
        }.otherwise{
            bhtBank(bht_r).bhr := io.update.brTaken
        }

        when(phtBank(pht_idx_u).valid){
            phtBank(pht_idx_u).ctr := satUpdate(phtBank(pht_idx_u).ctr, 2, io.update.brTaken)
        }
    }

}

