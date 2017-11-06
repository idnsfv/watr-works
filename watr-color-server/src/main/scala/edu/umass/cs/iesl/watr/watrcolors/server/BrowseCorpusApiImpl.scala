package edu.umass.cs.iesl.watr
package watrcolors
package server

import scala.concurrent.Future
import corpora._
import workflow._
import scala.concurrent.ExecutionContext
import watrmarks.Label
// import watrmarks.{StandardLabels => LB}

class BrowseCorpusApiListeners(
  corpusAccessApi: CorpusAccessApi
)(implicit ec: ExecutionContext) extends BrowseCorpusApi {

  val docStore: DocumentZoningApi = corpusAccessApi.docStore
  val workflowApi: WorkflowApi = corpusAccessApi.workflowApi
  val userbaseApi: UserbaseApi = corpusAccessApi.userbaseApi

  def listDocuments(n: Int, skip: Int, labelFilter: Seq[Label]): Future[Seq[DocumentEntry]] = {
    println(s"listDocuments $n, $skip")

    Future {
      docStore.getDocuments(n, skip, labelFilter)
        .map{ stableId =>

          // println(s"  listDocuments: ${stableId}")

          val zoneToLableTuples: Seq[Seq[(Label, Int)]] = for {
            docId <- docStore.getDocument(stableId).toList
          } yield for {
            labelId <- docStore.getZoneLabelsForDocument(docId)
          } yield {
            // println(s"  listDocuments: labelId:${labelId}")
            val label = docStore.getLabel(labelId)
            // println(s"  listDocuments: label:${label}")
            // val nZones =  docStore.getZonesForDocument(docId, labelId).length
            // println(s"  listDocuments: nZones:${nZones}")
            (label, 1)
          }

          DocumentEntry(stableId, stableId.unwrap, zoneToLableTuples.flatten)
        }
    }
  }

  def documentCount(labelFilter: Seq[Label]): Future[Int] = {
    Future {
      docStore.getDocumentCount(labelFilter)
    }
  }
}
