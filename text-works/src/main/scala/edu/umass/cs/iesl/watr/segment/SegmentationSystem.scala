package edu.umass.cs.iesl.watr
package segment

import geometry._
import geometry.syntax._
import watrmarks.Label
import corpora.DocumentZoningApi
import rtrees._
import utils.ExactFloats._
import extract._
import utils._
import TypeTags._
import scala.collection.mutable


case class DocSegShape[+T <: GeometricFigure](
  id: Int@@ShapeID,
  shape: T,
  labels: Set[Label],
) extends LabeledShape[T, Unit] {
  def addLabels(l: Label*): DocSegShape[T] = copy(
    labels = this.labels ++ l.toSet
  )

  val attr: Unit = ()
}

object DocSegShape {
  def create[T <: GeometricFigure](
    id: Int@@ShapeID,
    shape: T,
    labels: Label*
  ) = DocSegShape[T](
    id, shape, labels.toSet
  )
}


object SegmentationSystem {

  type ShapeIndex = LabeledShapeIndex[GeometricFigure, Unit, DocSegShape[GeometricFigure]]

  // type LineShape = DocSegShape[Line]
  // type PointShape = DocSegShape[Point]
  // type RectShape = DocSegShape[LTBounds]
  // type AnyShape = DocSegShape[GeometricFigure]

  // implicit class RicherLabeledShapesX[A <: GeometricFigure](val s: Seq[DocSegShape[A]])
  //     extends LabeledShapesCoercion[A, Unit](s)

  // implicit class RicherLabeledShapeX[A <: GeometricFigure](val theShape: DocSegShape[A])
  //     extends LabeledShapeCoercion[A, Unit](theShape)

  implicit class SS_LabeledShapeCoercion[+A <: GeometricFigure](val theShape: DocSegShape[A]) {
    def asLineShape: LineShape = theShape.asInstanceOf[LineShape]
    def asPointShape: PointShape = theShape.asInstanceOf[PointShape]
    def asRectShape: RectShape = theShape.asInstanceOf[RectShape]
  }

  implicit class RicherLabeledShapes[A <: GeometricFigure](val theShapes: Seq[ShapeIndex#AnyShape]) {
    def asLineShapes: Seq[LineShape] = theShapes.asInstanceOf[Seq[LineShape]]
    def asPointShapes: Seq[PointShape] = theShapes.asInstanceOf[Seq[PointShape]]
    def asRectShapes: Seq[RectShape] = theShapes.asInstanceOf[Seq[RectShape]]
  }

  // implicit class RicherLabeledShape[A <: GeometricFigure](val theShape: LabeledShape[A]) {
  //   def asLineShape: LineShape = theShape.asInstanceOf[LineShape]
  //   def asPointShape: PointShape = theShape.asInstanceOf[PointShape]
  //   def asRectShape: RectShape = theShape.asInstanceOf[RectShape]
  // }

}

trait DocumentScopeSegmenter extends DocumentScopeTracing { self =>
  import SegmentationSystem._

  lazy val docScope = self

  def pageAtomsAndGeometry: Seq[(Seq[ExtractedItem], PageGeometry)]

  // All extracted items across all pages, indexed by id, which equals extraction order
  lazy val extractedItemArray: Array[ExtractedItem] = {

    val extractedItemCount = (0 +: pageAtomsAndGeometry.map(_._1.length)).sum
    val itemArray = new Array[ExtractedItem](extractedItemCount+1)
    pageAtomsAndGeometry.foreach { case (items, _) =>
      items.foreach { item => itemArray(item.id.unwrap) = item }
    }
    itemArray
  }

  lazy val shapeIndexes: Map[Int@@PageNum, ShapeIndex] = {
    pageAtomsAndGeometry.map { case (items, pageGeometry) =>
      (pageGeometry.pageNum, LabeledShapeIndex.empty[GeometricFigure, Unit, DocSegShape[GeometricFigure]])
    }.toMap
  }

  def getLabeledShapeIndex(pageNum: Int@@PageNum) = shapeIndexes(pageNum)

  def getPageGeometry(p: Int@@PageNum) = pageAtomsAndGeometry(p.unwrap)._2

  def fontDefs: FontDefs

  def docStore: DocumentZoningApi

  def docStats: DocumentLayoutStats

  def stableId: String@@DocumentID
  def docId: Int@@DocumentID

  def pageSegmenters(): Seq[PageScopeSegmenter]


  def getPagewiseLinewidthTable(): TabularData[Int@@PageNum, String@@ScaledFontID, List[Int@@FloatRep], Unit, Unit] = {
    docScope.docStats.getTable[Int@@PageNum, String@@ScaledFontID, List[Int@@FloatRep]]("PagewiseLineWidths")
  }

