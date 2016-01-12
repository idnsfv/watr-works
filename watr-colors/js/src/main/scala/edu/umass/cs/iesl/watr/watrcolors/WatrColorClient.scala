package edu.umass.cs.iesl.watr
package watrcolors

import scala.annotation.tailrec
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import scala.util.Random
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scalatags.JsDom.all._
import upickle.Js
import org.scalajs.jquery.jQuery
import upickle.default._



// object Client extends autowire.Client[Js.Value, Reader, Writer] {
//   override def doCall(req: Request): Future[Js.Value] = {
//     dom.ext.Ajax.post(
//       url = "/api/" + req.path.mkString("/"),
//       data = upickle.json.write(Js.Obj(req.args.toSeq: _*))
//     ).map(_.responseText)
//       .map(upickle.json.read)
//   }

//   def read[Result: Reader](p: Js.Value) = readJs[Result](p)
//   def write[Result: Writer](r: Result) = writeJs(r)
// }

case class Keybindings(
  bindings: List[(String, (MousetrapEvent) => Boolean)]
)


trait ClientView {

  def setKeybindings(kb: Keybindings) = {
    Mousetrap.reset()
    kb.bindings.foreach {
      case (str, fn) =>
        Mousetrap.bind(str, fn, "keypress")
    }
  }

  def applyHtmlUpdates(updates: List[HtmlUpdate]): Unit = {
    updates.foreach {
      _ match {
        case HtmlAppend(css, content) => jQuery(css).append(content)
        case HtmlPrepend(css, content) => jQuery(css).prepend(content)
        case HtmlReplace(css, content) => jQuery(css).replaceWith(content)
        case HtmlReplaceInner(css, content) => jQuery(css).html(content)
        case HtmlRemove(css) => jQuery(css).remove()
      }
    }
  }

  def initKeys: Keybindings

  setKeybindings(initKeys)

  def createView(): Unit

}


@JSExport
object WatrColorClient {

  var currentView: ClientView = null

  def switchViews(v: ClientView): Unit = {
    currentView = v
    currentView.createView()
  }

  // TODO Can't figure out why this main() is getting called twice, so putting this guard here..
  var started = false

  @JSExport
  def main(): Unit = {
    if (!started) {
      started = true

      println("WatrColors Client started")
      switchViews(new CorpusExplorerView())

      // val _ = jQuery(dom.document).ready {() =>
      //   jQuery(".split-pane").splitPane();
      //   jQuery(".split-pane").trigger("resize");
      // }
    }
  }

}
