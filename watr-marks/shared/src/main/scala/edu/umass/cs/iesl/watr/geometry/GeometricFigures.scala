package edu.umass.cs.iesl.watr
package geometry

import scalaz.Equal
import scalaz.syntax.equal._
import scalaz.std.anyVal._
import geometry.syntax._

// import utils.Color
import TypeTags._

import utils.ExactFloats._
import scala.scalajs.js.annotation._

sealed trait GeometricFigure { self =>
  lazy val minBounds = minBoundingRect(self)
}

@JSExportTopLevel("LTBounds")
case class LTBounds(
  left   : Int@@FloatRep,
  top    : Int@@FloatRep,
  width  : Int@@FloatRep,
  height : Int@@FloatRep
) extends GeometricFigure  {
  override def toString: String = this.prettyPrint
  def right = left+width
  def bottom = top+height

  @JSExport("left")   val getLeft: Double = left.asDouble
  @JSExport("top")    val getTop: Double = top.asDouble
  @JSExport("width")  val getWidth: Double = width.asDouble
  @JSExport("height") val getHeight: Double = height.asDouble
  @JSExport("right")  val getRight: Double = right.asDouble
  @JSExport("bottom") val getBottom: Double = bottom.asDouble
}

@JSExportTopLevel("LTBoundsCompanion")
object LTBounds {

  @JSExport("FromInts")
  def FromInts(l: Int, t: Int, w: Int, h: Int) = Ints(l, t, w, h)

  object IntReps {
    def apply(left: Int, top: Int, width: Int, height: Int): LTBounds =
      LTBounds(FloatRep(left), FloatRep(top), FloatRep(width), FloatRep(height))

    def unapply(bbox: LTBounds): Option[(Int, Int, Int, Int)] = Some((
      bbox.left.unwrap,
      bbox.top.unwrap,
      bbox.width.unwrap,
      bbox.height.unwrap
    ))
  }

  object Ints {

    def apply(left: Int, top: Int, width: Int, height: Int): LTBounds =
      LTBounds(left.toFloatExact(), top.toFloatExact, width.toFloatExact, height.toFloatExact)

    def unapply(bbox: LTBounds): Option[(Int, Int, Int, Int)] = Some((
      bbox.left.asInt,
      bbox.top.asInt,
      bbox.width.asInt,
      bbox.height.asInt
    ))
  }

  object Doubles {
    def apply(left: Double, top: Double, width: Double, height: Double): LTBounds =
      LTBounds(left.toFloatExact(), top.toFloatExact, width.toFloatExact, height.toFloatExact)

    def unapply(bbox: LTBounds): Option[(Double, Double, Double, Double)] = Some((
      bbox.left.asDouble,
      bbox.top.asDouble,
      bbox.width.asDouble,
      bbox.height.asDouble
    ))
  }

  object Floats {
    def apply(left: Float, top: Float, width: Float, height: Float): LTBounds =
      LTBounds(left.toFloatExact(), top.toFloatExact, width.toFloatExact, height.toFloatExact)

    def unapply(bbox: LTBounds): Option[(Float, Float, Float, Float)] = Some((
      bbox.left.asFloat(),
      bbox.top.asFloat(),
      bbox.width.asFloat(),
      bbox.height.asFloat()
    ))
  }

  val empty = IntReps.apply(0, 0, 0, 0)
  val zero = empty

  implicit class RicherLTBounds(val theBbox: LTBounds) extends AnyVal {
    def toLBBounds: LBBounds = {
      LBBounds(
        left = theBbox.left,
        bottom =  theBbox.top+theBbox.height,
        width = theBbox.width,
        height = theBbox.height
      )
    }

    def prettyPrint: String = {
      val left = theBbox.left
      val top=  theBbox.top
      val width = theBbox.width
      val height = theBbox.height
      s"""(l:${left.pp}, t:${top.pp}, w:${width.pp}, h:${height.pp})"""
    }

    def lowLeftCornerPrint: String = {
      val left = theBbox.left
      val bottom = theBbox.bottom
      s"""[${left.pp}, ${bottom.pp}]"""
    }

    def compactPrint: String = {
      val left = theBbox.left
      val top=  theBbox.top
      val width = theBbox.width
      val height = theBbox.height
      s"""[${left.pp}, ${top.pp}, ${width.pp}, ${height.pp}]"""
    }

    def uriString: String = {
      val left = theBbox.left
      val top=  theBbox.top
      val width = theBbox.width
      val height = theBbox.height
      s"""${left.pp}+${top.pp}+${width.pp}+${height.pp}"""
    }
  }

}

