import circt.stage._

object TopMain extends App {
  def top = new SimTop()
  val ChiselStageOptions=Seq(
    chisel3.stage.ChiselGeneratorAnnotation(() => top),
    CIRCTTargetAnnotation(CIRCTTarget.SystemVerilog)
  )

  val firtoolOptions=Seq(
    FirtoolOption(
      "--lowering-options=disallowLocalVariables,locationInfoStyle=wrapInAtSquareBracket"
    ), //香山firtool参数
    FirtoolOption("--preserve-aggregate=vec"),
    FirtoolOption("--split-verilog"), //分离生成的verilog文件
    FirtoolOption("-o=vsrc"), //设置分离后verilog文件的路径
    FirtoolOption("--disable-all-randomization") //禁止随机数
  )
  val executeOptions=ChiselStageOptions++firtoolOptions
  val executeArgs=Array("-td","build")
  // (new ChiselStage).execute(args,executeOptions)
  (new ChiselStage).execute(executeArgs,executeOptions)
}

