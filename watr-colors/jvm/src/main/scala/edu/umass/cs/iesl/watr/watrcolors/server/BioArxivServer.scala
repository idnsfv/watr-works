package edu.umass.cs.iesl.watr
package watrcolors
package server

import scala.concurrent.Future

import corpora._
import geometry._
import labeling._
import docstore._

import utils.{Debugging => Dbg}
import scala.concurrent.ExecutionContext

class BioArxivServer(
  reflowDB: TextReflowDB,
  corpus: Corpus
)(implicit ec: ExecutionContext) extends WatrShellApi {
  var activeLabelWidgetIndex: Option[LabelWidgetIndex] = None

  import bioarxiv._

  def onDrawPath(artifactId: String, path: Seq[Point]): Unit = {
    println(s"onDrawPath: ")
  }

  def fetchDocumentLabeler(
    labelerRequest: LabelerIdentifier
  ): Future[LabelerResponse] = {

    val DocumentLabelerIdentifier(stableId, labelerType, pagination) = labelerRequest

    val hasEntry = corpus.hasEntry(stableId.unwrap)
    println(s"createDocumentLabeler($stableId, ${labelerType}): hasEntry=$hasEntry")
    val docStore = reflowDB.docStore

    val maybeLabeler = for {
      entry <- corpus.entry(stableId.unwrap)
      rec   <- BioArxivOps.getBioarxivJsonArtifact(entry)
    } yield {

      try {
        val labelingPanel = TitleAuthorsLabelers.bioArxivLabeler(stableId, pagination, rec, docStore)

        val mkPanel: LabelerOptions => LabelingPanel =
          labelerOptions => TitleAuthorsLabelers.bioArxivLabeler(stableId, pagination, rec, docStore)



        val lwIndex = LabelWidgetIndex.create(
          docStore,
          LabelWidgetTransforms.addAllZoneIndicators(
            labelingPanel.labelWidget,
            labelingPanel.labelerOptions,
            docStore
          ),
          mkPanel,
          labelingPanel.labelerOptions
        )

        activeLabelWidgetIndex = Some(lwIndex)

        val layout = lwIndex.layout.positioning

        Future { LabelerResponse(layout, labelingPanel.labelerOptions) }

      } catch {
        case t: Throwable =>
          Dbg.die(t)
          throw t
      }
    }

    maybeLabeler.getOrElse {
      sys.error("createDocumentLabeler: error")
    }

  }

  def uiRequest(r: UIRequest): Future[UIResponse] = {
    try {
      val UIRequest(
        uiState@ UIState(constraint, maybeLabel, selections, currLabeler, pagination),
        gesture
      ) = r

      activeLabelWidgetIndex.map { lwIndex =>
        println(s"got UIRequest ${r}")

        val (uiResponse, modifiedWidgetIndex) = lwIndex.userInteraction(uiState, gesture)

        activeLabelWidgetIndex = Some(modifiedWidgetIndex)

        Future{ uiResponse }
      } getOrElse {
        Future{ UIResponse(uiState, List()) }
      }
    } catch {
      case t: Throwable =>
        Dbg.die(t)
        throw t
    }
  }
}