case class LBBounds(
  left: Int@@FloatRep,
  bottom: Int@@FloatRep,
  width: Int@@FloatRep,
  height: Int@@FloatRep
) extends GeometricFigure {
  override def toString: String = this.prettyPrint

  def toLTBounds: LTBounds = {
    LTBounds(
      left = left,
      top = bottom-height,
      width = width, height = height
    )
  }
  def prettyPrint: String = {
    s"""(l:${left.pp}, b:${bottom.pp}, w:${width.pp}, h:${height.pp})"""
  }
}

object LBBounds {

  object Doubles {
    def apply(left: Double, bottom: Double, width: Double, height: Double): LBBounds =
      LBBounds(left.toFloatExact(), bottom.toFloatExact, width.toFloatExact, height.toFloatExact)

    def unapply(bbox: LBBounds): Option[(Double, Double, Double, Double)] = Some((
      bbox.left.asDouble,
      bbox.bottom.asDouble,
      bbox.width.asDouble,
      bbox.height.asDouble
    ))
  }

}

case class Point(
  x: Int@@FloatRep, y: Int@@FloatRep
) extends GeometricFigure {

  override def toString: String = this.prettyPrint
}

object Point {

  object IntReps {
    def apply(x: Int, y: Int): Point =
      Point(FloatRep(x), FloatRep(y))

    def unapply(p: Point): Option[(Int, Int)] =
      Some((p.x.unwrap, p.y.unwrap))
  }


  object Ints {
    def apply(x: Int, y: Int): Point =
      Point(x.toFloatExact(), y.toFloatExact())

    def unapply(p: Point): Option[(Int, Int)] =
      Some((p.x.asInt, p.y.asInt))
  }

  object Doubles {
    def apply(x: Double, y: Double): Point =
      Point(x.toFloatExact, y.toFloatExact)

    def unapply(p: Point): Option[(Double, Double)] = {
      Some((p.x.asDouble, p.y.asDouble))
    }
  }

  val origin = Ints(0, 0)
  val zero = origin

}

case class Line(
  p1: Point, p2: Point
) extends GeometricFigure {
  override def toString: String = this.prettyPrint
}

case class Trapezoid(
  topLeft: Point,
  topWidth: Int@@FloatRep,
  bottomLeft: Point,
  bottomWidth: Int@@FloatRep
) extends GeometricFigure  { self =>

  def height(): Int@@FloatRep = bottomLeft.y - topLeft.y

}

object Trapezoid {

  def isHorizontal(l: Line): Boolean = l.p1.y==l.p2.y

  def fromHorizontals(l1: Line, l2: Line): Trapezoid = {
    assume(isHorizontal(l1) && isHorizontal(l2))
    val Seq(ltop, lbottom) = Seq(l1, l2).sortBy(_.p1.y)
    val ltn = ltop.sortPointsAsc
    val lbn = lbottom.sortPointsAsc

    val tWidth = ltn.p2.x - ltn.p1.x
    val bWidth = lbn.p2.x - lbn.p1.x

    Trapezoid(
      ltn.p1, tWidth,
      lbn.p1, bWidth
    )
  }
}

case class Padding(
  left: Int@@FloatRep,
  top: Int@@FloatRep,
  right: Int@@FloatRep,
  bottom: Int@@FloatRep
) {

  override def toString: String = {
    s"""pad[l:${left.pp}, t:${top.pp}, r:${right.pp}, b:${bottom.pp}]"""
  }
}


object Padding {
  object IntReps {
    def apply(left: Int, top: Int, right: Int, bottom: Int): Padding =
      Padding(FloatRep(left), FloatRep(top), FloatRep(right), FloatRep(bottom))

    def apply(p: Int): Padding = apply(p, p, p, p)

    def unapply(pad: Padding): Option[(Int, Int, Int, Int)] = {
      Some((
        pad.left.unwrap,
        pad.top.unwrap,
        pad.right.unwrap,
        pad.bottom.unwrap
      ))
    }
  }

  object Ints {
    def apply(left: Int, top: Int, right: Int, bottom: Int): Padding =
      Padding(left.toFloatExact, top.toFloatExact, right.toFloatExact, bottom.toFloatExact)

