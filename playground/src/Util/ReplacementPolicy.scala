import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR


abstract class ReplacementPolicy {
  def nBits: Int
  def perSet: Boolean
  def way: UInt
  def miss: Unit
  def hit: Unit
  def access(touch_way: UInt): Unit
  def access(touch_ways: Seq[Valid[UInt]]): Unit
  def state_read: UInt
  def get_next_state(state: UInt, touch_way: UInt): UInt
  def get_next_state(state: UInt, touch_ways: Seq[Valid[UInt]]): UInt = {
    touch_ways.foldLeft(state)((prev, touch_way) => Mux(touch_way.valid, get_next_state(prev, touch_way.bits), prev))
  }
  def get_replace_way(state: UInt): UInt

  def extract(x: UInt, hi: Int, lo: Int): UInt = {
    require(hi >= lo-1)
    if (hi == lo-1) 0.U
    else x(hi, lo)
  }

  def padTo(x: UInt, n: Int): UInt = {
    require(x.getWidth <= n)
    if (x.getWidth == n) x
    else Cat(0.U((n - x.getWidth).W), x)
  }

  object Random{
    def apply(mod: Int, random: UInt): UInt = {
      if (isPow2(mod)) extract(random,log2Ceil(mod)-1,0)
      else PriorityEncoder(partition(apply(1 << log2Up(mod*8), random), mod))
    }

    def apply(mod: Int): UInt = apply(mod, randomizer)

    private def randomizer = LFSR(16)
    private def partition(value: UInt, slices: Int) =
      Seq.tabulate(slices)(i => value < (((i + 1) << value.getWidth) / slices).U)
  }
}


class RandomReplacement(n_ways: Int) extends ReplacementPolicy {
  private val replace = Wire(Bool())
  replace := false.B
  def nBits = 16
  def perSet = false
  private val lfsr = LFSR(nBits, replace)
  def state_read = WireDefault(lfsr)

  def way = Random(n_ways, lfsr)
  def miss = replace := true.B
  def hit = {}
  def access(touch_way: UInt) = {}
  def access(touch_ways: Seq[Valid[UInt]]) = {}
  def get_next_state(state: UInt, touch_way: UInt) = 0.U //DontCare
  def get_replace_way(state: UInt) = way
}



class PseudoLRU(n_ways: Int) extends ReplacementPolicy{
  // Pseudo-LRU tree algorithm: https://en.wikipedia.org/wiki/Pseudo-LRU#Tree-PLRU
  //
  //
  // - bits storage example for 4-way PLRU binary tree:
  //                  bit[2]: ways 3+2 older than ways 1+0
  //                  /                                  \
  //     bit[1]: way 3 older than way 2    bit[0]: way 1 older than way 0
  //
  //
  // - bits storage example for 3-way PLRU binary tree:
  //                  bit[1]: way 2 older than ways 1+0
  //                                                  \
  //                                       bit[0]: way 1 older than way 0
  //
  //
  // - bits storage example for 8-way PLRU binary tree:
  //                      bit[6]: ways 7-4 older than ways 3-0
  //                      /                                  \
  //            bit[5]: ways 7+6 > 5+4                bit[2]: ways 3+2 > 1+0
  //            /                    \                /                    \
  //     bit[4]: way 7>6    bit[3]: way 5>4    bit[1]: way 3>2    bit[0]: way 1>0

  def nBits = n_ways - 1
  def perSet = true
  protected val state_reg = if (nBits == 0) Reg(UInt(0.W)) else RegInit(0.U(nBits.W))
  def state_read = WireDefault(state_reg)

  def access(touch_way: UInt): Unit = {
    state_reg := get_next_state(state_reg, touch_way)
  }
  def access(touch_ways: Seq[Valid[UInt]]): Unit = {
    when (touch_ways.map(_.valid).reduce(_||_)) {
      state_reg := get_next_state(state_reg, touch_ways)
    }
    // for (i <- 1 until touch_ways.size) {
    //   cover(PopCount(touch_ways.map(_.valid)) === i.U, s"PLRU_UpdateCount$i", s"PLRU Update $i simultaneous")
    // }
  }


