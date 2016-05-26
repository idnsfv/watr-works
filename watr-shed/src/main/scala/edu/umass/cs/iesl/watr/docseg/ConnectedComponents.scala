package edu.umass.cs.iesl.watr
package docseg

import watrmarks._
import Bounds._
import scala.collection.JavaConversions._
import scala.collection.mutable
// import pl.edu.icm.cermine.tools.Histogram
import scalaz._
import Scalaz._


import DocstrumSegmenter._


case class CCRenderState(
  numOfPages: Int,
  startingPage: Int@@PageID = PageID(0),
  idgen: IdGenerator[TokenID] = IdGenerator[TokenID],
  tokens:mutable.ArrayBuffer[(Int@@PageID, Int@@TokenID, LTBounds)] = mutable.ArrayBuffer()
) {
  private var currPage: Int@@PageID = startingPage

  def currentPage: Int@@PageID = currPage

  def advancePage(): Int@@PageID = {
    currPage = PageID(PageID.unwrap(currPage)+1)
    currentPage
  }
}

object Component {
  def centerX(cb: CharBox) = cb.bbox.toCenterPoint.x
  def centerY(cb: CharBox) = cb.bbox.toCenterPoint.y

  val LB = StandardLabels

  def apply(charBox: CharBox): ConnectedComponents = {
    new ConnectedComponents(
      Seq(CharComponent(charBox, 0d)),
      0.0d,
      None
    )
  }
  def apply(components: Seq[Component]): ConnectedComponents = {
    new ConnectedComponents(
      components,
      0.0d
    )
  }

  // def apply(components: Seq[Component], orientation: Double, label: Label): ConnectedComponents = {
  def apply(components: Seq[Component], label: Label): ConnectedComponents = {
    new ConnectedComponents(
      components,
      0.0d,
      Option(label)
    )
  }

  def spaceWidths(cs: Seq[CharBox]): Seq[Double] = {
    pairwiseSpaceWidths(cs.map(Component(_)))
  }

  def pairwiseSpaceWidths(cs: Seq[Component]): Seq[Double] = {
    val cpairs = cs.sliding(2).toList

    val dists = cpairs.map({
      case Seq(c1, c2)  => c2.bounds.left - c1.bounds.right
      case _  => 0d
    })

    dists :+ 0d
  }

  def charInfosBox(cbs: Seq[CharBox]): Seq[TB.Box] = {
    import TB._

    cbs.zip(spaceWidths(cbs))
      .map{ case (c, dist) =>
        (tbox(c.char) +| "->" +| (dist.pp)) %
          c.bbox.top.pp %
          (c.bbox.left.pp +| c.bbox.right.pp) %
          (c.bbox.bottom.pp +| "(w:" + c.bbox.width.pp + ")")
    }
  }

  def determineCharSpacings(chars: Seq[CharBox]): Seq[Double] = {
    val dists = spaceWidths(chars)
    val resolution = 0.5d

    val hist = histogram(dists, resolution)

    val spaceDists = hist.iterator.toList
      .sortBy(_.getFrequency)
      .dropWhile(_.getFrequency==0)
      .map(_.getValue)
      .reverse

    spaceDists
  }




