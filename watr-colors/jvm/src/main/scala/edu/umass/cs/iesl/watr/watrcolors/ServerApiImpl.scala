package edu.umass.cs.iesl.watr
package watrcolors

import better.files._
import upickle.Js



trait WatrColorApiServer extends WatrColorApi with ServerState {

  def navNext(): Seq[HtmlUpdate] = {
    // val last = corpusCursor
    // corpusCursor = last.next

    // state.map({})

    Seq(
      // HtmlReplaceInner(s"#currfile", s"${corpusCursor.curr}")
    )
  }

  def navPrev(): Seq[HtmlUpdate] = {
    // val last = corpusCursor
    // corpusCursor = last.prev

    // Seq(
    //   HtmlReplaceInner(s"#currfile", s"${corpusCursor.curr}")
    // )
    Seq()
  }

  // the openCurrent specialized for file cursor
  def openCurrent(): Seq[HtmlUpdate] = {
    Seq(
      // HtmlPrepend(s"#winstack-top", s"${corpusCursor.curr}")
    )
  }

}

trait ServerState {
  import scalaz._
  import Scalaz._


  val initpath = file"../../corpus~/samples-mit"


  val init = Stream[DirectoryCursor](
    DirectoryCursor.init(initpath).get
  )

  var state: Zipper[DirectoryCursor] = init.toZipper.get

}

case class DirectoryCursor(
  curr:File,
  prevs: Seq[File]  = Seq(),
  nexts: Seq[File] = Seq(),
  horizon: Iterator[File] = Iterator()
) {
  val horizonDistance = 10

  def prev: DirectoryCursor = {
    if (prevs.isEmpty)
      this
    else DirectoryCursor(
      curr = prevs.head,
      prevs = prevs.drop(1),
      nexts = curr +: nexts,
      horizon
    )
  }
  def next: DirectoryCursor = {
    val prefetchN = Math.max(horizonDistance-nexts.length, 0)
    val newNexts = nexts ++ horizon.take(prefetchN).toSeq

    if (newNexts.isEmpty)
      this
    else DirectoryCursor(
      curr = newNexts.head,
      prevs = curr +: prevs,
      nexts = newNexts.drop(1),
      horizon
    )
  }

}

object DirectoryCursor {

  // next prevs
  object directoryNav {
    def navNext(dcursor: DirectoryCursor): Seq[HtmlUpdate] = {
      Seq(
        HtmlReplaceInner(s"#currfile", s"${dcursor.curr}")
      )
    }
    def navPrev(dcursor: DirectoryCursor): Seq[HtmlUpdate] = {
      Seq(
        HtmlReplaceInner(s"#currfile", s"${dcursor.curr}")
      )
    }
  }

  def init(f: File): Option[DirectoryCursor] = {

    if (f.isDirectory) {
      val m = f.glob("**/*.pdf")

      if (m.hasNext) {
        Option(DirectoryCursor(curr = m.next(), horizon = m))
      } else {
        None
      }

    } else {
      Option(new DirectoryCursor(curr = f))
      None
    }
  }


}
