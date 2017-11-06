package edu.umass.cs.iesl.watr
package examples

import textboxing.{TextBoxing => TB}, TB._
import TypeTags._
import corpora._

object ExampleMain extends App {

  val corpus = new SampleTextCorpus()

  corpus.loadSampleDoc(2)
  println(
    corpus.reportDocument(corpus.stableId)
  )

}

class SampleTextCorpus extends PlainTextCorpus {
  override val docStore: DocumentZoningApi = new MemDocZoningApi

  val stableId = DocumentID("stable-id#23")

  def loadSampleDoc(pageCount: Int): Unit = {
    val pages = MockPapers.genericTitle
      .take(pageCount)

    addDocument(stableId, pages)
  }


  def reportDocument(stableId: String@@DocumentID): TB.Box = {
    val docBoxes = for {
      docId <- docStore.getDocument(stableId).toSeq
    } yield {
      val pagesBox = for {
        pageId <- docStore.getPages(docId)
      } yield {
        val pageGeometry = docStore.getPageGeometry(pageId)

        val allTargetRegions = docStore.getTargetRegions(pageId)

        val regionCount =  s"PageRegions for page ${pageId}: ${allTargetRegions.length} ".box

        (
          indent(2)("PageGeometry")
            % indent(4)(pageGeometry.toString.box)
            % indent(2)(regionCount)
            % indent(2)("Page Zones")
        )
      }

      val zoneBoxes = for {
        labelId <- docStore.getZoneLabelsForDocument(docId)
        zoneId <- docStore.getZonesForDocument(docId, labelId)
        textReflow <- docStore.getTextReflowForZone(zoneId)
      } yield {
        (textReflow.toText.box
          % docStore.getZone(zoneId).toString().box)
      }
      (s"Document ${docId} (${stableId}) report"
        % indent(4)(vcat(pagesBox))
        % indent(2)("Zones")
        % indent(4)(vcat(zoneBoxes))
      )
    }
    vcat(docBoxes)
  }


}
