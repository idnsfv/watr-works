package edu.umass.cs.iesl.watr
package textgrid

import textboxing.{TextBoxing => TB}, TB._
import scalaz.{@@ => _, _}, Scalaz._

import utils.ScalazTreeImplicits._

import watrmarks._

sealed trait TreeNode

object TreeNode {

  case class CellGroup(
    cells: List[TextGrid.GridCell],
    gridRow: Int
  ) extends TreeNode

  case class LabelNode(
    label: Label
  ) extends TreeNode

  case object RootNode extends TreeNode

  implicit val ShowTreeNode = Show.shows[TreeNode]{ treeNode =>
    treeNode match {
      case TreeNode.CellGroup(cells, gridRow) => cells.map(_.char.toString()).mkString
      case TreeNode.LabelNode(l) => l.fqn
      case TreeNode.RootNode => "()"
    }
  }
}

sealed trait LabeledRowElem {
  def labels: List[Label]
  def getRowText: String
}

object LabeledRowElem {

  case class CellGroupRow(
    override val labels: List[Label],
    cells: Seq[TreeNode.CellGroup],
    depthMod: Int = 0
  ) extends LabeledRowElem {
    def getRowText: String = {
      cells.map(_.cells.map(_.char).mkString).mkString
    }

  }

  case class HeadingRow(
    override val labels: List[Label],
    heading: String
  ) extends LabeledRowElem {
    def getRowText: String = heading
  }

}


case class MarginalLabels(
  columns: List[MarginalLabelColumn]
)
case class MarginalLabelColumn(
  gloss: List[Gloss]
)
sealed trait Gloss
object Gloss {
  case class VSpace(len: Int) extends Gloss
  case class LabelGloss(label: Label, len: Int) extends Gloss
}

object TextGridLabelWidget {

  val Indent: Int = 2

  type LabeledLines = List[(List[Label], String)]


  type LabeledRows = List[LabeledRowElem]

  type Start = Int
  type Len = Int
  type Attr = (Option[Label], Start, Len)

  implicit val ShowAttr = Show.shows[Attr]{ case(lbl, st, len) =>
    s"${lbl}: (${st}-${st+len})"
  }


  def labelTreeToMarginals(labelTree: Tree[TreeNode], compactMarginals: Boolean): MarginalLabels = {

    def shiftChildren(ch: Stream[Tree[Tree[Attr]]], init: Int) =
      ch.foldLeft(Stream.empty[Tree[Attr]]){
        case (acc, child: Tree[Tree[Attr]]) =>
          val offset = acc.headOption.map { h: Tree[Attr] => h.rootLabel._2+h.rootLabel._3 }.getOrElse(init)
          val adjusted = child.rootLabel.map{ case (nlbl, nst, nlen) => (nlbl, nst+offset, nlen) }
          adjusted #:: acc
      }

    def attrEndIndex(attr: Attr) = {
      val (_, st, len) = attr
      st+len
    }


    def histo(node: TreeNode, children: Stream[Tree[Tree[Attr]]]): Tree[Attr] = {

      node match {
        case TreeNode.RootNode         => Tree.Node((None, 0, 0), shiftChildren(children, 0).reverse)
        case _: TreeNode.CellGroup     => Tree.Leaf((None, 0, 1))
        case TreeNode.LabelNode(label) =>

          val initOffset: Int = if (compactMarginals || children.length==1) 0 else 1
          val shifted = shiftChildren(children, initOffset)
          val endOffset = shifted.headOption.map(_.rootLabel).map(attrEndIndex).getOrElse(0)
          Tree.Node((Some(label), 0, endOffset), shifted.reverse)
      }
    }

    val tree = labelTree.scanr(histo).rootLabel

    val columns = tree.levels.toList.map{ level =>

      val spacers = level.foldLeft(List.empty[(Int, Int)]) { case (acc, (_, st, len)) =>
        val (lastEnd, lastSpace) = acc.headOption.getOrElse( (0, 0) )
        val thisSpace = (st+len, st-lastEnd)
        thisSpace :: acc
      }

      val spaces = spacers.map(_._2).reverse.map(Gloss.VSpace(_))

      val glossColumn = (level zip spaces).toList
        .flatMap { case ((lbl, st, len), space) =>
          val gloss = lbl
            .map{ Gloss.LabelGloss(_, len) }
            .getOrElse { Gloss.VSpace(len) }

          List(space, gloss)
        }

      MarginalLabelColumn(glossColumn)
    }
    MarginalLabels(columns)
  }

  def marginalGlossToTextBlock(marginalLabels: MarginalLabels): TB.Box = {

    val colBoxes = marginalLabels.columns.map{ col =>
      val colPins = col.gloss.map{ _ match {
        case Gloss.VSpace(len) =>
          vspace(len)

        case Gloss.LabelGloss(label, len) =>
          if (len==1) {
            label.fqn(0).toUpper.toString.box
          } else {
            val ch = label.fqn(0).toLower.toString()
            vjoin(ch, vjoins(List.fill(len-2)("║")), "╨")
          }
      }}

      vjoins(colPins)
    }
    borderLeftRight("|", ":")(hcat(top, colBoxes))
  }


