package edu.umass.cs.iesl.watr
package apps


import scopt._
import java.io.{File => JFile}

sealed trait InputMode

object InputMode {
  case class CorpusInput(corpusRoot: JFile) extends InputMode
  case class PdfFile(f: JFile) extends InputMode
  case class PdfFileList(f: JFile) extends InputMode
}

sealed trait OutputMode

object OutputMode {
  case class ToFile(f: JFile) extends OutputMode
  case class ToFileExt(f: String) extends OutputMode
  case class ToDatabase() extends OutputMode
}

// outputFileExtension: String = "wtext.json",
case class IOConfig(
  inputMode: Option[InputMode] = None,
  outputMode: Option[OutputMode] = None,
  force: Boolean = false,
  numToRun: Int = 0,
  numToSkip: Int = 0
)

object IOOptionParser {

  def inputPaths(c: IOConfig): Seq[JFile] = {
    c.inputMode.map{ mode =>
      mode match {
        case InputMode.PdfFile(f) => Seq(f)
        case _ => sys.error("inputPaths(): TODO")
      }
    }.getOrElse {
      sys.error("inputPaths(): Invalid input options")
    }
  }
}
