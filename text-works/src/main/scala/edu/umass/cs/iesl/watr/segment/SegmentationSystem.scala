package edu.umass.cs.iesl.watr
package segment

import geometry._
import geometry.syntax._
import watrmarks.Label
import corpora.DocumentZoningApi
import spindex._
import utils.ExactFloats._
import extract._
import utils._
import TypeTags._

trait DocumentScopeSegmenter extends DocumentScopeTracing with SegmentationCommons { self =>

  lazy val docScope = self

  def pageAtomsAndGeometry: Seq[(Seq[ExtractedItem], PageGeometry)]

  def fontDefs: FontDefs

  def mpageIndex: MultiPageIndex

  def docStore: DocumentZoningApi

  def docStats: DocumentLayoutStats

  def stableId: String@@DocumentID
  def docId: Int@@DocumentID

  def pageSegmenters(): Seq[PageScopeSegmenter]


  def getPagewiseLinewidthTable(): TabularData[Int@@PageNum, String@@ScaledFontID, List[Int@@FloatRep], Unit, Unit] = {
    docScope.docStats.getTable[Int@@PageNum, String@@ScaledFontID, List[Int@@FloatRep]]("PagewiseLineWidths")
  }

  def getFontsWithOccuranceCounts(): Seq[(String@@ScaledFontID, Int)] = {
    fontDefs.fontProperties.flatMap{ fontProps =>
      if (fontProps.isNatLangFont()) {
        fontProps.getScalingFactors().map{ scalingFactor =>

          val docWideCount = fontProps.totalGlyphOccurrenceCounts
            .computeColMarginals(0)(_ + _)
            .getColMarginal(scalingFactor)
            .getOrElse(0)

          // val pageWideCount = fontProps.totalGlyphOccurrenceCounts.get(pageNum, scalingFactor).getOrElse(0)
          // (fontProps.getFontIdentifier(scalingFactor), pageWideCount)

          (fontProps.getFontIdentifier(scalingFactor), docWideCount),
        }
      } else List[(String@@ScaledFontID, Int)]()
    }
  }



}

trait PageScopeSegmenter extends PageScopeTracing with SegmentationCommons { self =>
  lazy val pageScope = self

  def docScope: DocumentScopeSegmenter

  def pageId: Int@@PageID
  def pageNum: Int@@PageNum
  def pageStats: PageLayoutStats

  def docStore: DocumentZoningApi = docScope.docStore

  def mpageIndex: MultiPageIndex = docScope.mpageIndex
  def pageGeometry = docStore.getPageGeometry(pageId)
  def pageIndex: PageIndex = mpageIndex.getPageIndex(pageNum)

  implicit class RicherLabeledShapes[A <: GeometricFigure](val theShapes: Seq[LabeledShape[A]]) {
    def asLineShapes: Seq[LineShape] = theShapes.asInstanceOf[Seq[LineShape]]
    def asPointShapes: Seq[PointShape] = theShapes.asInstanceOf[Seq[PointShape]]
    def asRectShapes: Seq[RectShape] = theShapes.asInstanceOf[Seq[RectShape]]
  }

