# 非常重要

对于使用vivado仿真

```
liangyi/playground/src/Config.scala
```

请在该文件下修改CacheConfig为

```scala
object CacheConfig{
  def LINE_WIDTH=256
  def LINE_WORD_NUM=(LINE_WIDTH/32)

  def WAY_NUM_I=8
  def WAY_NUM_D=4
  def TAG_WIDTH=20
  def INDEX_WIDTH=12-OFFSET_WIDTH
  def OFFSET_WIDTH=log2Ceil(LINE_WORD_NUM) + 2
  def USE_LRU=false
}

```

再添加此ip核