  def getFontsWithDocwideOccuranceCounts(): Seq[(String@@ScaledFontID, Int)] = {
    fontDefs.fontProperties.flatMap{ fontProps =>
      if (fontProps.isNatLangFont()) {
        fontProps.getScalingFactors().map{ scalingFactor =>

          val docWideCount = fontProps.totalGlyphOccurrenceCounts
            .computeColMarginals(0)(_ + _)
            .getColMarginal(scalingFactor)
            .getOrElse(0)

          (fontProps.getFontIdentifier(scalingFactor), docWideCount),
        }
      } else List[(String@@ScaledFontID, Int)]()
    }
  }
}

trait PageScopeSegmenter extends PageScopeTracing { self =>
  import SegmentationSystem._
  lazy val pageScope = self

  def docScope: DocumentScopeSegmenter

  def pageId: Int@@PageID
  def pageNum: Int@@PageNum
  def pageStats: PageLayoutStats

  lazy val (pageAtoms, pageGeom) = docScope.pageAtomsAndGeometry(pageNum.unwrap)
  lazy val pageItems: Seq[ExtractedItem] = pageAtoms

  def docStore: DocumentZoningApi = docScope.docStore

  def pageGeometry = pageGeom.bounds
  lazy val shapeIndex: ShapeIndex = docScope.getLabeledShapeIndex(pageNum)


  protected def pageVerticalSlice(left: Double, width: Double): Option[LTBounds] = {
    pageGeometry.getVerticalSlice(left.toFloatExact(), width.toFloatExact())
  }

  protected def pageHorizontalSlice(top: Double, height: Double): Option[LTBounds] = {
    val texact = top.toFloatExact()
    val hexact = height.toFloatExact()
    val t = max(texact, pageGeometry.top)
    val b = min(texact + hexact, pageGeometry.bottom)
    pageGeometry.getHorizontalSlice(t, b-t)
  }

  protected def searchForPoints(query: GeometricFigure, l: Label): Seq[PointShape] = {
    shapeIndex.searchShapes(query, l)
      .map {_.asPointShape}
  }

  protected def searchForLines(query: GeometricFigure, l: Label*): Seq[LineShape] = {
    shapeIndex.searchShapes(query, l:_*)
      .map {_.asLineShape}
  }

  protected def searchForRects(query: GeometricFigure, l: Label*): Seq[RectShape] = {
    shapeIndex.searchShapes(query, l:_*)
      .map {_.asRectShape}
  }


  def getLabeledShapes(l: Label): Seq[AnyShape] = {
    shapeIndex.getShapesWithLabel(l)
  }

  def getLabeledRects(l: Label): Seq[RectShape] = {
    shapeIndex.getShapesWithLabel(l)
      .map(_.asRectShape)
  }

  def getLabeledLines(l: Label): Seq[LineShape] = {
    shapeIndex.getShapesWithLabel(l)
      .map(_.asLineShape)
  }

  def getLabeledPoints(l: Label): Seq[PointShape] = {
    shapeIndex.getShapesWithLabel(l)
      .map(_.asPointShape)
  }

  protected def deleteLabeledShapes(l: Label): Unit = {
    deleteShapes(shapeIndex.getShapesWithLabel(l))
  }

  protected def deleteShapes[T <: GeometricFigure](shapes: Seq[DocSegShape[T]]): Unit = {
    shapes.foreach { sh => shapeIndex.deleteShape(sh) }
  }

  protected def indexShape[T <: GeometricFigure](shape: T, l: Label): DocSegShape[GeometricFigure] = {
    // shapeIndex.indexShape(shape, l)
    shapeIndex.indexShape{ id =>
      DocSegShape.create(id, shape, l)
    }
  }

  protected def indexShapeAndSetItems[T <: GeometricFigure](shape: T, l: Label, items: ExtractedItem*): DocSegShape[GeometricFigure] = {
    val s = shapeIndex.indexShape{ id =>
      DocSegShape.create(id, shape, l)
    }
    setExtractedItemsForShape(s, items.toSeq)
    s
  }

  protected def deleteShape[T <: GeometricFigure](shape: DocSegShape[T]): Unit = {
    shapeIndex.deleteShape(shape)
  }

  protected def reindexShapes(l: Label): Unit = {
    shapeIndex.reindexShapes(l)
  }

  protected def unindexShape[T <: GeometricFigure](shape: DocSegShape[T]): Unit = {
    shapeIndex.unindexShape(shape)
  }
  protected def unindexShapes[T <: GeometricFigure](shapes: Seq[DocSegShape[T]]): Unit = {
    shapes.foreach { sh => shapeIndex.unindexShape(sh) }
  }

  // protected def addRelation(lhs: Int@@ShapeID, l: Label, rhs: Int@@ShapeID): Unit = {
  //   shapeIndex.addRelation(lhs, l, rhs)
  // }