  implicit class RicherLabeledShape[A <: GeometricFigure](val theShape: LabeledShape[A]) {
    def asLineShape: LineShape = theShape.asInstanceOf[LineShape]
    def asPointShape: PointShape = theShape.asInstanceOf[PointShape]
    def asRectShape: RectShape = theShape.asInstanceOf[RectShape]
  }

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
    pageIndex.shapes.searchShapes(query, l)
      .map {_.asPointShape}
  }

  protected def searchForLines(query: GeometricFigure, l: Label*): Seq[LineShape] = {
    pageIndex.shapes.searchShapes(query, l:_*)
      .map {_.asLineShape}
  }

  protected def searchForRects(query: GeometricFigure, l: Label*): Seq[RectShape] = {
    pageIndex.shapes.searchShapes(query, l:_*)
      .map {_.asRectShape}
  }


  def getLabeledShapes(l: Label): Seq[AnyShape] = {
    pageIndex.shapes.getShapesWithLabel(l)
  }

  def getLabeledRects(l: Label): Seq[RectShape] = {
    pageIndex.shapes.getShapesWithLabel(l)
      .map(_.asRectShape)
  }

  def getLabeledLines(l: Label): Seq[LineShape] = {
    pageIndex.shapes.getShapesWithLabel(l)
      .map(_.asLineShape)
  }

  def getLabeledPoints(l: Label): Seq[PointShape] = {
    pageIndex.shapes.getShapesWithLabel(l)
      .map(_.asPointShape)
  }

  protected def deleteLabeledShapes(l: Label): Unit = {
    deleteShapes(pageIndex.shapes.getShapesWithLabel(l))
  }

  protected def deleteShapes[T <: GeometricFigure](shapes: Seq[LabeledShape[T]]): Unit = {
    shapes.foreach { sh => pageIndex.shapes.deleteShape(sh) }
  }

  protected def indexShape[T <: GeometricFigure](shape: T, l: Label): LabeledShape[GeometricFigure] = {
    pageIndex.shapes.indexShape(shape, l)
  }

  protected def indexShapeAndSetItems[T <: GeometricFigure](shape: T, l: Label, items: ExtractedItem*): LabeledShape[GeometricFigure] = {
    val s = pageIndex.shapes.indexShape(shape, l)
    setExtractedItemsForShape(s, items.toSeq)
    s
  }

  protected def deleteShape[T <: GeometricFigure](shape: LabeledShape[T]): Unit = {
    pageIndex.shapes.deleteShape(shape)
  }

  protected def reindexShapes(l: Label): Unit = {
    pageIndex.shapes.reindexShapes(l)
  }

  protected def unindexShape[T <: GeometricFigure](shape: LabeledShape[T]): Unit = {
    pageIndex.shapes.unindexShape(shape)
  }
  protected def unindexShapes[T <: GeometricFigure](shapes: Seq[LabeledShape[T]]): Unit = {
    shapes.foreach { sh => pageIndex.shapes.unindexShape(sh) }
  }

  protected def addRelation(lhs: Int@@ShapeID, l: Label, rhs: Int@@ShapeID): Unit = {
    pageIndex.shapes.addRelation(lhs, l, rhs)
  }

  protected def getClusteredLines(l: Label): Seq[(Int@@ShapeID, Seq[LineShape])] = {
    pageIndex.shapes.getClustersWithReprID(l)
      .map{ case (id, shapes) =>
        (id, shapes.map {_.asLineShape})
      }
  }

  protected def getClusteredRects(l: Label): Seq[(Int@@ShapeID, Seq[RectShape])] = {
    pageIndex.shapes.getClustersWithReprID(l)
      .map{ case (id, shapes) =>
        (id, shapes.map {_.asRectShape})
      }
  }

  protected def cluster1(l: Label, shape: LabeledShape[GeometricFigure]): Unit = {
    pageIndex.shapes.addCluster(l, Seq(shape))
  }

  protected def cluster2(l: Label, shape1: LabeledShape[GeometricFigure], shape2: LabeledShape[GeometricFigure]): Unit = {
    pageIndex.shapes.union(l, shape1, shape2)
  }

  protected def clusterN(l: Label, shapes: Seq[LabeledShape[GeometricFigure]]): Unit = {
    pageIndex.shapes.addCluster(l, shapes)
  }

  protected def setExtractedItemsForShape(shape: LabeledShape[GeometricFigure], items: Seq[ExtractedItem] ): Unit = {
    pageIndex.shapes.setShapeAttribute[Seq[ExtractedItem]](shape.id, LB.ExtractedItems, items)
  }

  protected def getExtractedItemsForShape(shape: LabeledShape[GeometricFigure]): Seq[ExtractedItem] = {
    pageIndex.shapes.getShapeAttribute[Seq[ExtractedItem]](shape.id, LB.ExtractedItems).get
  }

  protected def getExtractedItemsForShapes(shapes: Seq[LabeledShape[GeometricFigure]]): Seq[Seq[ExtractedItem]] = {
    shapes.map { getExtractedItemsForShape(_) }
  }


  def getCharsForShape(shape: LabeledShape[GeometricFigure]): Seq[ExtractedItem.CharItem] = {
    getExtractedItemsForShape(shape)
      .collect{ case i: ExtractedItem.CharItem =>  i }
  }

  protected def setFontsForShape(shape: LabeledShape[GeometricFigure], fontIds: Set[String@@ScaledFontID]): Unit = {
    pageIndex.shapes.setShapeAttribute[Set[String@@ScaledFontID]](shape.id, LB.Fonts, fontIds)
  }

  protected def getFontsForShape(shape: LabeledShape[GeometricFigure]): Set[String@@ScaledFontID] = {
    pageIndex.shapes.getShapeAttribute[Set[String@@ScaledFontID]](shape.id, LB.Fonts).get
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
    shapes: Seq[LabeledShape[G]], getY: (LabeledShape[G]) => Int@@FloatRep
  ): Seq[(Int@@FloatRep, (LabeledShape[G], LabeledShape[G]))] = {

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



trait SegmentationCommons {

}
