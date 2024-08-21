// package chiselfunc
import chisel3._
import chisel3.util._  

object SDEF{ //将string定义的宏转换为uint类型便于使用
  def apply(s: String) = {
    val bnum="b"+s
    bnum.U
  }
}//String def -> uint def

object MuxPriorA { //优先选择Vec(0)
  def apply[T <: Data](sel_en: Bool, sel_parameters: Vec[T]): T = {
    // 确保Vec中的所有元素都有相同的位宽
    require(sel_parameters.forall(_.getWidth == sel_parameters(0).getWidth))
    Mux(sel_en, sel_parameters(0), sel_parameters(1))
  }
}

object Sext{ //有符号位宽扩展
  def apply(num:UInt,e_width:Int) = {
    val num_width=num.getWidth
    if(num_width<e_width){
      Cat(Fill(e_width-num_width,num(num_width-1)),num(num_width-1,0))
    }else{
      num(num_width-1,0)
    }
  }
}

object Zext{ //无符号位宽扩展
  def apply(num:UInt,e_width:Int) = {
    val num_width=num.getWidth
    if(num_width==1){
      Cat(Fill(e_width-num_width,0.U),num)
    }else if(num_width<e_width){
      Cat(Fill(e_width-num_width,0.U),num(num_width-1,0))
    }else{
      num(num_width-1,0)
    }
  }
}

object OneHotDef{ //便于把宏转换成下标索引
  def apply(oneHot: String) = {
    oneHot.reverse.indexOf('1').asUInt(log2Ceil(oneHot.length).W)
  }
}

object Mux1hMap{ //便于把宏转换成Mux1h的索引
  def apply(rd_addr: UInt, Map: Map[UInt, UInt]): UInt = {
    Mux1H(Map.map { case (addr, csr) => (rd_addr === addr) -> csr })
  }
}

object ParallelOperation {
  def apply[T](xs: Seq[T], func: (T, T) => T): T = {
    require(xs.nonEmpty)
    xs match {
      case Seq(a) => a
      case Seq(a, b) => func(a, b)
      case _ =>
        apply(Seq(apply(xs take xs.size/2, func), apply(xs drop xs.size/2, func)), func)
    }
  }
}

object ParallelXOR {
  def apply[T <: Data](xs: Seq[T]): T = {
    ParallelOperation(xs, (a: T, b:T) => (a.asUInt ^ b.asUInt).asTypeOf(xs.head))
  }
}

// def min(x: UInt, y: UInt, lanes: Int) = {
//     val xlo = if (lanes==1) 0.U else x(log2Ceil(lanes)-1, 0)
//     val ylo = if (lanes==1) 0.U else y(log2Ceil(lanes)-1, 0)
//     val xhi = x >= lanes.U
//     val yhi = y >= lanes.U
//     val xly = xlo <= ylo
//     Mux(xhi && yhi, lanes.U, Mux(Mux(xly, !xhi, yhi), xlo, ylo))
// }