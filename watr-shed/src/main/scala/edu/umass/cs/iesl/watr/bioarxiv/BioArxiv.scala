package edu.umass.cs.iesl.watr
package bioarxiv

import ammonite.{ops => fs}, fs._
import corpora.filesys._

import _root_.io.circe, circe._, circe.syntax._
import circe.generic.semiauto._
import circe.{parser => CirceParser}

import utils.DoOrDieHandlers._

import sys.process._

object BioArxiv {

  case class PaperRec(
    doi_link: String,
    pdf_link: String,
    pmid: Option[Long],
    sourceJson: Option[Json]
  )

}

trait BioArxivJsonFormats  {
  import BioArxiv._

  // implicit def optionalFormat[T](implicit jsFmt: Format[T]): Format[Option[T]] =
  //   new Format[Option[T]] {
  //     override def reads(json: JsValue): JsResult[Option[T]] = json match {
  //       case JsNull => JsSuccess(None)
  //       case js     => jsFmt.reads(js).map(Some(_))
  //     }
  //     override def writes(o: Option[T]): JsValue = o match {
  //       case None    => JsNull
  //       case Some(t) => jsFmt.writes(t)
  //     }
  //   }

  implicit def Encode_PaperRec: Encoder[PaperRec] =  deriveEncoder
  implicit def Decode_PaperRec: Decoder[PaperRec] =  deriveDecoder


}


object BioArxivOps extends BioArxivJsonFormats {
  private[this] val log = org.log4s.getLogger
  import BioArxiv._


  def getBioarxivJsonArtifact(corpusEntry: CorpusEntry): Option[PaperRec] = {
    for {
      rec      <- corpusEntry.getArtifact("bioarxiv.json")
      asJson   <- rec.asJson.toOption
    } yield { asJson.decodeOrDie[PaperRec]() }
  }

  def loadPaperRecs(path: Path): Map[String, PaperRec] = {
    // val fis = nio.Files.newInputStream(path.toNIO)
    val jsonStr = _root_.scala.io.Source.fromFile(path.toIO).mkString

    val papers = CirceParser.parse(jsonStr) match {
      case Left(failure) => die(s"Invalid JSON : ${failure}")
      case Right(json) =>
        json.hcursor.values.map { jsons =>
          jsons.map { jsonRec =>
            // println(s"decoding ${jsonRec.spaces2}")
            val paperRec = jsonRec.decodeOrDie[PaperRec]()
            paperRec.copy(sourceJson = Some(jsonRec))
          }
        }.orDie()
    }

    println("bioarxiv json load successful.")

    papers.map{  p=>
      val pathParts = p.doi_link.split("/")
      val key = pathParts.takeRight(2).mkString("-") + ".d"
      (key, p)
    }.toMap
  }

  def createCorpus(corpusRoot: Path, paperRecs: Map[String, PaperRec]): Unit = {
    val corpus = Corpus(corpusRoot)
    corpus.touchSentinel

    for {
      (key, rec) <- paperRecs
    } {
      val entry =  corpus.ensureEntry(key)
      val pjson = rec.sourceJson.orDie("no source json found")
      val jsOut = pjson.spaces2
      val artifact = entry.putArtifact("bioarxiv.json", jsOut)
      val path = artifact.rootPath
      log.info(s"entry $key created in $path")
    }
  }


  def downloadPdfs(corpusRoot: Path): Unit = {
    val corpus = Corpus(corpusRoot)
    println(s"downloading pdf from ${corpus}")
    for {
      entry  <- corpus.entries()
      json   <- entry.getArtifact("bioarxiv.json")
      asJson <- json.asJson
    } {
      val paper = asJson.decodeOrDie[PaperRec]()
      val link = paper.pdf_link
      val pdfName = link.split("/").last

      if (!entry.hasArtifact(pdfName)) {
        try {

          println(s"downloading ${link}")
          val downloadPath =  (entry.artifactsRoot / s"${pdfName}").toNIO.toFile

          val cmd = Seq("curl", "-L", link, "--output", downloadPath.toString())

          val status = cmd.!

          println(s"code ${status} for ${link}")

          // val asdf = new URL(link) #> downloadPath !!

        } catch {
          case t: Throwable =>
            println(s"Error: ${t}")
        }

        println(s"  ...done ")
      } else {
        println(s"already have ${link}")

      }

    }

  }
}

object BioArxivCLI extends App with utils.AppMainBasics {
  import BioArxivOps._
  import utils.PathUtils._

  def run(args: Array[String]): Unit = {
    val argMap = argsToMap(args)

    // val json = argMap.get("json").flatMap(_.headOption)
    //   .getOrElse(sys.error("no bioarxiv json file supplied (--json ...)"))

    val corpusRoot = argMap.get("corpus").flatMap(_.headOption)
      .getOrElse(sys.error("no corpus root  (--corpus ...)"))

    // val paperRecs = loadPaperRecs(json.toPath)
    // val withPmids = paperRecs.filter { case (_, rec) => rec.pmid.isDefined }

    // createCorpus(corpusRoot.toPath, withPmids)

    downloadPdfs(corpusRoot.toPath())


  }

  run(args)
}


// object AlignBioArxiv {
//   import BioArxiv._
//   private[this] val log = org.log4s.getLogger

