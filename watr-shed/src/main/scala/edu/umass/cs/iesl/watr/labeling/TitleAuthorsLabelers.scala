package edu.umass.cs.iesl.watr
package labeling


import bioarxiv._
import BioArxiv._
import AlignBioArxiv._
import data._
import textreflow.data._
import watrmarks.{StandardLabels => LB}
import textboxing.{TextBoxing => TB}, TB._
import TypeTags._
import data._
import corpora._
import scala.collection.mutable


object TitleAuthorsLabelers extends LabelWidgetUtils {

  def bioArxivLabeler(stableId: String@@DocumentID, paperRec: PaperRec, theDocumentCorpus: DocumentCorpus): LabelingPanel = {

    val paperRecWidget = LW.textbox(
      TB.vjoin()(
        paperRec.title,
        indent(4)(
          TB.vcat(
            paperRec.authors.map(_.box)
          )
        )
      )
    )

    val page0 = PageNum(0)
    val docId = theDocumentCorpus.getDocument(stableId)
      .getOrElse(sys.error(s"Trying to access non-existent document ${stableId}"))

    val pageId = theDocumentCorpus.getPage(docId, page0)
      .getOrElse(sys.error(s"Trying to access non-existent page in doc ${stableId} page 0"))

    val pageGeometry = theDocumentCorpus.getPageGeometry(pageId)
    // .getOrElse(sys.error(s"Trying to access non-existent page geometry in doc ${stableId} page ${page0}"))

    val pageTargetRegionId = theDocumentCorpus.addTargetRegion(pageId, pageGeometry)

    val pageTargetRegion = theDocumentCorpus.getTargetRegion(pageTargetRegionId)

    // val pageTargetRegion = TargetRegion(r0, stableId, page0, pageGeometry)

    val allPageLines = for {
      (zone, linenum) <- theDocumentCorpus.getPageVisualLines(stableId, page0).zipWithIndex
      lineReflow      <- theDocumentCorpus.getTextReflowForZone(zone.id)
    } yield {
      val lt = LW.labeledTarget(lineReflow.targetRegion, None, None)
      (linenum, (0d, lt))
    }

    val scores: Seq[AlignmentScores] = AlignBioArxiv.alignPaperWithDB(theDocumentCorpus, paperRec, stableId)

    // linenum -> best-score, label-widget
    val allLineScores = mutable.HashMap[Int, (Double, LabelWidget)]()
    allLineScores ++= allPageLines

    val overlays = scores.map({alignScores =>
      val label = alignScores.alignmentLabel

      val scoreList = alignScores.lineScores.toList
      val maxScore = scoreList.map(_._2).max
      val lineScores = scoreList.filter(_._2 > maxScore/2)

      lineScores.foreach({case (linenum, score) =>
        val lineReflow = alignScores.lineReflows(linenum)
        val lineBounds = lineReflow.targetRegion()
        val normalScore = score/maxScore

        val lt = LW.labeledTarget(lineBounds, Some(label), Some(normalScore))

        if (allLineScores.contains(linenum)) {
          val Some((currScore, currWidget)) = allLineScores.get(linenum)

          if (normalScore > currScore) {
            allLineScores.put(linenum, (normalScore, lt))
          }

        } else {
          allLineScores.put(linenum, (normalScore, lt))
        }

      })
    })

    val lwidgets = allLineScores.toList
      .sortBy(_._1)
      .map({case (linenum, (score, lwidget)) =>
        lwidget
      })

    val body = LW.row(
      LW.targetOverlay(pageTargetRegion, lwidgets),
      paperRecWidget
    )

    LabelingPanel(
      body,
      LabelOptions(List(
        LB.Title,
        LB.Authors,
        LB.Abstract,
        LB.Affiliation
      ))
    )

  }

}