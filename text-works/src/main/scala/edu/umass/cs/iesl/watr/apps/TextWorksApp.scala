package edu.umass.cs.iesl.watr
package apps


import corpora._

import ammonite.{ops => fs} // , fs._
import segment.DocumentSegmenter
import segment.DocumentSegmentation
import TypeTags._
import scopt.Read
import shapeless._
import java.nio.{file => nio}
import fs2._
import tracing.VisualTracer
import _root_.io.circe, circe._, circe.syntax._

sealed trait OutputOption

object OutputOption {
  case object VisualStructure extends OutputOption
  case object ReadingStructure extends OutputOption
  case object SuperSubEscaping extends OutputOption

  implicit val OutputOptionRead: Read[OutputOption] =
    Read.reads { _.toLowerCase match {
      case "VisualStructure"        | "vs"  => VisualStructure
      case "ReadingStructure"       | "rs"  => ReadingStructure
      case "SuperSubEscaping"       | "sse" => SuperSubEscaping
      case s       =>
        throw new IllegalArgumentException(s"""'${s}' is not an output option.""")
    }}
}

object TextWorksConfig {
  implicit val NioPath: Read[nio.Path] =
    Read.reads { v =>
      nio.Paths.get(v).toAbsolutePath().normalize()
    }

  case class Config(
    ioConfig        : IOConfig = IOConfig(),
    writeRTrees     : Boolean = false,
    initCorpus      : Option[nio.Path] = None,
    runTraceLogging : Boolean = VisualTracer.tracingEnabled(),
    outputOptions   : List[OutputOption] = List(),
    exec            : Option[(Config) => Unit] = Some((c) => extractText(c))
  )

  val parser = new scopt.OptionParser[Config]("text-works") {
    import scopt._

    override def renderingMode: RenderingMode = RenderingMode.OneColumn

    head("Text Works PDF text extraction, part of the WatrWorks", "0.1")

    note("Run text extraction and analysis on PDFs")

    help("help")

    /// IO Config options
    note("Specify exactly one input mode: corpus|file|file-list \n")


    opt[nio.Path]('c', "corpus") action { (v, conf) =>
      lens[Config].ioConfig.modify(conf) { ioConfig =>
        ioConfig.copy(
          inputMode = Option(InputMode.CorpusFile(v, None))
        )
      }
    } text ("root path of PDF corpus; output will be written to same dir as input")

    opt[nio.Path]('i', "input") action { (v, conf) =>
      lens[Config].ioConfig.inputMode.modify(conf){ m =>
        Option(InputMode.SingleFile(v))
      }
    } text("choose single input PDF")

    opt[nio.Path]('l', "input-list") action { (v, conf) =>
      lens[Config].ioConfig.inputMode.modify(conf){ m =>
        Option(InputMode.ListOfFiles(v))
      }
    } text("process list of input PDFs in specified file.")

    opt[String]("filter") action { (v, conf) =>
      lens[Config].ioConfig.pathFilter.modify(conf){ m =>
        val f = trimQuotes(v)
        Option(f)
      }
    } text("if specified, only files matching regex will be processed")


    def trimQuotes(s: String): String = {
      val t = s.trim
      if (t.length() >= 2) {
        val isQuote = List('"', '\'').contains(t.head)
        if (isQuote && t.head == t.last) {
          t.drop(1).dropRight(1)
        } else s
      } else s
    }

    note("\nOutput file options\n")

    opt[nio.Path]('o', "output-file") action { (v, conf) =>
      lens[Config].ioConfig.outputPath.modify(conf){ m =>
        Some(v)
      }
    } text("""|specify output file. In --corpus mode, ouput will be written to same directory as
              |           input file, otherwise relative to cwd. Use --overwrite to overwrite existing files.""".stripMargin)


    opt[Unit]("write-rtrees") action { (v, conf) =>
      lens[Config].writeRTrees.modify(conf){ m =>
        true
      }
    } text ("write the computed rtrees to disk")

    opt[nio.Path]("init-corpus") action { (v, conf) =>
      val conf1 = lens[Config].initCorpus.modify(conf){ m =>
        Some(v)
      }
      setAction(conf1, initCorpus(_))
    } text ("initialize dir of pdfs to corpus structure")

    opt[Unit]("overwrite") action { (v, conf) =>
      lens[Config].ioConfig.overwrite.modify(conf){ m =>
        true
      }
    } text ("write the computed rtrees to disk")


    note("\nOutput text layout options: \n")

    opt[OutputOption]('p', "layout-option") action { (v, conf) =>
      conf.copy(outputOptions = v :: conf.outputOptions)
    } text("choose layout options for extracted text [VisualStructure|ReadingStructure] [SuperSubEscaping]")


    checkConfig{ c =>
      val validConfig = (
        c.initCorpus.isDefined
          || c.ioConfig.inputMode.nonEmpty)

      if (!validConfig) {
        failure("Invalid input options")
      } else success
    }
  }

  def setAction(conf: Config, action: (Config) => Unit): Config = {
    conf.copy(exec=Option(action))
  }



