package edu.umass.cs.iesl.watr
package geometry

import utils.ExactFloats._
import utils.{RelativeDirection => Dir}

trait GeometricOps {
  implicit class GeometricOps_RicherLTBounds(val self: LTBounds) {
    def area: Double = (self.width*self.height).asDouble


    // TODO these should return Option[LTBounds] and/or check for empty regions
    def setLeft(left: Int@@FloatRep): LTBounds = {
      self.copy(
        left=left,
        width=self.right-left
      )
    }

    def setRight(right: Int@@FloatRep): LTBounds = {
      self.copy(
        width=right-self.left
      )
    }

    def translate(pvec: Point): LTBounds = {
      translate(pvec.x, pvec.y)
    }

    def translate(x: Int@@FloatRep, y: Int@@FloatRep): LTBounds = {
      self.copy(
        left=self.left+x,
        top=self.top+y
      )
    }

    def translate(x: Double, y: Double): LTBounds = {
      self.copy(
        left=self.left+x,
        top=self.top+y
      )
    }

    def moveTo(x: Int@@FloatRep, y: Int@@FloatRep): LTBounds = {
      self.copy(left=x, top=y)
    }

    def moveToOrigin(): LTBounds = {
      moveTo(FloatRep(0), FloatRep(0))
    }

    def scale(byPercent: Double@@Percent): LTBounds = {
      val scale = byPercent.unwrap/100d
      val w = self.width + self.width*scale
      val h = self.height + self.height*scale
      self.copy(width=w, height=h)
    }


    def splitVertical(x: Int@@FloatRep): (Option[LTBounds], Option[LTBounds]) = {
      val LTBounds(left, top, width, height) = self

      val maybeLeft = if (left < x) {
        val leftWidth = min(x-left, width)
        Some(LTBounds(left, top, leftWidth, height))
      } else None

      val maybeRight = if (x < self.right) {
        val leftWidth = maybeLeft.map(_.width).getOrElse(0.toFloatExact)
        Some(LTBounds(left+leftWidth, top, width-leftWidth, height))
      } else None
      (maybeLeft, maybeRight)
    }

    def getHorizontalSlices(n: Int): Seq[LTBounds] = {
      val LTBounds(_, top, _, height) = self
      val sliceHeight: Int@@FloatRep = height / n

      val slices = (1 until n).map{ i =>
        val sliceAt = top + sliceHeight * i
        splitHorizontal(sliceAt)
      }

      slices.map(_._1.get) :+ slices.last._2.get
    }

    def getHorizontalSlice(y: Int@@FloatRep, h: Int@@FloatRep): Option[LTBounds] = {
      val (_, maybeLower) = splitHorizontal(y)
      maybeLower.flatMap { lowerRect =>
        val (maybeUpper, _) = lowerRect.splitHorizontal(y+h)
        maybeUpper
      }
    }
    def getVerticalSlice(x: Int@@FloatRep, w: Int@@FloatRep): Option[LTBounds] = {
      val (_, maybeRight) = splitVertical(x)
      maybeRight.flatMap { rightRect =>
        val (maybeLeft, _) = rightRect.splitVertical(x+w)
        maybeLeft
      }
    }

    def splitHorizontal(y: Int@@FloatRep): (Option[LTBounds], Option[LTBounds]) = {
      val LTBounds(left, top, width, height) = self


      val maybeTop = if (top < y) {
        val topHeight = min(y-top, height)
        Some(LTBounds(left, top, width, topHeight))
      } else None


      val maybeBottom = if (y < self.bottom) {
        val topHeight = maybeTop.map(_.height).getOrElse(0.toFloatExact)
        Some(LTBounds(left, top+topHeight, width, height-topHeight))
      } else None

      (maybeTop, maybeBottom)
    }

    // def splitHorizontal(y: Int@@FloatRep): Option[(LTBounds, LTBounds)] = {
    //   if (overlapsY(y)) {
    //     val LTBounds(left, top, width, height) = self
    //     val upper = LTBounds(left, top, width, y-top)
    //     val lower = LTBounds(left, y, width, height-upper.height)

    //     Some((upper, lower))
    //   } else None
    // }

    def overlapsX(x: Int@@FloatRep): Boolean = {
      self.left <= x &&  x <= self.right
    }
    def overlapsY(y: Int@@FloatRep): Boolean = {
      self.top <= y && y <= self.bottom
    }

    def intersectsX(x: Int@@FloatRep):Boolean = {
      self.left <= x && x <= self.right
    }


    def union(b: LTBounds): LTBounds = {
      val left   = min(self.left, b.left)
      val top    = min(self.top, b.top)
      val right = max(self.right, b.right)
      val bottom = max(self.bottom, b.bottom)
      LTBounds(
        left, top,
        right-left,
        bottom-top
      )
    }

    def intersection(b: LTBounds): Option[LTBounds] = {
      val left   = max(self.left, b.left)
      val top    = max(self.top, b.top)
      val right = min(self.right, b.right)
      val bottom = min(self.bottom, b.bottom)
      if (left < right && top < bottom) {
        Some(LTBounds(left, top,
            right-left,
            bottom-top
          ))
      } else None
    }

    def intersects(rhs:LTBounds):Boolean = {
      intersection(rhs).isDefined
    }

    def toPoint(dir: Dir): Point ={
      def centerX = (self.left+self.width/2)
      def centerY = (self.top+self.height/2)

      dir match {
        case Dir.Top         => Point(centerX, self.top)
        case Dir.Bottom      => Point(centerX, self.bottom)
        case Dir.Right       => Point(self.right, centerY)
        case Dir.Left        => Point(self.left, centerY)
        case Dir.TopLeft     => Point(self.left, self.top)
        case Dir.BottomLeft  => Point(self.left, self.bottom)
        case Dir.TopRight    => Point(self.right, self.top)
        case Dir.BottomRight => Point(self.right, self.bottom)
        case Dir.Center      => Point(centerX, centerY)
      }
    }

    def toLine(dir: Dir): Line = dir match {
      case Dir.Top         => Line(toPoint(Dir.TopLeft), toPoint(Dir.TopRight))
      case Dir.Bottom      => Line(toPoint(Dir.BottomLeft), toPoint(Dir.BottomRight))
      case Dir.Right       => Line(toPoint(Dir.TopRight), toPoint(Dir.BottomRight))
      case Dir.Left        => Line(toPoint(Dir.TopLeft), toPoint(Dir.BottomLeft))
      case Dir.TopLeft     => ???
      case Dir.BottomLeft  => ???
      case Dir.TopRight    => ???
      case Dir.BottomRight => ???
      case Dir.Center      => ???
    }


    def centerDistanceTo(other: LTBounds): Double = {
      val cx = (self.left+self.width/2).asDouble
      val cy = (self.top+self.height/2).asDouble
      val cx2 = (other.left+other.width/2).asDouble
      val cy2 = (other.top+other.height/2).asDouble

      math.sqrt(
        math.pow((cx-cx2), 2) + math.pow((cy-cy2), 2)
      )
    }

    def hasOverlappingArea(other: LTBounds): Boolean = {
      val overlapLR = (
        self.left < other.right
          && self.right > other.left
      )
      val overlapTB = (
        self.top < other.bottom
          && self.bottom > other.top
      )
      overlapLR && overlapTB
    }

    def hasSharedEdge(other: LTBounds): Boolean = {
      (self.left == other.left
        || self.right == other.right
        || self.top == other.top
        || self.bottom == other.bottom
      )
    }

    def hasNoSharedEdges(other: LTBounds): Boolean = {
      !hasSharedEdge(other)
    }


    def isContainedByVProjection(other: LTBounds): Boolean = {
      self.left >= other.left && self.right <= other.right
    }

    def isContainedByHProjection(other: LTBounds): Boolean = {
      self.top >= other.top && self.bottom <= other.bottom
    }

    def isContainedBy(other: LTBounds): Boolean = {
      isContainedByHProjection(other) && isContainedByVProjection(other)
    }




  }

}