  def renderConnectedComponents(_cc: Component)(implicit ostate: Option[CCRenderState] = None): Seq[TB.Box] = {
    import TB._

    _cc match {
      case cc: ConnectedComponents =>
        cc.blockRole.map{ _ match {
          case LB.Line =>
            renderConnectedComponents(cc.tokenizeLine())

          case LB.Page =>
            // should be a set of blocks
            val vs = cc.components.flatMap({ c=>
              renderConnectedComponents(c)
            })
            Seq(vcat(vs))

          case LB.Column =>
            Seq(
              vcat(cc.components.flatMap({ c=>
                renderConnectedComponents(c)
              })))


          case LB.TokenizedLine =>
            // println(s"   ${cc.blockRole}")
            ostate.map{ state =>
              val currPage = state.currentPage

              val vs = cc.components.map({ c=>
                val tokenId = state.idgen.nextId
                state.tokens.append((currPage, tokenId, c.bounds))
                val trend = renderConnectedComponents(c)
                val token = hcat(trend)
                val quoted = "\"".box+token+"\""
                (quoted,  tokenId.toString.box)
              })

              Seq(
                "[[".box + hsepb(vs.map(_._1), ",") +"]" + ",     " + "[" + hsepb(vs.map(_._2), ",") +"]]"
              )

            } getOrElse {
              val vs = cc.components.map({ c=>
                val trend = renderConnectedComponents(c)
                hcat(trend)
              })

              Seq(hsep(vs))
            }


          case LB.Token =>
            // println(s"   ${cc.blockRole}")
            val vs = cc.components.map({c =>
              hcat(renderConnectedComponents(c))
            })

            vs

          case LB.Sup   =>
            // println(s"   ${cc.blockRole}")
            val vs = cc.components.flatMap(c =>
              renderConnectedComponents(c)
            )

            Seq("^{".box + hcat(vs) + "}")

          case LB.Sub   =>
            // println(s"   ${cc.blockRole}")
            val vs = cc.components.flatMap(c =>
              renderConnectedComponents(c)
            )

            Seq("_{".box + hcat(vs) + "}")

          case LB.Block =>
            // println(s"   ${cc.blockRole}")
            val vs = cc.components.map(c =>
              hcat(renderConnectedComponents(c))
            )

            Seq(vjoinTrailSep(left, ",")(vs:_*))

          case LB.Para  => ???
          case LB.Image => ???
          case LB.Table => ???
          case x =>
            println(s"  ??? ${cc.blockRole}")
            val vs = cc.components.flatMap(c =>
              renderConnectedComponents(c)
            )

            Seq(hcat(vs))
        }} getOrElse {
          val vs = cc.components.flatMap(c =>
            renderConnectedComponents(c)
          )

          Seq(hcat(vs))
        }

      case charcomp: CharComponent =>
        Seq(charcomp.component.char.box)
    }

  }

  def debugLineComponentStats(linecc: ConnectedComponents): Unit = {
    // linecc.components.foreach{_ match {
    //   case cc: ConnectedComponents =>
    //     println(s"""    cc: ${cc.toText} ${cc.bounds.prettyPrint} cc.right: ${cc.bounds.right}""")

    //   case cc: CharComponent =>
    //     println(s"""    c:  ${cc.toText} ${cc.bounds.prettyPrint} cc.right: ${cc.bounds.right}""")

    // }}
    val firstCC = linecc.components.head
    linecc.components.sliding(2).foreach{_ match {
      case Seq(c1, c2) =>
        val totalBBox = firstCC.bounds.union(c2.bounds)
        println(s"""| ${c1.toText} - ${c2.toText}
                    |    ${c1.bounds.prettyPrint} - ${c2.bounds.prettyPrint}
                    |    c1.left: ${c1.bounds.left} c1.right: ${c1.bounds.right} c2.left: ${c2.bounds.left}
                    |    dist = ${c2.bounds.left} - ${c1.bounds.right} = ${c2.bounds.left - c1.bounds.right}
                    |    totalBBox = ${totalBBox.prettyPrint}, bb.right:${totalBBox.right.pp}
                    |""".stripMargin)
      case Seq(c1) =>
    }}

  }
}

import Component._


sealed trait Component {
  def chars: String

  def toText(implicit idgen:Option[CCRenderState] = None): String

  def bounds: LTBounds

  def x0: Double
  def y0: Double
  def x1: Double
  def y1: Double
  def height: Double


  def getAngle(): Double = {
    math.atan2(y1 - y0, x1 - x0);
  }

  def getSlope(): Double = {
    (y1 - y0) / (x1 - x0);
  }

  def getLength(): Double = {
    math.sqrt((x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1));
  }


  def angularDifference(j: Component): Double = {
    val diff = Math.abs(getAngle() - j.getAngle());
    if (diff <= Math.PI/2) {
      return diff;
    } else {
      return Math.PI - diff;
    }
  }

  def horizontalDistance(other: Component,  orientation: Double): Double = {
    var xs = Array[Double](0, 0, 0, 0)
    var s = Math.sin(-orientation)
    var c = Math.cos(-orientation);
    xs(0) = c * x0 - s * y0;
    xs(1) = c * x1 - s * y1;
    xs(2) = c * other.x0 - s * other.y0;
    xs(3) = c * other.x1 - s * other.y1;
    var overlapping = xs(1) >= xs(2) && xs(3) >= xs(0);
    xs = xs.sorted
    Math.abs(xs(2) - xs(1)) * (if(overlapping) 1 else -1)
  }