  def flattenLabelTreeToLines(labelTree: Tree[TreeNode]): List[LabeledRowElem] = {

    def histo(node: TreeNode, children: Stream[Tree[LabeledRows]]): List[LabeledRowElem] = {
      node match {
        case n: TreeNode.CellGroup =>
          List(LabeledRowElem.CellGroupRow(List(), List(n)))

        case TreeNode.RootNode =>
          children.toList.flatMap { _.rootLabel }

        case n @ TreeNode.LabelNode(label) =>
          val childRowElems: List[LabeledRowElem] = children.toList.flatMap { _.rootLabel }

          val headerList = childRowElems.collect{
            case r @ LabeledRowElem.CellGroupRow(labels, cells, depthMod) =>
              cells.map(_.cells.map(_.char).mkString).mkString
          }

          val localText = headerList.mkString

          val localHeader = LabeledRowElem.HeadingRow(List(label), localText)

          val updatedChildren:List[LabeledRowElem] = childRowElems.map{ _ match {
            case r @ LabeledRowElem.CellGroupRow(labels, cells, depthMod) =>
                r.copy(labels = label::labels)

            case r @ LabeledRowElem.HeadingRow(labels, heading) =>
                r.copy(labels = label::labels)
          }}

          val shouldShiftFirstChild = updatedChildren.headOption.exists { firstChild =>
            firstChild.isInstanceOf[LabeledRowElem.CellGroupRow] && firstChild.getRowText == localText
          }

          val shiftedChildren = if (shouldShiftFirstChild) {
            updatedChildren.headOption.map{head =>
              head.asInstanceOf[LabeledRowElem.CellGroupRow].copy(
                depthMod = -1
              ) :: updatedChildren.tail
            } getOrElse { updatedChildren }
          } else {
            updatedChildren
          }

          val shouldPrependHeader = updatedChildren.headOption.exists { firstChild =>
            firstChild.getRowText != localText
          }

          if (shouldPrependHeader) {
            localHeader :: shiftedChildren
          } else shiftedChildren
      }
    }

    labelTree.scanr(histo).rootLabel
  }


  def textGridToIndentedBox(textGrid: TextGrid): TB.Box = {
    val labelTree = textGridToLabelTree(textGrid)

    val lls = flattenLabelTreeToLines(labelTree)

    val dbg = lls.map { _ match {
      case LabeledRowElem.CellGroupRow(labels, cells, depthMod) =>
        val text = cells.map(_.cells.map(_.char).mkString).mkString
        val depth = Indent * (labels.length+1+depthMod)
        (
          labels.map(_.fqn).mkString(", "),
          indent(
            depth,
            s"`${text}`"
          )
        )
      case LabeledRowElem.HeadingRow(labels, heading) =>
        val text = heading
        val depth = Indent * labels.length
        (
          labels.map(_.fqn).mkString(", "),
          indent(
            depth,
            s"++ ${text}"
          )
        )
    }}

    // val lbox = vjoins(left, dbg.map(_._1.box))
    val rbox = vjoins(left, dbg.map(_._2))

    rbox
  }



  def textGridToLabelTree(textGrid: TextGrid): Tree[TreeNode] = {
    val init = Tree.Node[TreeNode](TreeNode.RootNode, Stream.empty)
    var currLoc = init.loc

    def up(): Unit = {
      currLoc = currLoc.parent.getOrElse(sys.error("no parent found"))
    }

    for { (cell, row, col) <- textGrid.indexedCells() } {
      val pinStack = cell.pins.reverse
      val basePins = pinStack.drop(currLoc.parents.length)

      basePins.takeWhile(p => p.isBegin || p.isUnit)
        .foreach { pin =>
          val n = Tree.Node[TreeNode](TreeNode.LabelNode(pin.label), Stream.empty)
          currLoc = currLoc.insertDownLast(n)
        }

      val maybeAppend = for {
        lastChild <- currLoc.lastChild
      } yield {
        lastChild.getLabel match {
          case prevCell@ TreeNode.CellGroup(cells, prevRow) if prevRow == row =>
            lastChild.modifyLabel { p =>
              TreeNode.CellGroup(cells++List(cell), row): TreeNode
            }
          case _ =>
            currLoc.insertDownLast(
              Tree.Leaf[TreeNode](TreeNode.CellGroup(List(cell), row))
            )
        }
      }

      currLoc = maybeAppend.getOrElse {
        currLoc.insertDownLast(
          Tree.Leaf[TreeNode](TreeNode.CellGroup(List(cell), row))
        )
      }

      up()

      cell.pins.takeWhile(p => p.isLast || p.isUnit)
        .foreach { _ => up()  }
    }

    currLoc.root.toTree
  }


}
