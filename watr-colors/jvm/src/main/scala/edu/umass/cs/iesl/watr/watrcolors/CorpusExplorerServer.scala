package edu.umass.cs.iesl.watr
package watrcolors


import better.files._

object CorpusExplorerServer extends CorpusExplorerApi  {

  val initpath = file"../../corpus-zero"

  val init = DirectoryCursor.init(initpath).get

  var state: DirectoryCursor = init

  def navNext(): List[HtmlUpdate] = {
    val last = state

    state = state.next

    List(
      HtmlReplaceInner(s"#currfile", s"${state.curr.name}")
    )
  }

  def navPrev(): List[HtmlUpdate] = {
    val last = state

    state = state.prev

    List(
      HtmlReplaceInner(s"#currfile", s"${state.curr.name}")
    )
  }

  def htmlId = "explorer"

  def openCurrent(): List[HtmlUpdate] = {
    // WatrColorServer
    // switch to svg-view
    List(
      // HtmlPrepend(s"#winstack-top", s"${corpusCursor.curr}")
    )
  }

  def createView(): List[HtmlUpdate] = {
    List(
      HtmlReplaceInner("#main", html.CorpusExplorerPane.init().render)
    )
  }

}