  def verticalDistance(other: Component, orientation: Double): Double = {
    val xm = (x0 + x1) / 2
    val  ym = (y0 + y1) / 2;
    val xn = (other.x0 + other.x1) / 2
    val yn = (other.y0 + other.y1) / 2;
    val a = Math.tan(orientation);
    return Math.abs(a * (xn - xm) + ym - yn) / Math.sqrt(a * a + 1);
  }


  def withLabel(l: Label): Component
  def removeLabel(): Component
  def label: Option[Label]

}


case class CharComponent(
  component: CharBox,
  orientation: Double,
  blockRole: Option[Label] = None
) extends Component {
  val dx = component.bbox.width / 3
  val dy = dx * math.tan(orientation);

  lazy val x0 = centerX(component) - dx;
  lazy val x1 = centerX(component) + dx;
  lazy val y0 = centerY(component) - dy;
  lazy val y1 = centerY(component) + dy;
  val bounds = component.bbox
  def height: Double  = bounds.height

  def toText(implicit idgen:Option[CCRenderState] = None): String = component.char
  def chars: String = toText

  val label: Option[Label] = blockRole
  def withLabel(l: Label): Component = {
    this.copy(blockRole = Option(l))
  }
  def removeLabel(): Component = {
    this.copy(blockRole = None)
  }
}

