import chisel3._
import chisel3.util._

class TreeLRU(nWays: Int) extends Module {
  require(isPow2(nWays), "Number of ways must be a power of 2")
  
  val io = IO(new Bundle {
    val access = Input(UInt(log2Ceil(nWays).W)) // The accessed way
    val update = Input(Bool()) // Signal to update the LRU tree
    val lruWay = Output(UInt(log2Ceil(nWays).W)) // The way to be replaced (LRU way)
  })

  val logNWays = log2Ceil(nWays)
  val nNodes = nWays - 1
  val tree = RegInit(VecInit(Seq.fill(nWays)(false.B))) // LRU tree nodes

  // Function to recursively find the LRU way
  def findLRU(idx: UInt, tree_idx: UInt, level: Int): UInt = {
    if (level == logNWays) {
      idx
    } else {
      val bit = tree(tree_idx(logNWays-1,0))
      val nextIdx = Cat(idx, !bit)
      val nextTreeIdx = Mux(bit, 2.U*tree_idx, 2.U*tree_idx+1.U)
      findLRU(nextIdx, nextTreeIdx, level + 1)
    }
  }

  // Function to recursively update the LRU tree
  def updateLRU(tree_idx: UInt, level: Int, way: UInt): Unit = {
    if (level < logNWays) {
      val bit = way(logNWays - level - 1)
      tree(tree_idx(logNWays-1,0)) := bit 
      val nextTreeIdx = Mux(bit, 2.U*tree_idx+1.U, 2.U*tree_idx)
      updateLRU(nextTreeIdx, level + 1, way)
    }
  }

  io.lruWay := findLRU(0.U, 1.U, 0)

  when(io.update) {
    updateLRU(1.U, 0, io.access)
  }
}