//   case class AlignmentScores(
//     alignmentLabel: Label
//   ) {
//     val lineScores = mutable.HashMap[Int, Double]()
//     val triScores  = mutable.HashMap[Int, Double]()

//     val lineReflows = mutable.HashMap[Int, TextReflow]()
//     val triReflows = mutable.HashMap[Int, TextReflow]()

//     val consecutiveTriBoosts  = mutable.HashMap[Int, Int]()

//     def boostTrigram(lineInfo: ReflowSliceInfo, triInfo: ReflowSliceInfo): Unit = {
//       val ReflowSliceInfo(linenum, lineReflow, _/*lineText*/) = lineInfo
//       val ReflowSliceInfo(trinum, triReflow, _/*triText*/) = triInfo
//       val triBoostRun = consecutiveTriBoosts.getOrElseUpdate(trinum-1, 0)+1
//       consecutiveTriBoosts.put(trinum, triBoostRun)

//       val lineScore = lineScores.getOrElse(linenum, 1d)
//       val triScore = triScores.getOrElse(trinum, 1d)

//       lineScores.put(linenum, lineScore + triBoostRun)
//       triScores.put(trinum, triScore + triBoostRun)

//       lineReflows.getOrElseUpdate(linenum, lineReflow)
//       triReflows.getOrElseUpdate(trinum, triReflow)
//     }


//     def alignStringToPage(str: String, pageTrigrams: Seq[(ReflowSliceInfo, ReflowSliceInfo)]): Unit = {
//       println(s"aligning ${str}")
//       // // Init lineScores/lineReflows
//       // for ((ReflowSliceInfo(linenum, lineReflow, lineText), _) <- pageTrigrams) {
//       //   lineScores.put(linenum, 0d)
//       //   lineReflows.put(linenum, lineReflow)
//       // }
//       for {
//         tri <- makeTrigrams(str)
//         (lineInfo@ReflowSliceInfo(linenum, lineReflow, lineText), triInfo@ReflowSliceInfo(trinum, triReflow, triText)) <- pageTrigrams
//       } {
//         if (tri == triText) {
//           boostTrigram(lineInfo, triInfo)
//         }
//       }
//     }

//     def report(lineText: Seq[String]): Unit = {
//       println(s"Top candidates:")
//       for {
//         (k, v) <- lineScores.toList.sortBy(_._2).reverse.take(10)
//       } {
//         val text = lineText(k)
//         println(s"score ${v}; ln ${k}>  $text")
//       }
//     }
//   }

//   def makeTrigrams(str: String): Seq[String] = {
//     str.sliding(3).toList
//   }

//   case class ReflowSliceInfo(
//     index: Int,
//     reflow: TextReflow,
//     text: String
//   )


//   def alignPaperWithDB(docStore: DocumentZoningApi, paper: PaperRec, stableId: String@@DocumentID): List[AlignmentScores] = {

//     log.debug("aligning bioarxiv paper")

//     val titleBoosts = new AlignmentScores(LB.Title)
//     val authorBoosts = new AlignmentScores(LB.Authors)
//     val abstractBoosts = new AlignmentScores(LB.Abstract)


//     val page0 = PageNum(0)

//     val lineReflows = for {
//       (vlineZone, linenum) <- docStore.getPageVisualLines(stableId, page0).zipWithIndex
//     } yield {
//       println(s"${vlineZone}")

//       val vlineReflow = docStore.getTextReflowForZone(vlineZone.id)
//       val reflow = vlineReflow.getOrElse { sys.error(s"no text reflow found for line ${linenum}") }
//       val text = reflow.toText
//       println(s"  >>${text}<<")
//       (linenum, reflow, text)
//     }

//     val lineTrisAndText = for {
//       (linenum, vlineReflow, lineText) <- lineReflows
//       // _            = println(s"${linenum}> ${lineText}")
//       lineInfo = ReflowSliceInfo(linenum, vlineReflow, vlineReflow.toText())
//     } yield for {
//       i <- 0 until vlineReflow.length
//       (slice, sliceIndex)       <- vlineReflow.slice(i, i+3).zipWithIndex
//     } yield {
//       val triInfo = ReflowSliceInfo(sliceIndex, slice, slice.toText())
//       (lineInfo, triInfo)
//     }

//     val page0Trigrams = lineTrisAndText.flatten.toList

//     titleBoosts.alignStringToPage(paper.title, page0Trigrams)
//     abstractBoosts.alignStringToPage(paper.`abstract`, page0Trigrams)

//     paper.authors.map(author =>
//       authorBoosts.alignStringToPage(author, page0Trigrams)
//     )

//     println(s"Actual title> ${paper.title}")
//     titleBoosts.report(lineReflows.map(_._3))

//     println(s"""Actual Authors> ${paper.authors.mkString(", ")}""")
//     authorBoosts.report(lineReflows.map(_._3))

//     println("Abstract lines")
//     println(s"""Actual Abstract> ${paper.`abstract`.substring(0, 20)}...""")
//     abstractBoosts.report(lineReflows.map(_._3))

//     List(
//       titleBoosts,
//       authorBoosts,
//       abstractBoosts
//     )
//   }
// }