case class ConnectedComponents(
  components: Seq[Component],
  orientation: Double,
  blockRole: Option[Label] = None
  // label: Label = LB.Line
  // labels: Seq[Label] = Seq()
) extends Component {

  val label: Option[Label] = blockRole
  def withLabel(l: Label): Component = {
    this.copy(blockRole = Option(l))
  }
  def removeLabel(): Component = {
    this.copy(blockRole = None)
  }

  def chars:String = {
    components.map(_.chars).mkString
  }

  def toText(implicit idgen:Option[CCRenderState] = None): String ={
    val ccs = renderConnectedComponents(this)
    TB.hcat(ccs).toString()
  }

  override val (x0, y0, x1, y1) = if (components.length >= 2) {
    val (sx, sxx, sxy, sy) = components
      .foldLeft((0d, 0d, 0d, 0d))({ case ((sx, sxx, sxy, sy), comp) =>
        val c = comp.bounds.toCenterPoint
        (sx + c.x, sxx + c.x*c.x, sxy + c.x*c.y, sy + c.y)
      })


    val b:Double = (components.length * sxy - sx * sy) / (components.length * sxx - sx * sx);
    val a:Double = (sy - b * sx) / components.length;

    val _x0 = components.head.bounds.toCenterPoint.x
    val _x1 = components.last.bounds.toCenterPoint.x
    val _y0 = a + b * _x0;
    val _y1 = a + b * _x1;

    (_x0, _y0, _x1, _y1)

  } else if (!components.isEmpty) {
    val component = components.head;
    val dx = component.bounds.width / 3
    val dy = dx * Math.tan(orientation);

    val _x0 = components.head.bounds.toCenterPoint.x - dx
    val _x1 = components.last.bounds.toCenterPoint.x + dx
    val _y0 = components.head.bounds.toCenterPoint.y - dy
    val _y1 = components.last.bounds.toCenterPoint.y + dy
    (_x0, _y0, _x1, _y1)
  }
  else {
    sys.error("Component list must not be empty")
  }

  val bounds: LTBounds = components.tail
    .map(_.bounds)
    .foldLeft(components.head.bounds)( { case (b1, b2) =>
      b1 union b2
    })

  def height: Double  = bounds.height


  def determineNormalTextBounds: LTBounds = {
    val mfHeights = getMostFrequentValues(components.map(_.bounds.height), 0.1d)
    val mfTops = getMostFrequentValues(components.map(_.bounds.top), 0.1d)

    val mfHeight= mfHeights.headOption.map(_._1).getOrElse(0d)
    val mfTop = mfTops.headOption.map(_._1).getOrElse(0d)

    components
      .map({ c =>
        val cb = c.bounds
        LTBounds(
          left=cb.left, top=mfTop,
          width=cb.width, height=mfHeight
        )
      })
      .foldLeft(components.head.bounds)( { case (b1, b2) =>
        b1 union b2
      })
  }



  // List of avg distances between chars, sorted largest (inter-word) to smallest (intra-word)
  def determineSpacings(): Seq[Double] = {
    val dists = pairwiseSpaceWidths(components)
    val resolution = 0.5d

    val hist = histogram(dists, resolution)

    val spaceDists = hist.iterator.toList
      .sortBy(_.getFrequency)
      // .dropWhile(_.getFrequency==0)
      .map(_.getValue)
      .reverse

    spaceDists
  }

  def findCenterY(): Double = {
    components.map({c => c.bounds.toCenterPoint.y}).sum / components.length
  }

  def findCommonToplines(): Seq[Double] = {
    getMostFrequentValues(
      components.map({c => c.bounds.top}),
      0.001d
    ).toList.map(_._1)
  }

  def findCommonBaselines(): Seq[Double] = {
    getMostFrequentValues(
      components.map({c => c.bounds.bottom}),
      0.001d
    ).toList.map(_._1)
  }

  def splitAtBreaks(bis: Seq[Int], cs: Seq[Component]): Seq[Seq[Component]] = {
    // println(s"""splitAtBreaks: bis=${bis.mkString(",")}""")
    // println(s"""        cs=${cs.map(_.toText).mkString("")}""")
    if (bis.isEmpty){
      Seq(cs)
    } else {
      val (pre, post) = cs.splitAt(bis.head+1)
      // println(s"""        pre=${pre.map(_.toText).mkString("")}""")
      // println(s"""        post=${post.map(_.toText).mkString("")}""")
      pre +: splitAtBreaks(bis.tail.map(_-bis.head-1), post)
    }
  }

  def printCCStats(range: (Int, Int), centerY: Double): Unit = {
    import TB._

    val stats = components.zip(pairwiseSpaceWidths(components))
      .drop(range._1)
      .take(range._2).map({case (c, dist) =>
        (tbox(c.toText) +| "->" +| (dist.pp)) %
          c.bounds.top.pp %
          (c.bounds.left.pp +| c.bounds.right.pp) %
          (c.bounds.bottom.pp +| "(w:" +| c.bounds.width.pp)
      }).toList

    println(
      hsep(stats)
    )
  }

  def angleBasedSuperSubFinding(): Unit = {

    // start w/first modal-top char, search forward, then back
    // val supOrSubs = mutable.ArrayBuffer[Int]()
    // components
    //   .sliding(2).toList
    //   .zipWithIndex
    //   .foreach({
    //     case (Seq(c1), i)  =>

    //     case (Seq(c1, c2), i)  =>
    //       val c1west = c1.bounds.toWesternPoint
    //       val c2east = c2.bounds.toEasternPoint
    //       val c1c2Angle = c1west.angleTo(c2east)
    //       val c1IsAboveC2 = c1c2Angle > 0
    //       if (c1.bounds.top.eqFuzzy(0.01)(modalTop)) {
    //       }
    //       if (c.bounds.top > modalTop) {
    //         c.withLabel(LB.Sub)
    //       } else if (c.bounds.bottom < modalBottom) {
    //         c.withLabel(LB.Sup)
    //       } else {
    //         c
    //       }
    //   })

  }

  def tokenizeLine(): ConnectedComponents = {
    // println("tokenizeLine")
    val tops = findCommonToplines()
    val bottoms = findCommonBaselines()
    val modalTop = tops.head // - 0.01d
    val modalBottom = bottoms.head // + 0.01d

    val modalCenterY = (modalBottom + modalTop)/2
    val meanCenterY = findCenterY()

    // try using angles for super/subs

    val searchLog = mutable.ArrayBuffer[TB.Box]()

    // label super/sub if char.ctr fall above/below centerline
    val supSubs = components.map({c =>
      val cctr = c.bounds.toCenterPoint
      if (cctr.y.eqFuzzy(0.2)(modalCenterY)) {
        c
      } else if (cctr.y > modalCenterY) {
        c.withLabel(LB.Sub)
      } else {
        c.withLabel(LB.Sup)
      }
    })

    def slurpUnlabeled(cs: Seq[Component]): (Seq[Component], Seq[Component]) = {
      val unLabeled = cs.takeWhile({_.label.isEmpty })
      (unLabeled, cs.drop(unLabeled.length))
    }
    def slurpLabels(l: Label, cs: Seq[Component]): (Seq[Component], Seq[Component]) = {
      val withLabel = cs.takeWhile({_.label.exists(_ == l) })
      (withLabel, cs.drop(withLabel.length))
    }

    val unconnected = mutable.ArrayBuffer[Component](supSubs:_*)
    val connectedSupSubs = mutable.ArrayBuffer[Component]()

    while (!unconnected.isEmpty) {
      { val (withL, _) = slurpUnlabeled(unconnected)
        if (!withL.isEmpty) {
          connectedSupSubs ++= withL
          unconnected.remove(0, withL.length)
        } }

      { val (withL, _) = slurpLabels(LB.Sub, unconnected)
        if (!withL.isEmpty) {
          connectedSupSubs += Component(withL.map(_.removeLabel), LB.Sub)
          unconnected.remove(0, withL.length)
        } }

      { val (withL, _) = slurpLabels(LB.Sup, unconnected)
        if (!withL.isEmpty) {
          connectedSupSubs += Component(withL.map(_.removeLabel), LB.Sup)
          unconnected.remove(0, withL.length)
        } }
    }



    val charDists = determineSpacings()
    val modalLittleGap = charDists.head
    val modalBigGap = charDists.drop(1).headOption.getOrElse(modalLittleGap)
    val splitValue = (modalBigGap+modalLittleGap)/2
    val splittable = charDists.length > 1

    // println(s"""|    top char dists: ${charDists.map(_.pp).mkString(", ")}
    //             |    modalTop = ${modalTop} modalBottom = ${modalBottom}
    //             |    modalCenter: ${modalCenterY} meanCenter: ${meanCenterY}
    //             |    modal little gap = ${modalLittleGap} modal big gap = ${modalBigGap}
    //             |    splitValue = ${splitValue}
    //             |""".stripMargin)

    // { import TB._
    //   // println(s"""tops: ${tops.map(_.pp).mkString(" ")}""")
    //   // println(s"""bottoms: ${bottoms.map(_.pp).mkString(" ")}""")
    //   val stats = components.zip(pairwiseSpaceWidths(components))
    //     .map({case (c, dist) =>
    //       (tbox(c.toText) +| "->" +| (dist.pp)) %
    //         c.bounds.top.pp %
    //         (c.bounds.left.pp +| c.bounds.right.pp) %
    //         (c.bounds.bottom.pp +| "(w:" + c.bounds.width.pp) + ")"
    //     }).toList

    //   searchLog.append(
    //     vcat(left)(stats)
    //   )
    // }

    val wordBreaks = mutable.ArrayBuffer[Int]()
    // (suborsub, (start, end))
    // val supSubRanges = mutable.ArrayBuffer[(Int, (Int, Int))]()

    // supSubs
    //   .zip(pairwiseSpaceWidths(supSubs))
    connectedSupSubs
      .zip(pairwiseSpaceWidths(connectedSupSubs))
      .sliding(2).toList
      .zipWithIndex
      .foreach({
        case (Seq((c2, _)), i)  =>
          // single component, no need to append any word breaks

        case (Seq((c1, d1), (c2, _)), i)  =>
          val dist = math.abs(c2.bounds.left - c1.bounds.right)

          if(splittable && d1*1.1 > splitValue) {
            wordBreaks.append(i)
          }

          val angleC1C2 = c1.bounds.toCenterPoint.angleTo(
            c2.bounds.toCenterPoint
          )

          val c1ctr = c1.bounds.toCenterPoint
          val c2ctr = c2.bounds.toCenterPoint

          val c1west = c1.bounds.toWesternPoint
          val c2east = c2.bounds.toEasternPoint
          val c1c2Angle = c1west.angleTo(c2east)


          val c12diff = c2ctr - c1ctr
          val checkAngle = Point(0, 0).angleTo(c12diff)

          // { import TB._
          //   val stats = s"${c1.chars}  -  ${c2.chars}" %
          //   s"    ${c1.bounds.prettyPrint}  -  ${c2.bounds.prettyPrint}" %
          //   s"    pairwisedist: ${d1}  asbdist: ${dist}" %
          //   s"    c1ctr: ${c1ctr.prettyPrint} c2ctr: ${c2ctr.prettyPrint} c12diff: ${c12diff} " %
          //   s"    c1 dist to modal Y (c1ctr.y-modalCenterY):${math.abs(c1ctr.y-modalCenterY)}" %
          //   s"    c1wst: ${c1west.prettyPrint} c2east: ${c2east.prettyPrint} angle: ${c1west.angleTo(c2east)} " %
          //   s"    c1-c2 angle: ${angleC1C2}   checkAngle: ${checkAngle}"

          //   searchLog.append(stats)
          // }
        case _  =>
          sys.error("why are we here? wtf??")
      })

    val asTokens = splitAtBreaks(wordBreaks, connectedSupSubs)
      .map(Component(_, LB.Token))

    // { import TB._
    //   println(
    //       vcat(top)(searchLog.toList)
    //   )
    // }

    Component(asTokens,  LB.TokenizedLine)
  }



  // def convertToBxLine(): ConnectedComponents = {

  //   val cpairs = components.sliding(2).toList

  //   val dists = cpairs.map({
  //     case Seq(c1, c2)  => c2.bounds.left - c1.bounds.right
  //     case _  => 0d
  //   })

  //   val resolution = 0.5d

  //   val histogram = Histogram.fromValues(dists.toList.map(new java.lang.Double(_)), resolution)


  //   val top2Spacings = histogram.iterator.toList.sortBy(_.getFrequency).reverse.take(2)

  //   val splitValue = top2Spacings match {
  //     case sp1 :: sp2 :: Nil =>
  //       // println(s"    top 2 = ${sp1.getValue}, ${sp2.getValue}")
  //       (sp1.getValue+sp2.getValue) / 2
  //     case sp1 :: Nil =>
  //       // println(s"   top 1 = ${sp1.getValue}")
  //       math.abs(sp1.getValue)+1.0d
  //     case _ =>
  //       // println(s"   top = ?")
  //       0d
  //   }
  //   // println(s"   splitval = ${splitValue}")

  //   // val histstr = histogram.iterator().map(x => s"""v=${x.getValue}, fr=${x.getFrequency}""").toList.mkString("\n  ","  \n  ", "\n")
  //   // println(s"""|hist = ${histstr},
  //   //             |  peak = ${histogram.getPeakValue}
  //   //             |""".stripMargin)


  //   val wordBreaks = mutable.ArrayBuffer[Int]()

  //   if(components.length < 2) {
  //     Component(components)
  //   } else {
  //     cpairs.zipWithIndex
  //       .foreach({ case(Seq(c1, c2), i)  =>
  //         val dist = c2.bounds.left - c1.bounds.right

  //         // println(s"""| ${c1.toText} - ${c2.toText}
  //         //             |    ${c1.bounds.prettyPrint} - ${c2.bounds.prettyPrint}
  //         //             |    c1-left: ${c1.bounds.left} c1-right: ${c1.bounds.right} c2-left: ${c2.bounds.left}
  //         //             |    dist = ${c2.bounds.left} - ${c1.bounds.right} = ${c2.bounds.left - c1.bounds.right}
  //         //             |    dist= ${dist.pp} wordSpace=${splitValue}""".stripMargin)
  //         // println(s"""| ${c1.toText} - ${c2.toText}   ${c1.bounds.prettyPrint} - ${c2.bounds.prettyPrint}
  //         //             |     dist=${dist.pp} ws=${splitValue}""".stripMargin)

  //         if(dist > splitValue) {
  //           wordBreaks.append(i)
  //         }
  //       })



  //     val tokenized = splitAtBreaks(wordBreaks, components)

  //     val tccs = tokenized.map({ ts =>
  //       Component(ts, 0d, LB.Word)
  //     })

  //     val lineComp = Component(tccs, 0d, LB.Line)

  //     // val relWordbreaks = wordBreaks.sliding(2).map({
  //     //   case Seq(i) => i
  //     //   case Seq(i, j) => j-i
  //     // })

  //     // println(s"word breaks = ${wordBreaks.mkString(", ")}")
  //     // println(s"relative word breaks = ${relWordbreaks.mkString(", ")}")

  //     // val (ctail, words) = relWordbreaks
  //     //   .foldLeft((components, List[ConnectedComponents]()))({
  //     //     case ((remaining, words), breakIndex) =>
  //     //       println(s"""|remaining: ${remaining.map(_.toText).mkString},
  //     //                   |words: '${words.map(_.toText).mkString(" | ")}',
  //     //                   |break: ${breakIndex}""".stripMargin)

  //     //       val (w0, r0) = remaining.splitAt(breakIndex+1)
  //     //       val word = Component(w0, 0d)

  //     //       (r0, words :+ word)
  //     //   })

  //     // val lastWord = Component(ctail, 0d)
  //     // val finalLine = words :+ lastWord
  //     // val lineComp = Component(finalLine, 0d)

  //     // println(s"line = ${lineComp.toText}")

  //     lineComp

  //   }
  // }


}