  protected def getClusteredLines(l: Label): Seq[(Int@@ShapeID, Seq[LineShape])] = {
    shapeIndex.getClustersWithReprID(l)
      .map{ case (id, shapes) =>
        (id, shapes.map {_.asLineShape})
      }
  }

  protected def getClusteredRects(l: Label): Seq[(Int@@ShapeID, Seq[RectShape])] = {
    shapeIndex.getClustersWithReprID(l)
      .map{ case (id, shapes) =>
        (id, shapes.map {_.asRectShape})
      }
  }

  protected def cluster1(l: Label, shape: DocSegShape[GeometricFigure]): Unit = {
    shapeIndex.addCluster(l, Seq(shape))
  }

  protected def cluster2(l: Label, shape1: DocSegShape[GeometricFigure], shape2: DocSegShape[GeometricFigure]): Unit = {
    shapeIndex.union(l, shape1, shape2)
  }

  protected def clusterN(l: Label, shapes: Seq[DocSegShape[GeometricFigure]]): Unit = {
    shapeIndex.addCluster(l, shapes)
  }

  protected def setExtractedItemsForShape(shape: DocSegShape[GeometricFigure], items: Seq[ExtractedItem] ): Unit = {
    shapeIndex.setShapeAttribute[Seq[ExtractedItem]](shape.id, LB.ExtractedItems, items)
  }

  protected def getExtractedItemsForShape(shape: DocSegShape[GeometricFigure]): Seq[ExtractedItem] = {
    shapeIndex.getShapeAttribute[Seq[ExtractedItem]](shape.id, LB.ExtractedItems).get
  }

  protected def getExtractedItemsForShapes(shapes: Seq[DocSegShape[GeometricFigure]]): Seq[Seq[ExtractedItem]] = {
    shapes.map { getExtractedItemsForShape(_) }
  }


  def getCharsForShape(shape: DocSegShape[GeometricFigure]): Seq[ExtractedItem.CharItem] = {
    getExtractedItemsForShape(shape)
      .collect{ case i: ExtractedItem.CharItem =>  i }
  }

  protected def setFontsForShape(shape: DocSegShape[GeometricFigure], fontIds: Set[String@@ScaledFontID]): Unit = {
    shapeIndex.setShapeAttribute[Set[String@@ScaledFontID]](shape.id, LB.Fonts, fontIds)
  }


  protected def getFontsForShape(shape: DocSegShape[GeometricFigure]): Set[String@@ScaledFontID] = {
    shapeIndex.getShapeAttribute[Set[String@@ScaledFontID]](shape.id, LB.Fonts).get
  }

  protected def setPrimaryFontForShape(shape: DocSegShape[GeometricFigure], fontId: String@@ScaledFontID): Unit = {
    shapeIndex.setShapeAttribute[String@@ScaledFontID](shape.id, LB.PrimaryFont, fontId)
  }

  protected def getPrimaryFontForShape(shape: DocSegShape[GeometricFigure]): Option[String@@ScaledFontID] = {
    shapeIndex.getShapeAttribute[String@@ScaledFontID](shape.id, LB.PrimaryFont)
  }

  protected def queriesAllEmpty(queryRect: LTBounds, labels: Label*): Boolean = {
    labels.map{ l => searchForRects(queryRect, l).isEmpty }
      .forall(b => b)
  }

  protected def hasNoNonTextOverlaps(queryRect: LTBounds): Boolean = {
    queriesAllEmpty(queryRect, LB.Image, LB.PathBounds)
  }

  protected def hasNoOverlaps(queryRect: LTBounds): Boolean = {
    queriesAllEmpty(queryRect, LB.Image, LB.PathBounds, LB.Glyph)
  }

  protected def findDeltas(ns: Seq[Int@@FloatRep]): Seq[Int@@FloatRep] = {
    ns.zip(ns.tail)
      .map {case (n1, n2) => n2 - n1 }
  }

  protected def findPairwiseVerticalJumps[G <: GeometricFigure](
    shapes: Seq[DocSegShape[G]], getY: (DocSegShape[G]) => Int@@FloatRep
  ): Seq[(Int@@FloatRep, (DocSegShape[G], DocSegShape[G]))] = {

    val sorted = shapes.sortBy { getY(_) }
    val yVals = sorted.map(s => getY(s))
    val deltas = findDeltas(yVals)

    deltas.zip(sorted.zip(sorted.tail))

  }

  protected def pairwiseItemDistances(sortedLineCCs: Seq[PageItem]): Seq[FloatExact] = {
    val cpairs = sortedLineCCs.sliding(2).toList

    val dists = cpairs.map({
      case Seq(c1, c2)  => (c2.bbox.left - c1.bbox.right)
      case _  => 0d.toFloatExact()
    })

    dists :+ 0d.toFloatExact()
  }

}