  def initCorpus(conf: Config): Unit = {
    println("initCorpus")
    conf.initCorpus.foreach { corpusRoot =>
      filesys.Corpus.initCorpus(corpusRoot.toString())
    }
  }
  def extractText(conf: Config): Unit = {
    val ioOpts = new IOOptionParser(conf.ioConfig)

    val fsStream = ioOpts.inputStream()
      .through(pipe.zipWithIndex)
      .evalMap { case (input, i) =>
        Task.delay{

          try {

            input match {
              case InputMode.SingleFile(f) =>
              case InputMode.CorpusFile(_, Some(corpusEntry)) =>

                val stableId = DocumentID(corpusEntry.entryDescriptor)

                println(s"Processing ${stableId}")

                for {
                  pdfEntry <- corpusEntry.getPdfArtifact
                  pdfPath <- pdfEntry.asPath
                  docsegPath <- ioOpts.maybeProcess(corpusEntry, "docseg.json")
                } {

                  val traceLogRoot = if (conf.runTraceLogging) {
                    val traceLogGroup = corpusEntry.ensureArtifactGroup("tracelogs")
                    traceLogGroup.deleteGroupArtifacts()
                    Some(traceLogGroup.rootPath)
                  } else None

                  val rtreeGroup = corpusEntry.ensureArtifactGroup("rtrees")

                  val rtreeOutput = if (conf.writeRTrees) {
                    rtreeGroup.deleteGroupArtifacts()
                    Some(rtreeGroup.rootPath)
                  } else None

                  val ammPath = PathConversions.nioToAmm(docsegPath)

                  TextWorksActions.extractText(stableId, pdfPath, ammPath, rtreeOutput, traceLogRoot)
                }
            }
          } catch {
            case t: Throwable =>
              // println(s"t = ${t}")
              // t.printStackTrace()
              utils.Debugging.printAndSwallow(t)
          }
        }
      }


    fsStream.run.unsafeRun()
  }
}

object TextWorksActions {

  def extractText(
    stableId: String@@DocumentID,
    inputPdf: fs.Path,
    textOutputFile: fs.Path,
    rtreeOutputRoot: Option[fs.Path],
    traceLogRoot: Option[fs.Path]
  ): DocumentSegmentation = {

    println(s"Extracting ${stableId}")

    val zoningApi = new MemDocZoningApi

    val segmenter = DocumentSegmenter.createSegmenter(stableId, inputPdf, zoningApi)

    segmenter.runDocumentSegmentation()

    // val mpageIndex = segmenter.mpageIndex

    // val content = formats.DocumentIO.documentToPlaintext(mpageIndex)

    // write(textOutputFile, content)

    traceLogRoot.foreach { rootPath =>
      println("writing tracelogs")

      val allPageTextGrids = segmenter.pageSegmenters
        .map { pageSegmenter =>
          val textGrid = pageSegmenter.getTextGrid
          val gridJs = textGrid.buildOutput().gridToJson()
            // .getSerialization()

          val pageImageShapes = pageSegmenter.pageImageShapes()

          // val gridJs = serProps.gridToJson()

          Json.obj(
            ("shapes" := pageImageShapes),
            ("textgrid" := gridJs)
          )
        }

      val jsLog = Json.arr(
        Json.obj(
          ("name" := s"Document Text"),
          ("steps" := Json.arr(Json.obj(
            ("desc" := "DocumentText"),
            ("Method" := "DocumentTextGrid"),
            ("pages" := allPageTextGrids)
          )))
        ))

      val gridJsStr = jsLog.noSpaces

      fs.write(rootPath / s"textgrid.json", gridJsStr)

      segmenter.pageSegmenters.foreach { pageSegmenter =>
        val textGrid = pageSegmenter.getTextGrid
        val gridJs = textGrid.buildOutput().gridToJson()

        val pageImageShapes = pageSegmenter.pageImageShapes()

        val pageNum = pageSegmenter.pageIndex.pageNum

        val jsLog = Json.arr(
          Json.obj(
            ("name" := s"Page ${pageNum.unwrap+1} Text"),
            ("steps" := Json.arr(Json.obj(
              ("desc" := "textgrid pg"),
              ("Method" := "TextGrid"),
              ("grid" := gridJs),
              ("shapes" := pageImageShapes)
            )))
          ))

        val gridJsStr = jsLog.noSpaces

        fs.write(rootPath / s"page-${pageNum}-textgrid.json",gridJsStr)
      }

      val allLogs = segmenter.pageSegmenters
        .foldLeft(List[Json]()) {
          case (accum, pageSegmenter) =>
            accum ++ pageSegmenter.emitLogs()
        }

      val jsonLogs = allLogs.asJson
      val pp = circe.Printer.spaces2
      val jsonStr = jsonLogs.pretty(pp)

      fs.write(rootPath / "tracelog.json", jsonStr)
    }

    rtreeOutputRoot.foreach { rtreeRootPath =>
      println("writing rtrees")

      for { pageNum <- segmenter.mpageIndex.getPages } {
        val pageIndex = segmenter.mpageIndex.getPageIndex(pageNum)
        val bytes = pageIndex.saveToBytes()
        val pageIndexPath = rtreeRootPath / pageIndex.rtreeArtifactName
        fs.write(pageIndexPath, bytes)
      }
    }

    segmenter
  }

}

object TextWorks extends App {
  import TextWorksConfig._

  parser.parse(args, Config()).foreach{ config =>
    config.exec.foreach { _.apply(config) }
  }

}
