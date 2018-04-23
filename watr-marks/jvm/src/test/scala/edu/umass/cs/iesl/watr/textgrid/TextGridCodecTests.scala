package edu.umass.cs.iesl.watr
package textgrid

import geometry._

import TypeTags._
import TextGridFunctions._

import _root_.io.circe
import circe.syntax._

import utils.DoOrDieHandlers._
import LabelTreeCodecs._


class TextGridCodecTests extends TextGridSpec {

  val dummyPageRegion = PageRegion(
    StablePage(DocumentID("docX"), PageNum(0)), LTBounds.empty
  )

  def emptyInlineBIO(len: Int): Seq[TextGrid.GridCell] = {
    Array.fill[TextGrid.GridCell](len){
      TextGrid.InsertCell('a', dummyPageRegion)
    }
  }

  "Format of LabelTree serialization" in {
    //  inline bio <--> label tree <--> json rep
    val inlineBio = emptyInlineBIO(10)

    for {
      gridCursor <- GridCursor.init(inlineBio)
      c3         <- gridCursor.move(3)
      c4 = {
        val window = c3.toWindow()
        val w2 = window.widen(3)
        w2.addLabel(FirstName)
        w2.closeWindow()
      }
      c5 <-c4.next
      _ = {
        val window = c5.toWindow()
        val w2 = window.widen(3)
        w2.addLabel(LastName)
        w2.closeWindow()
      }
    } yield ()

    val labelTree = gridCellsToLabelTree(inlineBio)

    println(inlineBio.map(_.showPinsVert()).mkString)

    println(labelTree.drawTree)


    {
      println("Marginal Span Tree -----------------------")
      val labelSpanTree2 = labelTreeToMarginalSpanTree(labelTree)
      println(labelSpanTree2.drawTree)
      println("-----------------------")
    }

    val labelSpanTree = labelTreeToSpanTree(labelTree)

    {
      println("Label Span Tree -----------------------")
      println(labelSpanTree.drawTree)
      println("-----------------------")
    }
    val jsonSpanTreeRep = spanTreeToJson(labelSpanTree)
    println(jsonSpanTreeRep.pretty(JsonPrettyPrinter))

    // Json -> inline BIO

    val ls = LabelSpan(LastName, 2, 4)

    val lt = LabelingTree(
      LabelSpan(FirstName, 0, 10), List(
        LabelingTree(
          LabelSpan(LastName, 2, 4), List()
        )
      )
    )

    val lsjs = ls.asJson
    println(lsjs.noSpaces)
    val ltjs = lt.asJson
    println(ltjs.noSpaces)

    val labelingTree = jsonSpanTreeRep.decodeOrDie[Seq[LabelingTree]]()
    println(labelingTree)
    val inlineBioRT = LabelTreeCodecs.decodeBioLabels(jsonSpanTreeRep)
    println("inline Bio Round Trip")
    val rt = inlineBioRT.map(_.mkString).zipWithIndex.mkString("\n  ", "\n  ", "\n")
    println(rt)

  }

  "Behavior of labeled TextGrid serialization" in {
    val textGrid = makeBishopClarkTextGrid()
    val asJson = textGrid.toJson // .pretty(jsonPrinter)
    val roundTripGrid = TextGrid.fromJson(asJson)
    val rtJson = roundTripGrid.toJson()

    println("BishopClarkTextGrid")
    println(asJson.pretty(JsonPrettyPrinter))
    println(rtJson.pretty(JsonPrettyPrinter))

    // val indentedBlock = textGridToIndentedBox(roundTripGrid)
    // val labelTree = textGridToLabelTree(roundTripGrid)
    // val expMarginals = labelTreeToMarginals(labelTree, compactMarginals=false)
    // val emarginBlock = marginalGlossToTextBlock(expMarginals)
    // val expBlock = emarginBlock + indentedBlock
    // println("post: ")
    // println(expBlock.toString())
    // val cmpare = asJson.toString().mbox besideS rtJson.toString().mbox
    // println("\n\n\n----------------------------------------------")
    // println(cmpare)
    // println("========================================================")
    assert(asJson.toString() === rtJson.toString())
  }
}