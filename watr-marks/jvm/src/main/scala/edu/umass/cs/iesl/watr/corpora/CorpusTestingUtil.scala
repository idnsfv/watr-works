package edu.umass.cs.iesl.watr
package corpora

import textboxing.{TextBoxing => TB}, TB._
import TypeTags._

object TextPageSamples {
  val samples = List(
    """|            The Title of the Paper
       |^{a}Faculty of Engineering, Yamagata University, Yonezawa 992-8510, Japan
       |""".stripMargin,

    """|   EXPERIMENTAL
       |1. Sample Preparation and Characterization
       |
       |   The starting material of NaBiO_{3} ? nH2O (Nacalai Tesque
       |Inc.) was placed in a Teflon lined autoclave (70 ml) with
       |LiOH and H2O (30 ml) and was heated at 120–2008C
       |for 4 days.
       |
       |""".stripMargin
  )

}

trait CorpusTestingUtil extends PlainTextCorpus {
  def createEmptyDocumentCorpus(): DocumentCorpus

  var freshDocstore: Option[DocumentCorpus] = None

  def docStore: DocumentCorpus = freshDocstore
    .getOrElse(sys.error("Uninitialized DocumentCorpus; Use FreshDocstore() class"))

  class FreshDocstore(pageCount: Int=0) {
    try {
      freshDocstore = Some(createEmptyDocumentCorpus())
      loadSampleDoc(pageCount)
    } catch {
      case t: Throwable =>
        val message = s"""error: ${t}: ${t.getCause}: ${t.getMessage} """
        println(s"ERROR: ${message}")
        t.printStackTrace()
    }
  }

  val stableId = DocumentID("stable-id#23")

  def loadSampleDoc(pageCount: Int): Unit = {
    val pages = TextPageSamples.samples
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

        val regionBoxes = for {
          regionId <- docStore.getTargetRegions(pageId)
        } yield {
          val targetRegion = docStore.getTargetRegion(regionId)
          // val imageBytes = getTargetRegionImage(regionId)

          "t: ".box + targetRegion.toString.box
        }

        // val pageZoneBoxes = for {
        //   zoneId <- docStore.getZonesForPage(pageId)
        //   textReflow <- docStore.getTextReflowForZone(zoneId)
        // } yield {
        //   docStore.getZone(zoneId).toString().box
        // }
        // val zoneBox = for {
        //   zoneId <- docStore.getZonesForTargetRegion(regionId)
        // } yield {
        //   docStore.getZone(zoneId).toString().box
        // }

        (
          indent(2)("PageGeometry")
            % indent(4)(pageGeometry.toString.box)
            % indent(2)("TargetRegions + zones/regions")
            % indent(4)(vcat(regionBoxes))
            % indent(2)("Page Zones")
            // % indent(4)(vcat(pageZoneBoxes))
        )
      }

      (s"Document ${docId} (${stableId}) report"
         % vcat(pagesBox)
      )
    }
    vcat(docBoxes)
  }


}