    def apply(p: Int): Padding = apply(p, p, p, p)

    def unapply(pad: Padding): Option[(Int, Int, Int, Int)] = {
      Some((
        pad.left.asInt,
        pad.top.asInt,
        pad.right.asInt,
        pad.bottom.asInt
      ))
    }
  }

  object Doubles {
    def apply(left: Double, top: Double, right: Double, bottom: Double): Padding =
      Padding(left.toFloatExact, top.toFloatExact, right.toFloatExact, bottom.toFloatExact)

    def apply(p: Double): Padding = apply(p, p, p, p)

    def unapply(pad: Padding): Option[(Double, Double, Double, Double)] = {
      Some((
        pad.left.asDouble,
        pad.top.asDouble,
        pad.right.asDouble,
        pad.bottom.asDouble
      ))
    }
  }
}

object GeometryImplicits extends RectangularCuts {

  implicit def EqualGeometricFigure
      : Equal[GeometricFigure] =
    Equal.equal((a, b)  => (a, b) match {

      case (g1: LTBounds, g2: LTBounds) => (
        g1.left === g2.left && g1.top === g2.top &&
          g1.width === g2.width && g1.height === g2.height)

      case (g1: LBBounds, g2: LBBounds) => g1.toLTBounds === g2.toLTBounds
      case (g1: LBBounds, g2: LTBounds) => g1.toLTBounds === g2
      case (g1: LTBounds, g2: LBBounds) => g1 === g2.toLTBounds
      case (g1: Point, g2: Point)       => g1.x===g2.x && g1.y===g2.y
      case (g1: Line, g2: Line)         => g1.p1===g2.p1 && g1.p2===g2.p2
      case (g1: Trapezoid, g2: Trapezoid) =>
        (g1.topLeft === g2.topLeft
          && g1.bottomLeft === g2.bottomLeft
          && g1.topWidth === g2.topWidth
          && g1.bottomWidth === g2.bottomWidth
        )

      case (_, _)                       => false
    })

  implicit val EqualLTBounds: Equal[LTBounds] = Equal.equalBy(_.asInstanceOf[GeometricFigure])
  implicit val EqualLBBounds: Equal[LBBounds] = Equal.equalBy(_.asInstanceOf[GeometricFigure])
  implicit val EqualPoint: Equal[Point] = Equal.equalBy(_.asInstanceOf[GeometricFigure])
  implicit val EqualLine: Equal[Line] = Equal.equalBy(_.asInstanceOf[GeometricFigure])
  implicit val EqualTrapezoid: Equal[Trapezoid] = Equal.equalBy(_.asInstanceOf[GeometricFigure])


  def minBoundingRect(fig: GeometricFigure): LTBounds = fig match {
    case f: LTBounds       => f
    case f: LBBounds       => f.toLTBounds
    case f: Point          => LTBounds(f.x, f.y, FloatRep(0), FloatRep(0))
    case f: Line           => f.bounds()
    case f: Trapezoid      =>
      val Trapezoid(Point(tlx, tly), twidth, Point(blx, bly), bwidth) = f
      val minx = min(tlx, blx)
      val maxx = max(tlx+twidth, blx+bwidth)

      val miny = min(tly, bly)
      val maxy = max(tly, bly)

      LTBounds(minx, miny, maxx-minx, maxy-miny)

    case x => sys.error(s"minBoundingRect unexpected case ${x}")
  }

  def intersectionMBR(f1: GeometricFigure, f2: GeometricFigure): Option[LTBounds] = {
    minBoundingRect(f1).intersection(minBoundingRect(f2))
  }

  def shapesIntersect(f1: GeometricFigure, f2: GeometricFigure): Boolean =
    intersectionMBR(f1, f2).isDefined

  def shapesOverlap(f1: GeometricFigure, f2: GeometricFigure): Boolean =
    intersectionMBR(f1, f2).exists(_.area > 0)

  def shapesTouch(f1: GeometricFigure, f2: GeometricFigure): Boolean =
    intersectionMBR(f1, f2).exists(_.area == 0)


