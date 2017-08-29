package com.cibo.evilplot.colors

trait Color {
  val repr: String
}

case class HSL(hue: Int, saturation: Int, lightness: Int) extends Color {
  require(hue        >= 0 && hue        <  360, s"hue must be within [0, 360) {was $hue}")
  require(saturation >= 0 && saturation <= 100, s"saturation must be within [0, 100] {was $saturation}")
  require(lightness  >= 0 && lightness  <= 100, s"lightness must be within [0, 100] {was $lightness}")

  private def boundHue(hue: Int) = if (hue < 0) hue + 360 else if (hue > 360) hue - 360 else hue
  def triadic: (HSL, HSL) = (this.copy(hue = boundHue(this.hue - 120)), this.copy(hue = boundHue(this.hue + 120)))
  def analogous: (HSL, HSL) = (this.copy(hue = boundHue(this.hue - 14)), this.copy(hue = boundHue(this.hue + 14)))
  def incremental(increment:Int): (HSL, HSL) = (this.copy(hue = boundHue(this.hue - increment)), this.copy(hue = boundHue(this.hue + increment)))

  val repr = s"hsl($hue, $saturation%, $lightness%)"
}

case object Clear extends Color {
  val repr: String = "rgba(0,0,0,0)"
}

object Colors {

  // TODO: this needs work
  def stream: Seq[HSL] = {
    val hueSpan = 7
    Stream.from(0).map{ i =>
      // if hueSpan = 8, for instance:
      // Epoch 0 ->  8 equally spaced  0 -  7
      // Epoch 1 -> 16 equally spaced  8 - 21
      // Epoch 2 -> 32 equally spaced 22 - 53
      // Epoch 3 -> 64 equally spaced 54 - 117
      // Epoch 4 -> 128 equally spaced 118 - 245
      // ..
      // e^2 * 8
      // pt = sum_epoch( 8 * 2 ^ (e) ) - log(8)/log(2) // not quite right this last term?
      // pt = 8 * 2 ^ (2) + 8 * 2 ^ (1) + 8 * 2 ^ (0) - 3
      // pt = 8 * (2 ^ (2) + 2 ^ (1) + 2 ^ (0)) - 3
      // pt = 8 * (2^(e+1) - 1) - 3

      import math._
      def log2(x: Double) = log(x) / log(2)
      val magicFactor = log2(hueSpan) // TODO: this may or may not be correct for other hueSpan's
      val epoch = if (i < hueSpan) 0 else ceil(log2(((i + magicFactor) / hueSpan) + 1) - 1).toInt

      def endIndexOfThisEpoch(e: Int) = 8 * (pow(2, e + 1) - 1) - magicFactor

      val slicesThisEpoch = hueSpan * Math.pow(2, epoch)
      val initialRotate = 360.0 / slicesThisEpoch / 2.0

      val zeroBasedIndexInThisEpoch = i - endIndexOfThisEpoch(epoch - 1) - 1

      val saturationDropoff = 2
      def saturationLevel(e: Int) = 100 * 1.0 / pow(saturationDropoff, epoch + 1)
      val saturationBase = 50//100 - saturationLevel(0)
      HSL(
        abs(round(initialRotate + 360.0 / slicesThisEpoch * zeroBasedIndexInThisEpoch).toInt % 360),
        (saturationBase + saturationLevel(epoch)).round.toInt,
        50
      )
    }
  }

  trait ColorBar

  // Use when one color is wanted but a ColorBar is needed.
  case class SingletonColorBar(color: Color) extends ColorBar

  case class GradientColorBar(nColors: Int, zMin: Double, zMax: Double) extends ColorBar {
    private val startH = 0
    private val endH = 359
    private val deltaH = (endH - startH) / nColors.toFloat
    private val zWidth = (zMax - zMin) / nColors.toFloat
    private val colors: Seq[Color] = Seq.tabulate(nColors)(x => HSL(startH + (x * deltaH).toInt, 90, 54))

    def getColor(i: Int): Color = {
      require((i >= 0) && (i < colors.length))
      colors(i)
    }

    def getColor(z: Double): Color = {
      val colorIndex = math.min(math.round(math.floor((z - zMin) / zWidth)).toInt, nColors - 1)
      colors(colorIndex)
    }
  }

  //TODO: Experimental doesn't split analogous colors up properly
  object ColorSeq{
    def analogGrow(node: HSL, depth: Int): Seq[HSL] = {

      val list = for {
        i <- Iterator.range(1, depth)
        val newHue = ((i * 60) + node.hue) % 360
        val newAdjHue = (newHue + 14) % 360
        val newHSL = HSL(newHue, node.saturation, node.lightness)
        val newAdjHSL = HSL(newAdjHue, node.saturation, node.lightness)
        yield(newHSL, newAdjHSL)
      }
      println(list.fl)
      list
    }

//    def analogGrow(node: HSL, depth: Int): Seq[HSL] = {
//      val left = node.analogous._1
//      val right = node.analogous._2
//      if (depth > 0) node +: (incrementalGrow(right, depth - 1))
//      else Seq()
//    }

    def triadGrow(node: HSL, depth: Int): Seq[HSL] = {
      val left = node.triadic._1
      val right = node.triadic._2
      if (depth > 0) node +: (analogGrow(right, depth))
      else Seq()
    }

    def incrementalGrow(node: HSL, depth: Int): Seq[HSL] = {
      val left = node.incremental(60)._1
      val right = node.incremental(60)._2
      if (depth > 0) node +: (analogGrow(right, depth))
      else Seq()
    }

    def apply(seed: HSL, depth: Int): Seq[HSL] = {
      analogGrow(seed, depth)
    }
  }

  def triAnalogStream(seed: HSL = HSL(207, 90, 54)): Seq[HSL] = {
    val colors = ColorSeq(seed, 5)
    Stream.from(0).map{ x =>
      colors(x % colors.length)
    }
  }

}
