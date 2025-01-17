package edu.umass.cs.iesl.watr
package corpora

import geometry._
import watrmarks._
import textgrid._

import watrmarks.{StandardLabels => LB}


trait DocumentZoningApi {
  val Rel = RelationModel

  def getDocuments(n: Int=Int.MaxValue, skip: Int=0): Seq[String@@DocumentID]
  def getDocumentCount(): Int
  def addDocument(stableId: String@@DocumentID): Int@@DocumentID
  def getDocument(stableId: String@@DocumentID): Option[Int@@DocumentID]
  def getDocumentStableId(docId: Int@@DocumentID): String@@DocumentID

  def addPage(docId: Int@@DocumentID, pageNum: Int@@PageNum): Int@@PageID
  def getPage(docId: Int@@DocumentID, pageNum: Int@@PageNum): Option[Int@@PageID]
  def getPageIdentifier(pageId: Int@@PageID): StablePage
  def getPageDef(pageId: Int@@PageID): Option[Rel.Page]
  def getPages(docId: Int@@DocumentID): Seq[Int@@PageID]
  def getPageGeometry(pageId: Int@@PageID): LTBounds
  def setPageGeometry(pageId: Int@@PageID, geom: LTBounds): Unit

}