// class ComponentLine(
//   val components: Seq[CharBox],
//   val orientation: Double
// ) {

//   var  x0 = 0d
//   var  y0 = 0d
//   var  x1 = 0d
//   var  y1 = 0d
//   var height = 0d


//   if (components.length >= 2) {
//     // Simple linear regression
//     var sx = 0.0d
//     var sxx = 0.0d
//     var sxy = 0.0d
//     var sy = 0.0d

//     for (component <- components) {
//       val x = centerX(component)
//       val y = centerY(component)

//       sx += x // component.getX();
//         sxx += x*x //component.getX() * component.getX();
//         sxy += x*y // component.getX() * component.getY();
//         sy += y //component.getY();
//     }

//     val b:Double = (components.length * sxy - sx * sy) / (components.length * sxx - sx * sx);
//     val a:Double = (sy - b * sx) / components.length;

//     this.x0 = components(0).bbox.toCenterPoint.x
//     this.y0 = a + b * this.x0;
//     this.x1 = centerX(components(components.length - 1))
//     this.y1 = a + b * this.x1;
//   } else if (!components.isEmpty) {
//     val component = components(0);
//     // val dx = component.getChunk().getBounds().getWidth() / 3;
//     val dx = component.bbox.width / 3

//     val dy = dx * Math.tan(orientation);
//     this.x0 = centerX(component) - dx;
//     this.x1 = centerX(component) + dx;
//     this.y0 = centerY(component) - dy;
//     this.y1 = centerY(component) + dy;
//   }
//   else {
//     sys.error("Component list must not be empty")
//   }
//   height = computeHeight();


