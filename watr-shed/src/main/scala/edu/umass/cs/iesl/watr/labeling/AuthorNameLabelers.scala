package edu.umass.cs.iesl.watr
package labeling

import bioarxiv._
import BioArxiv._
import AlignBioArxiv._
import data._
import geometry._
import PageComponentImplicits._
import textreflow.data._
import watrmarks.{StandardLabels => LB}
import textboxing.{TextBoxing => TB}, TB._
import TypeTags._
import data._
import docstore._


object AuthorNameLabelers extends LabelWidgetUtils {
  private[this] val log = org.log4s.getLogger

  def alignAuthors(reflowDB: TextReflowDB, paper: PaperRec, docId: String@@DocumentID): AlignmentScores = {
    log.info(s"aligning authors to bioarxiv paper ${paper.title}")

    val authorBoosts = new AlignmentScores(LB.Authors)

    val page0 = PageNum(0)

    val lineReflows = for {
      (zone, linenum) <- reflowDB.selectZones(docId, page0, LB.VisualLine).zipWithIndex
      region <- zone.regions
    } yield {
      val vlineReflow = reflowDB.getTextReflowForZone(zone)
      val reflow = vlineReflow.getOrElse { sys.error(s"no text reflow found for line ${linenum}") }
      (linenum, reflow, reflow.toText)
    }

    val lineTrisAndText = for {
      (linenum, vlineReflow, lineText) <- lineReflows
      lineInfo = ReflowSliceInfo(linenum, vlineReflow, vlineReflow.toText())
    } yield for {
      i <- 0 until vlineReflow.length
      (slice, sliceIndex)       <- vlineReflow.slice(i, i+3).zipWithIndex
    } yield {
      val triInfo = ReflowSliceInfo(sliceIndex, slice, slice.toText())
      (lineInfo, triInfo)
    }

    val page0Trigrams = lineTrisAndText.flatten.toList


    paper.authors.map(author =>
      authorBoosts.alignStringToPage(author, page0Trigrams)
    )

    authorBoosts
  }

  def nameLabeler(reflowDB: TextReflowDB, docs: Seq[(String@@DocumentID, PaperRec)]): LabelWidget = {
    val highestScoringLines =
      docs.map({case (docId, paperRec) =>
        val scores = alignAuthors(reflowDB, paperRec, docId)
        val bestLine = scores.lineScores
          .toList.sortBy(_._2).reverse
          .headOption.map(_._1)
          .map({linenum =>
            scores.lineReflows(linenum)
          })

        val authorStrings = indent(2)(
          TB.vcat(List(
            "BibTex Authors".box,
            indent(2)(
              TB.vcat(
                paperRec.authors.map(_.box)
              )))))

        bestLine.map(l => (l, authorStrings))
      })

    val nameLines = highestScoringLines.flatten


    val allNames = nameLines.map({ case (namesReflow, authorNames) =>
      val namesText = namesReflow.toText()
      val target = namesReflow.targetRegion()
      val namesTarget = LW.targetOverlay(target, List())

      // Try super/subscript split:
      val splitSuperscripts = labelSplit(namesReflow, LB.Sup)

      println("namesReflow: ")
      println(prettyPrintTree(namesReflow))
      splitSuperscripts
        .foreach({case (name0, maybeLabel) =>
          println(s"name: ${maybeLabel} ")
          println(prettyPrintTree(name0))
        })

      val foundSupscripts = splitSuperscripts
        .filter(_._2.isDefined)
        .length > 1

      val nameReflows = if (foundSupscripts) {

        val labelTargetRegions = splitSuperscripts
          .filter(_._2.isDefined)
          .map(_._1)

        val candidates = labelTargetRegions
          .foldLeft((namesReflow, List[TextReflow]()))({case ((accReflow, splits), elemReflow) =>
            val splitTr = accReflow.targetRegion.splitHorizontal(elemReflow.targetRegion)
            // val local = elemReflow :: (splitTr.map({tr =>
            //   accReflow.clipToTargetRegion(tr).map(_._1).toList
            // })).flatten
            val local =  (splitTr.map({tr =>
              accReflow.clipToTargetRegion(tr).map(_._1).toList
            })).flatten
            (local.last, splits ++ local)
          })

        candidates._2.sortBy(_.targetRegion.bbox.left)

      } else {
        // split names on comma/semicolon
        val regex = if (namesText.contains(';')) {
          ";".r
        } else if (namesText.contains(',')) {
          ",".r
        } else {
          "<do nothing>".r
        }

        val miter = regex.findAllIn(namesText)

        val sliced = miter.matchData.toList
          .foldLeft((0, List[TextReflow]()))({case ((lastIndex, reflows), m) =>
            val slice = namesReflow.slice(lastIndex, m.start)

            (m.end, slice.get :: reflows)
          })

        val (lastSliceEnd, slices) = sliced

        namesReflow
          .slice(lastSliceEnd, namesReflow.length)
          .map(_ :: slices)
          .getOrElse(slices)
          .reverse

      }


      val nameWidgets = nameReflows
        .map({n =>
          LW.pad(
            LW.targetOverlay(n.targetRegion(), List()),
            Padding(left=0, top=0, right=0, bottom=2)
          )
        })

      val nameCol = LW.pad(
        LW.col(nameWidgets:_*),
        Padding(left=4, top=2, right=0, bottom=3)
      )

      val authors = LW.textbox(authorNames)

      val extraction = TB.indent(2)(TB.vcat(List(
        "Extracted Text",
        TB.indent(2)(namesText)
      )))

      LW.panel(
        LW.pad(
          LW.row(
            LW.panel(LW.col(
              namesTarget,
              nameCol
            )),
            LW.panel(LW.col(
              LW.textbox(extraction),
              authors
            ))
          ),
          Padding(left=2, top=0, right=0, bottom=5)
        )
      )

    })

    LW.col(allNames:_*)

  }


}