  /** @param state state_reg bits for this sub-tree
    * @param touch_way touched way encoded value bits for this sub-tree
    * @param tree_nways number of ways in this sub-tree
    */
  def get_next_state(state: UInt, touch_way: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways-1),                   s"wrong state bits width ${state.getWidth} for $tree_nways ways")
    require(touch_way.getWidth == (log2Ceil(tree_nways) max 1), s"wrong encoded way width ${touch_way.getWidth} for $tree_nways ways")

    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1)  // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways         // number of ways in the left sub-tree
      val set_left_older      = !touch_way(log2Ceil(tree_nways)-1)
      val left_subtree_state  = extract(state, tree_nways-3, right_nways-1)
      val right_subtree_state = state(right_nways-2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(set_left_older,
            Mux(set_left_older,
                left_subtree_state,  // if setting left sub-tree as older, do NOT recurse into left sub-tree
                get_next_state(left_subtree_state, extract(touch_way, log2Ceil(left_nways)-1,0), left_nways)),  // recurse left if newer
            Mux(set_left_older,
                get_next_state(right_subtree_state, touch_way(log2Ceil(right_nways)-1,0), right_nways),  // recurse right if newer
                right_subtree_state))  // if setting right sub-tree as older, do NOT recurse into right sub-tree
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(set_left_older,
            Mux(set_left_older,
                get_next_state(right_subtree_state, touch_way(log2Ceil(right_nways)-1,0), right_nways),  // recurse right if newer
                right_subtree_state))  // if setting right sub-tree as older, do NOT recurse into right sub-tree
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so set the single state bit opposite of the lsb of the touched way encoded value
      !touch_way(0)
    } else {  // tree_nways <= 1
      // we are at an empty node in an empty tree for 1 way, so return single zero bit for Chisel (no zero-width wires)
      0.U(1.W)
    }
  }

  def get_next_state(state: UInt, touch_way: UInt): UInt = {
    val touch_way_sized = if (touch_way.getWidth < log2Ceil(n_ways)) padTo(touch_way, log2Ceil(n_ways))
                                                                else extract(touch_way, log2Ceil(n_ways)-1,0)
    get_next_state(state, touch_way_sized, n_ways)
  }


  /** @param state state_reg bits for this sub-tree
    * @param tree_nways number of ways in this sub-tree
    */
  def get_replace_way(state: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways-1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")

    // this algorithm recursively descends the binary tree, filling in the way-to-replace encoded value from msb to lsb
    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1)  // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways         // number of ways in the left sub-tree
      val left_subtree_older  = state(tree_nways-2)
      val left_subtree_state  = extract(state, tree_nways-3, right_nways-1)
      val right_subtree_state = state(right_nways-2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(left_subtree_older,      // return the top state bit (current tree node) as msb of the way-to-replace encoded value
            Mux(left_subtree_older,  // if left sub-tree is older, recurse left, else recurse right
                get_replace_way(left_subtree_state,  left_nways),    // recurse left
                get_replace_way(right_subtree_state, right_nways)))  // recurse right
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(left_subtree_older,      // return the top state bit (current tree node) as msb of the way-to-replace encoded value
            Mux(left_subtree_older,  // if left sub-tree is older, return and do not recurse right
                0.U(1.W),
                get_replace_way(right_subtree_state, right_nways)))  // recurse right
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so just return the single state bit as lsb of the way-to-replace encoded value
      state(0)
    } else {  // tree_nways <= 1
      // we are at an empty node in an unbalanced tree for non-power-of-2 ways, so return single zero bit as lsb of the way-to-replace encoded value
      0.U(1.W)
    }
  }

  def get_replace_way(state: UInt): UInt = get_replace_way(state, n_ways)

  def way = get_replace_way(state_reg)
  def miss = access(way)
  def hit = {}
}




/*
  // The implementation using a setable global is bad, but removes dependence on Parameters
  // This change was made in anticipation of a proper cover library
  object cover {
    private var propLib: BasePropertyLibrary = new DefaultPropertyLibrary
    def setPropLib(lib: BasePropertyLibrary): Unit = this.synchronized {
      propLib = lib
    }
    def apply(cond: Bool)(implicit sourceInfo: SourceInfo): Unit = {
      propLib.generateProperty(CoverPropertyParameters(cond))
    }
    def apply(cond: Bool, label: String)(implicit sourceInfo: SourceInfo): Unit = {
      propLib.generateProperty(CoverPropertyParameters(cond, label))
    }
    def apply(cond: Bool, label: String, message: String)(implicit sourceInfo: SourceInfo): Unit = {
      propLib.generateProperty(CoverPropertyParameters(cond, label, message))
    }
    def apply(prop: BaseProperty)(implicit sourceInfo: SourceInfo): Unit = {
      prop.generateProperties().foreach( (pp: BasePropertyParameters) => {
        if (pp.pType == PropertyType.Cover) {
          propLib.generateProperty(CoverPropertyParameters(pp.cond, pp.label, pp.message))
        }
      })
    }
    def apply[T <: Data](rv: ReadyValidIO[T], label: String, message: String)(implicit sourceInfo: SourceInfo): Unit = {
      apply( rv.valid &&  rv.ready, label + "_FIRE",  message + ": valid and ready")
      apply( rv.valid && !rv.ready, label + "_STALL", message + ": valid and not ready")
      apply(!rv.valid &&  rv.ready, label + "_IDLE",  message + ": not valid and ready")
      apply(!rv.valid && !rv.ready, label + "_FULL",  message + ": not valid and not ready")
    }
  }
}

*/