//   def getAngle(): Double = {
//     return Math.atan2(y1 - y0, x1 - x0);
//   }

//   // public double getSlope() {
//   //     return (y1 - y0) / (x1 - x0);
//   // }

//   def getLength(): Double = {
//       return Math.sqrt((x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1));
//   }

//   def computeHeight(): Double = {
//     var sum = 0.0d
//       for ( component <- components) {
//         sum += component.bbox.height
//       }
//       return sum / components.length;
//   }

//   def getHeight(): Double = {
//     height;
//   }


//   def angularDifference(j: ComponentLine): Double = {
//     val diff = Math.abs(getAngle() - j.getAngle());
//     if (diff <= Math.PI/2) {
//       return diff;
//     } else {
//       return Math.PI - diff;
//     }
//   }

//   def horizontalDistance(other: ComponentLine,  orientation: Double): Double = {
//     var xs = Array[Double](0, 0, 0, 0)
//     var s = Math.sin(-orientation)
//     var c = Math.cos(-orientation);
//     xs(0) = c * x0 - s * y0;
//     xs(1) = c * x1 - s * y1;
//     xs(2) = c * other.x0 - s * other.y0;
//     xs(3) = c * other.x1 - s * other.y1;
//     var overlapping = xs(1) >= xs(2) && xs(3) >= xs(0);
//     xs = xs.sorted
//     Math.abs(xs(2) - xs(1)) * (if(overlapping) 1 else -1)
//   }