  def makeFringeParts(fig: GeometricFigure, padding: Padding): List[GeometricFigure] = {

    val wbbox = minBoundingRect(fig)

    val leftGutter = wbbox.copy(
      width=padding.left
    )

    val rightGutter = wbbox.copy(
      left=(wbbox.right-padding.right),
      width=padding.right
    )

    val topGutter = wbbox.copy(
      left=wbbox.left+padding.left,
      width=wbbox.width-(padding.right+padding.left),
      height=padding.top
    )

    val bottomGutter = wbbox.copy(
      left=topGutter.left,
      top=wbbox.bottom-padding.bottom,
      width=topGutter.width,
      height=padding.bottom
    )

    List(leftGutter, rightGutter, topGutter, bottomGutter)
  }


  // def makeFringe(fig: GeometricFigure, padding: Padding): GeometricFigure = {
  //   val fringe = makeFringeParts(fig, padding)
  //   val wbbox = minBoundingRect(fig)
  //   GeometricGroup(
  //     wbbox,
  //     fringe
  //   )
  // }


  implicit class RicherPoint(val self: Point) extends AnyVal {
    // def +(r: Double): Int@@FloatRep = self + r.toFloatExact()
    // def -(r: Double): Int@@FloatRep = self - r.toFloatExact()
    // def *(r: Double): Int@@FloatRep = (self.asDouble * r).toFloatExact
    // def /(r: Double): Int@@FloatRep = (self.asDouble / r).toFloatExact

    def +(p: Point): Point = translate(p)
    def -(p: Point): Point = translate(-p)
    // def *(r: Double): Int@@FloatRep = (self.asDouble * r).toFloatExact
    // def /(r: Double): Int@@FloatRep = (self.asDouble / r).toFloatExact

    def unary_-(): Point = {
      Point(-self.x, -self.y)
    }

    def lineTo(p1: Point): Line = {
      Line(self, p1)
    }

    def translate(x: Double, y: Double): Point = {
      Point(self.x+x, self.y+y)
    }
    def translate(p: Point): Point = {
      Point(self.x+p.x, self.y+p.y)
    }
    def translate(x: Int@@FloatRep, y: Int@@FloatRep): Point = {
      Point(self.x+x, self.y+y)
    }

    def hdist(p1: Point): Double = (p1.x - self.x).asDouble()
    def hdistAbs(p1: Point): Double = math.abs(hdist(p1))

    def vdist(p1: Point): Double = (p1.y - self.y).asDouble()
    def vdistAbs(p1: Point): Double = math.abs(vdist(p1))

    def dist(p1: Point): Double = {
      val x = (self hdist p1)
      val y = (self vdist p1)
      math.sqrt(x*x + y*y)
    }

    def angleTo(p1: Point): Double = {
      val dy = self.y - p1.y
      val dx = p1.x - self.x
      math.atan2(dy.asDouble, dx.asDouble)
      // if (self.x > p1.x) {
      //   math.atan2((self.y - p1.y).asDouble, (self.x - p1.x).asDouble)
      // } else {
      //   math.atan2((p1.y - self.y).asDouble, (p1.x - self.x).asDouble)
      // }
    }
    def prettyPrint: String = {
      s"""(${self.x.pp}, ${self.y.pp})"""
    }

  }

  implicit val ptOrd:Ordering[Point] = Ordering.by{ p: Point =>
    (p.x, p.y)
  }

  implicit val lineOrd: Ordering[Line] = Ordering.by{ l:Line =>
    (l.p1, l.p2)
  }

