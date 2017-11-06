package edu.umass.cs.iesl.watr
package textgrid

import scala.collection.mutable
import watrmarks._
import geometry._
import geometry.PageComponentImplicits._
import textboxing.{TextBoxing => TB}, TB._


sealed trait FontInfo

case object NoFonts extends FontInfo


class TextOutputBuilder(textGrid: TextGrid) {




  def withText(): Unit = {
    textGrid.rows.map{ row =>
      row.toText()
    }
  }

  def withLineNumbering(): Unit = {}
  def withMarginLabels(): Unit = {}
  def withTextLocations(): Unit = {}

  def getSerialization(): TextGrid.SerializationProps = {
    val serProps = new TextGrid.SerializationProps

    textGrid.rows.zipWithIndex
      .foreach{ case (row, rowi) =>
        row.serialize(serProps)
      }

    serProps
  }
  def getOutput(): String = {
    val serProps = new TextGrid.SerializationProps

    textGrid.rows.zipWithIndex
      .foreach{ case (row, rowi) =>
        row.serialize(serProps)
      }

    val lineNums = serProps.lineMap.keys.toList.sorted

    val textAndLoci = lineNums.map { lineNum =>
      val text = serProps.lineMap(lineNum)._2
      val loci = serProps.lineMap(lineNum)._1
      (text, loci)
    }

    val textBlock = textAndLoci.zipWithIndex.map{ case ((text, _), i) =>
      val linenum = "%04d".format(i)
      s"${linenum}>  ${text}"
    }
    val lociBlock = textAndLoci.zipWithIndex.map{ case ((_, loci), i) =>
      val linenum = "%04d".format(i)
      s"${linenum}: ${loci}"
    }

    val allLines = textBlock ++ List("##", "##") ++ lociBlock
    allLines.mkString("\n  ", "\n  ", "\n")
    // val textLinesBlock = indent(4)(vjoinTrailSep(left, ",")(textLines:_*))
    // textLinesBlock.toString()
    // textLines.mkString("\n  ", "\n  ", "\n")
  }

}

trait TextGrid {
  import TextGrid._

  def rows: Seq[Row]

  def toText(): String = {
    rows.map(_.toText).mkString("\n  ", "\n  ", "\n")
  }

  def buildOutput() = new TextOutputBuilder(this)

}

object TextGrid {
  type SetType[A] = mutable.Set[A]
  type PinSet = SetType[BioPin]


  sealed trait GridCell {
    def pageRegion: PageRegion

    def char: Char

    val pins: PinSet = mutable.HashSet[BioPin]()

    def fonts: FontInfo = NoFonts

    def labels: SetType[Label] = pins.map(_.label)

    def addPin(p: BioPin): Unit = pins.add(p)

    def addLabel(l: Label): Unit = addPin(l.U)

    def removeLabel(l: Label): Unit = {
      pins.retain(_.label != l)
    }

    def createLeftInsert(ch: Char): InsertCell = LeftInsertCell(ch, this)
    def createRightInsert(ch: Char): InsertCell = RightInsertCell(ch, this)

    def hasLabel(l: Label): Boolean = {
      pins.exists(_.label == l)
    }

    def hasPin(p: BioPin): Boolean = {
      pins.contains(p)
    }

  }


  def mbrRegionFunc(h: PageItem, tail: Seq[PageItem]): PageRegion = {
    (h +: tail).map(_.pageRegion).reduce { _ union _ }
  }

  case class PageItemCell(
    headItem: PageItem,
    tailItems: Seq[PageItem] = Seq(),
    override val char: Char,
    regionFunc: (PageItem, Seq[PageItem]) => PageRegion = mbrRegionFunc(_, _)
  ) extends GridCell {

    override val pageRegion: PageRegion = regionFunc(headItem, tailItems)

    def createLeftExpansion(ch: Char): ExpansionCell = LeftExpansionCell(ch, this)
    def createRightExpansion(ch: Char): ExpansionCell = RightExpansionCell(ch, this)
  }

  abstract class ExpansionCell(
    char: Char,
    root: PageItemCell
  ) extends GridCell {
    override val pageRegion: PageRegion = root.pageRegion
    // TODO override addPin() = root.addPin()
  }

  case class LeftExpansionCell(
    override val char: Char,
    root: PageItemCell
  ) extends ExpansionCell(char, root)

  case class RightExpansionCell(
    override val char: Char,
    root: PageItemCell
  ) extends ExpansionCell(char, root)


  abstract class InsertCell(
    char: Char,
    root: GridCell
  ) extends GridCell {
    override val pageRegion: PageRegion = root.pageRegion
    // TODO override addPin() = root.addPin()
  }

  case class LeftInsertCell(
    override val char: Char,
    root: GridCell
  ) extends InsertCell(char, root)

  case class RightInsertCell(
    override val char: Char,
    root: GridCell
  ) extends InsertCell(char, root)



  trait Row {
    def cells: Seq[GridCell]

    def toCursor(): Option[GridCursor] = {
      GridCursor.init(cells.toList.toZipper)
    }

    def foreach(f: GridCell => Unit): Unit  = {
      cells.foreach(f(_))
    }


    def toText(): String = {
      cells.map(_.char).mkString("")
    }


    import play.api.libs.json, json._
    private def esc(s: String) = Json.stringify(JsString(s))

    def serialize(props: SerializationProps): Unit = {

      val lineCells = cells.map{ _ match {
        case cell@ TextGrid.PageItemCell(headItem, tailItems, char, _) =>
          val items = (headItem +: tailItems).map{ pageItem =>
            val page = pageItem.pageRegion.page
            val pageNum = page.pageNum
            if (!props.pageIdMap.contains(page.pageId)) {
              props.pageIdMap.put(page.pageId, (page.stableId, page.pageNum))
            }
            val l = pageItem.bbox.left
            val b = pageItem.bbox.bottom
            val w = pageItem.bbox.width
            val h = pageItem.bbox.height
            s"""[${pageNum}, [$l, $b, $w, $h], ${esc(char.toString())}]"""
          }

          items.mkString("[", ",", "]")
        case cell@ TextGrid.LeftExpansionCell(char, root)  => '"'+"el"+'"'
        case cell@ TextGrid.RightExpansionCell(char, root) => '"'+"er"+'"'
        case cell@ TextGrid.LeftInsertCell(char, root)     => '"'+"il"+'"'
        case cell@ TextGrid.RightInsertCell(char, root)    => '"'+"ir"+'"'
      }}

      val lineDefStr = lineCells.mkString(",")
      val lineDef = s"""[${lineDefStr}]"""
      val lineNum = props.nextLineNum
      props.lineMap.put(lineNum, (lineDef, toText))
    }

  }

  class SerializationProps {
    val pageIdMap = mutable.Map[Int@@PageID, (String@@DocumentID, Int@@PageNum)]()
    val lineMap = mutable.Map[Int, (String, String)]()

    def nextLineNum: Int = if (lineMap.keySet.isEmpty) 0 else lineMap.keySet.max + 1
  }


  abstract class MutableRow extends Row {
    override val cells: mutable.ArrayBuffer[GridCell] = mutable.ArrayBuffer()
  }


  object Row {
    def fromCells(init: Seq[GridCell]): Row = new MutableRow {
      cells.appendAll(init)
    }
  }


  abstract class MutableTextGrid extends TextGrid {
    override val rows: mutable.ArrayBuffer[Row] = mutable.ArrayBuffer()

  }

  def fromRows(init: Seq[Row]): TextGrid = new MutableTextGrid {
    rows.appendAll(init)
  }

}
