import chisel3._
import chisel3.util._  
import config.Configs._

class perf_branch_bundle extends Bundle{
  val br_type=UInt(Control.BR_XXX.length.W)
  val taken  =Bool()
}