//   def verticalDistance(other: ComponentLine, orientation: Double): Double = {
//     val xm = (x0 + x1) / 2
//     val  ym = (y0 + y1) / 2;
//     val xn = (other.x0 + other.x1) / 2
//     val yn = (other.y0 + other.y1) / 2;
//     val a = Math.tan(orientation);
//     return Math.abs(a * (xn - xm) + ym - yn) / Math.sqrt(a * a + 1);
//   }

//   // public BxLine convertToBxLine(double wordSpacing) {
//   //   BxLine line = new BxLine();
//   //   BxWord word = new BxWord();
//   //   Component previousComponent = null;
//   //   for (Component component : components) {
//   //     if (previousComponent != null) {
//   //       double dist = component.getChunk().getBounds().getX() -
//   //         previousComponent.getChunk().getBounds().getX() -
//   //         previousComponent.getChunk().getBounds().getWidth();
//   //       if(dist > wordSpacing) {
//   //         BxBoundsBuilder.setBounds(word);
//   //         line.addWord(word);
//   //         word = new BxWord();
//   //       }
//   //     }
//   //     word.addChunk(component.getChunk());
//   //     previousComponent = component;
//   //   }
//   //   BxBoundsBuilder.setBounds(word);
//   //   line.addWord(word);
//   //   BxBoundsBuilder.setBounds(line);
//   //   return line;
//   // }