  implicit class RicherLine(val self: Line) extends AnyVal {
    def prettyPrint(): String = {
      val p1 = self.p1.prettyPrint
      val p2 = self.p2.prettyPrint
      s"<$p1->$p2>"
    }

    def rise(): Double = (self.p2.y - self.p1.y).asDouble

    def run(): Double =  (self.p2.x - self.p1.x).asDouble

    def angle(): Double = math.atan2(self.rise, self.run)

    def slope(): Double = (self.rise) / (self.run)

    def length(): Double = {
      math.sqrt(self.run*self.run + self.rise*self.rise)
    }

    def ordered(l2: Line): (Line, Line) = {
      if (lineOrd.compare(self, l2) <= 0) (self, l2)
      else (l2, self)
    }

    def centerPoint: Point = Point(
      ((self.p1.x+self.p2.x) / 2),
      ((self.p1.y+self.p2.y) / 2)
    )

    def sortPointsAsc: Line = {
      val (p1x, p2x) = if (self.p1.x <= self.p2.x) (self.p1.x, self.p2.x) else (self.p2.x, self.p1.x)
      val (p1y, p2y) = if (self.p1.y <= self.p2.y) (self.p1.y, self.p2.y) else (self.p2.y, self.p1.y)

      Line(Point(p1x, p1y), Point(p2x, p2y))
    }

    def bounds(): LTBounds = {
      val nline = sortPointsAsc
      LTBounds(
        nline.p1.x, nline.p1.y,
        nline.p2.x - nline.p1.x,
        nline.p2.y - nline.p1.y
      )
    }

    def clipTo(b: LTBounds): Line = {
      val lnorm = self.sortPointsAsc
      val p1x = max(lnorm.p1.x, b.left)
      val p2x = min(lnorm.p2.x, b.left+b.width)
      val p1y = max(lnorm.p1.y, b.top)
      val p2y = min(lnorm.p2.y, b.left+b.width)
      Line(Point(p1x, p1y), Point(p2x, p2y))
    }

    def extendRightTo(x: Int@@FloatRep): Line = {
      val Line(_, Point(_, y2)) = self
      self.copy(p2=Point(x, y2))
    }

    def extendLeftTo(x: Int@@FloatRep): Line = {
      val Line(Point(x1, y1), _) = self
      self.copy(p1=Point(x, y1))
    }

    def splitVertical(x: Int@@FloatRep): Option[(Line, Line)] = {
      val Line(Point(x1, y1), Point(x2, y2)) = self.sortPointsAsc
      val overlaps = x1 < x && x < x2
      if (overlaps) {
        val left = Line(Point(x1, y1), Point(x, y2))
        val right = Line(Point(x, y1), Point(x2, y2))

        Some((left, right))
      } else None
    }

    def translate(x: Double, y: Double): Line = {
      Line(
        self.p1.translate(x, y),
        self.p2.translate(x, y)
      )
    }

    def translate(x: Int@@FloatRep, y: Int@@FloatRep): Line = {
      Line(
        self.p1.translate(x, y),
        self.p2.translate(x, y)
      )
    }

  }

}


trait GeometricFigureCodecs extends TypeTagCodecs {
  import io.circe
  import circe.generic.semiauto._
  import io.circe._, io.circe.generic.auto._
  import circe.syntax._

  import utils.DoOrDieHandlers._

  implicit val Enc_Int_FloatRep: Encoder[Int@@FloatRep] = Encoder.encodeInt.contramap(_.unwrap)
  implicit val Dec_Int_FloatRep: Decoder[Int@@FloatRep] = Decoder.decodeInt.map(FloatRep(_))

  // val Enc_LTBounds_v1: ObjectEncoder[LTBounds] = deriveEncoder
  val Dec_LTBounds_v1: Decoder[LTBounds] = deriveDecoder

  implicit val Enc_LTBounds: Encoder[LTBounds] = Encoder.instance { bbox =>
    val LTBounds.IntReps(l, t, w, h) = bbox
    List(l, t, w, h).asJson
  }

  val Dec_LTBounds_v2: Decoder[LTBounds] = Decoder.instance { hCursor =>
    val intVals = hCursor.focus.orDie().decodeOrDie[List[Int]]()
    intVals match {
      case List(l, t, w, h) =>
        Right(LTBounds.IntReps(l, t, w, h))

      case _ =>
        Left(DecodingFailure("",List()))
    }
  }

  implicit val Dec_LTBounds = Dec_LTBounds_v1.or(Dec_LTBounds_v2)

  implicit val Enc_LBBounds: ObjectEncoder[LBBounds] = deriveEncoder
  implicit val Dec_LBBounds: Decoder[LBBounds] = deriveDecoder

  implicit val Enc_Point: ObjectEncoder[Point] = deriveEncoder
  implicit val Dec_Point: Decoder[Point] = deriveDecoder

  implicit val Enc_Line: ObjectEncoder[Line] = deriveEncoder
  implicit val Dec_Line: Decoder[Line] = deriveDecoder

  implicit val Enc_Trapezoid: ObjectEncoder[Trapezoid] = deriveEncoder
  implicit val Dec_Trapezoid: Decoder[Trapezoid] = deriveDecoder

  implicit val Enc_GeometricFigure: ObjectEncoder[GeometricFigure] = deriveEncoder
  implicit val Dec_GeometricFigure: Decoder[GeometricFigure] = deriveDecoder

}

object GeometryCodecs extends GeometricFigureCodecs
