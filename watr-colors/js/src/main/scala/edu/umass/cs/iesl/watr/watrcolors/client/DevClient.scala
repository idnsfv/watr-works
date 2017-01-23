package edu.umass.cs.iesl.watr
package watrcolors
package client


import scala.scalajs.js.annotation.JSExport
import textreflow._

trait TextReflowExamples extends PlainTextReflow with LabelerRendering {

  def example1(): TextReflow = {
    stringToTextReflow("""|To be or not to be,
                          |That is the question.
                          |""".stripMargin)(DocumentID("d0"), PageID(0))
  }
}


@JSExport
class DevClient extends TextReflowExamples {

  @JSExport
  def main(): Unit = {
    val widgets = for (i <- 1 to 10) yield {
      example1()
    }

    vcatWidgets(widgets)
  }

}