//   def convertToBxLine(wordSpacing: Double): ComponentLine = {
//     // BxLine line = new BxLine();
//     // val line = new ComponentLine()

//     // BxWord word = new BxWord();
//     // Component previousComponent = null;
//     // for (Component component : components) {
//     //   if (previousComponent != null) {
//     //     double dist = component.getChunk().getBounds().getX() -
//     //       previousComponent.getChunk().getBounds().getX() -
//     //       previousComponent.getChunk().getBounds().getWidth();
//     //     if(dist > wordSpacing) {
//     //       BxBoundsBuilder.setBounds(word);
//     //       line.addWord(word);
//     //       word = new BxWord();
//     //     }
//     //   }
//     //   word.addChunk(component.getChunk());
//     //   previousComponent = component;
//     // }
//     // BxBoundsBuilder.setBounds(word);
//     // line.addWord(word);
//     // BxBoundsBuilder.setBounds(line);
//     // return line;
//     ???
//   }
// }
// r:0.00 (0.00)  = [1.00, 0.00], atan: 0.00 p1 angleto p2: 0.0
// r:0.30 (0.10)  = [0.96, 0.30], atan: 0.30 p1 angleto p2: 0.29999999999999993
// r:0.60 (0.19)  = [0.83, 0.56], atan: 0.60 p1 angleto p2: 0.6
// r:0.90 (0.29)  = [0.62, 0.78], atan: 0.90 p1 angleto p2: 0.8999999999999999
// r:1.20 (0.38)  = [0.36, 0.93], atan: 1.20 p1 angleto p2: 1.2
// r:1.50 (0.48)  = [0.07, 1.00], atan: 1.50 p1 angleto p2: 1.5
// r:1.80 (0.57)  = [-0.23, 0.97], atan: 1.80 p1 angleto p2: -1.3415926535897933
// r:2.10 (0.67)  = [-0.50, 0.86], atan: 2.10 p1 angleto p2: -1.041592653589793
// r:2.40 (0.76)  = [-0.74, 0.68], atan: 2.40 p1 angleto p2: -0.7415926535897933
// r:2.70 (0.86)  = [-0.90, 0.43], atan: 2.70 p1 angleto p2: -0.4415926535897935
// r:3.00 (0.95)  = [-0.99, 0.14], atan: 3.00 p1 angleto p2: -0.14159265358979367
// r:3.30 (1.05)  = [-0.99, -0.16], atan: -2.98 p1 angleto p2: 0.15840734641020612
// r:3.60 (1.15)  = [-0.90, -0.44], atan: -2.68 p1 angleto p2: 0.458407346410206
// r:3.90 (1.24)  = [-0.73, -0.69], atan: -2.38 p1 angleto p2: 0.7584073464102058
// r:4.20 (1.34)  = [-0.49, -0.87], atan: -2.08 p1 angleto p2: 1.058407346410206
// r:4.50 (1.43)  = [-0.21, -0.98], atan: -1.78 p1 angleto p2: 1.3584073464102058
// r:4.80 (1.53)  = [0.09, -1.00], atan: -1.48 p1 angleto p2: -1.4831853071795875
// r:5.10 (1.62)  = [0.38, -0.93], atan: -1.18 p1 angleto p2: -1.1831853071795877
// r:5.40 (1.72)  = [0.63, -0.77], atan: -0.88 p1 angleto p2: -0.8831853071795879
// r:5.70 (1.81)  = [0.83, -0.55], atan: -0.58 p1 angleto p2: -0.5831853071795882
// r:6.00 (1.91)  = [0.96, -0.28], atan: -0.28 p1 angleto p2: -0.28318530717958823
