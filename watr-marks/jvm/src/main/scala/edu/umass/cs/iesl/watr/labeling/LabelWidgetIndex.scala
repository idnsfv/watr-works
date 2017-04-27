package edu.umass.cs.iesl.watr
package labeling

import scala.collection.mutable

import textreflow.data._
import geometry._
// import geometry.zones.syntax._
import geometry.syntax._
import LabelWidgetF._
import corpora._
import rindex._
import watrmarks._
import LabelAction._

import scalaz.Free
import scalaz.~>
import scalaz.State
import shapeless._

import matryoshka._
import matryoshka.implicits._
import utils.GraphPaper
import utils.Colors
import TypeTags._
import LabelWidgetTransforms._

// Provide a caching wrapper around TextReflow + precomputed page bbox
// Only valid for TextReflow that occupy a single Bbox (e.g., VisualLine)
case class IndexableTextReflow(
  id: Int@@TextReflowID,
  textReflow: TextReflow,
  targetRegion: PageRegion
)


case class QueryHit(
  positioned: AbsPosWidget,
  pageId: Int@@PageID,
  pageQueryBounds: LTBounds, // The query
  iTextReflows: Seq[IndexableTextReflow]
)

case class InterpState(
  uiResponse: UIResponse,
  labelWidget: LabelWidget,
  labelWidgetIndex: Option[LabelWidgetIndex] = None
)

object istate {

  val uiResponseL       = lens[InterpState].uiResponse
  val labelWidgetIndexL = lens[InterpState].labelWidgetIndex
  val uiStateL          = lens[InterpState].uiResponse.uiState
  val selectionsL       = lens[InterpState].uiResponse.uiState.selections
  val changesL          = lens[InterpState].uiResponse.changes
  val labelWidgetL      = lens[InterpState].labelWidget


  def addSelection(zoneId: Int@@ZoneID): InterpState => InterpState =
    st => selectionsL.modify(st) {
      sels => zoneId +: sels
    }
}

object LabelWidgetIndex extends LabelWidgetLayout {

  implicit object TextReflowIndexable extends SpatialIndexable[IndexableTextReflow] {
    def id(t: IndexableTextReflow): Int = t.id.unwrap
    def ltBounds(t: IndexableTextReflow): LTBounds = t.targetRegion.bbox
  }

  implicit object LabelWidgetIndexable extends SpatialIndexable[AbsPosWidget] {
    def id(t: AbsPosWidget): Int = t.widget.wid.unwrap
    def ltBounds(t: AbsPosWidget): LTBounds = t.strictBounds
  }


  import textreflow.TextReflowJsonCodecs._

  def init(
    docStore: DocumentCorpus,
    labelerOpts: LabelerOptions=LabelerOptions(Pagination(0, PageNum(0), None), Map()),
    mkWidget: LabelerOptions => LabelingPanel
  ): LabelWidgetIndex = {
    val newPanel = mkWidget(labelerOpts)
    create(docStore, newPanel.labelWidget, mkWidget, labelerOpts, None)
  }

  def create(
    docStore0: DocumentCorpus,
    lwidget: LabelWidget,
    mkWidget0: LabelerOptions => LabelingPanel,
    labelOpts: LabelerOptions=LabelerOptions(Pagination(0, PageNum(0), None), Map()),
    priorIndex: Option[LabelWidgetIndex]=None
  ): LabelWidgetIndex = {
    val lwIndex = SpatialIndex.createFor[AbsPosWidget]()

    val layout0 = layoutWidgetPositions(lwidget)

    layout0.positioning.foreach({pos =>
      lwIndex.add(pos)
    })

    // println(s"      :create(): index add complete")
    val targetPageRIndexes: mutable.HashMap[Int@@PageID, SpatialIndex[IndexableTextReflow]] =
      priorIndex.map{ p =>
        val tmp = mutable.HashMap[Int@@PageID, SpatialIndex[IndexableTextReflow]]()
        tmp ++= p.pageIndexes
        tmp
      }.getOrElse {
        mutable.HashMap[Int@@PageID, SpatialIndex[IndexableTextReflow]]()
      }

    def addPage(targetRegion: TargetRegion): Unit= {
      val pageId = targetRegion.page.pageId
      if (!targetPageRIndexes.contains(pageId)) {
        val pageIndex = SpatialIndex.createFor[IndexableTextReflow]()
        targetPageRIndexes.put(pageId, pageIndex)

        // Put all visual lines into index
        for {
          vline <- docStore0.getPageVisualLines(pageId)
          reflow <- docStore0.getModelTextReflowForZone(vline.id)
        } {
          val textReflow = jsonStrToTextReflow(reflow.reflow)
          val indexable = IndexableTextReflow(
            reflow.prKey,
            textReflow,
            textReflow.targetRegion
          )
          pageIndex.add(indexable)
        }
      }
    }

    println(s"      :create(): begin add pages")
    layout0.positioning.foreach({pos => pos.widget match {

      case l @ RegionOverlay(wid, under, overlays) =>
        addPage(under)

      case _ =>

    }})

    println(s"      :create(): end add pages")


    new LabelWidgetIndex {
      def docStore: DocumentCorpus = docStore0
      def layout: WidgetLayout = layout0
      def mkWidget: LabelerOptions => LabelingPanel = mkWidget0
      def index: SpatialIndex[AbsPosWidget] = lwIndex
      def pageIndexes: Map[Int@@PageID, SpatialIndex[IndexableTextReflow]] = targetPageRIndexes.toMap
      def labelerOptions: LabelerOptions = labelOpts
    }
  }
}


trait LabelWidgetIndex { self =>
  import LabelWidgetIndex._

  def docStore: DocumentCorpus
  def layout: WidgetLayout
  def mkWidget: LabelerOptions => LabelingPanel
  def labelerOptions: LabelerOptions
  def index: SpatialIndex[AbsPosWidget]
  def pageIndexes: Map[Int@@PageID, SpatialIndex[IndexableTextReflow]]

  def update(updatedOptions: LabelerOptions): LabelWidgetIndex = {
    new LabelWidgetIndex {
      def docStore: DocumentCorpus                                         = self.docStore
      def layout: WidgetLayout                                             = self.layout
      def labelerOptions: LabelerOptions                                   = updatedOptions
      def mkWidget: LabelerOptions => LabelingPanel                        = self.mkWidget
      def index: SpatialIndex[AbsPosWidget]                                = self.index
      def pageIndexes: Map[Int@@PageID, SpatialIndex[IndexableTextReflow]] = self.pageIndexes
    }
  }

  def queryForPanels(queryPoint: Point): Seq[(Panel[Unit], AbsPosWidget)] = {
    val queryBox = queryPoint
      .lineTo(queryPoint.translate(1, 1))
      .bounds
    // println(s"queryForPanels: queryPoint = ${queryBox}")


    val ret = index.queryForIntersects(queryBox)
      .map ({ pos => pos.widget match {
        case p : Panel[Unit] => Option { (p, pos) }
        case _ => None
      }})
      .flatten

    // println(s"queryForPanels: found = ${ret.mkString('\n'.toString)}")
    ret
  }

  def queryPage(pos: AbsPosWidget, queryBounds: LTBounds, pageId: Int@@PageID): Option[QueryHit] = {
    pos.strictBounds
      .intersection(queryBounds)
      .map { clippedQueryBox =>
        val pageQueryBounds = clippedQueryBox.translate(pos.translation)
        val pageIndex = pageIndexes(pageId)
        val pageHits = pageIndex.queryForIntersects(pageQueryBounds)
        QueryHit(pos, pageId, pageQueryBounds, pageHits)
      }
  }

  def queryRegion(queryBounds: LTBounds): Seq[QueryHit] = {
    val hits = index
      .queryForIntersects(queryBounds)
      .map { pos => pos.widget match {

        case l @ RegionOverlay(wid, under, overlays) =>
          val pageId = under.page.pageId
          queryPage(pos, queryBounds, pageId)


        case _ => None
      }}

    hits.flatten
  }


  def applyConstraint(constraint: Constraint, queryHits: Seq[QueryHit]): Seq[QueryHit] = {
    for {
      qhit <- queryHits
    } yield constraint match {

      case ByLine   => qhit
      case ByRegion => qhit

      case ByChar =>

        val clippedReflows = for {
          iReflow <- qhit.iTextReflows
        } yield {

          val clipped = iReflow.textReflow
            .clipToBoundingRegion(qhit.pageQueryBounds)
            .map { case (clipped, range) =>
              clipped
            }

          // FIXME: This assumes that clipping the text reflow will result in a single non-empty result
          iReflow.copy(
            textReflow=clipped.head
          )

        }

        qhit.copy(iTextReflows=clippedReflows)

    }

  }

  def labelConstrained(constraint: Constraint, queryHits: Seq[QueryHit], label: Label): Option[Int@@ZoneID] = {

    val pageRegionsToBeLabeled = (for {
      qhit <- queryHits
    } yield constraint match {

      case ByLine =>
        qhit.iTextReflows.map(_.targetRegion)

      case ByRegion =>
        // val regionId = docStore.addTargetRegion(qhit.pageId, qhit.pageSpaceBounds)
        // val newRegion = docStore.getTargetRegion(regionId)
        val pageStableId = docStore.getPageIdentifier(qhit.pageId)
        val pageRegion = PageRegion(pageStableId, qhit.pageQueryBounds)
        Seq(pageRegion)

      case ByChar =>
        qhit.iTextReflows.map(_.textReflow.targetRegion)

    }).flatten

    docStore.labelRegions(label, pageRegionsToBeLabeled)
  }

  def addLabel(queryBounds: LTBounds, constraint: Constraint, label: Label): Option[Int@@ZoneID] = {
    val queryHits = queryRegion(queryBounds)
    val constrainedHits = applyConstraint(constraint, queryHits)
    labelConstrained(constraint, constrainedHits, label)
  }



  def removeNodesWithID(zoneId: Int@@ZoneID, labelWidget: LabelWidget): LabelWidget = {
    LabelWidgetTransforms
      .atEveryId(zoneId, labelWidget, { lw: LabelWidget =>
        LabelWidgets.terminal
      })
  }


  // TODO this interpreter is specific to a particular labeler type (e.g., BioArxiv Labeler) and should be parameterized
  //    within this class
  val interpLabelAction: LabelAction ~> State[InterpState, ?] =
    new (LabelAction ~> State[InterpState, ?]) {

      def apply[A](fa: LabelAction[A]) =  {

        fa match {
          case act@ LabelAction.ToggleZoneSelection(zoneId) =>
            // println(s"ToggleZoneSelection")

            for {
              initSt <- State.get[InterpState]
              initSelections = initSt.uiResponse.uiState.selections

              _ <- State.modify[InterpState] { interpState =>
                if (initSelections.contains(zoneId)) {
                  // println("  ..remove fringe")
                  (istate.selectionsL ~ istate.labelWidgetL).modify(interpState) {
                    case (sels, labelWidget) =>
                      val newSels = sels.filterNot(_ == zoneId)
                      val newWidget = LabelWidgetTransforms.atEveryId(zoneId, labelWidget, { lw: LabelWidget =>
                        lw.project match {
                          case fa@ Figure(wid, fig) =>
                            val GeometricGroup(grbbox, grfigs) = fig
                            LabelWidgets.figure(grfigs.last)

                          case _ => lw
                        }
                      })
                      (newSels, newWidget)
                  }


                } else {
                  // println("  ..add fringe")
                  (istate.selectionsL ~ istate.labelWidgetL).modify(interpState) {
                    case (sels, labelWidget) =>
                      val newWidget = LabelWidgetTransforms.atEveryId(zoneId, labelWidget, { lw: LabelWidget =>
                        lw.project match {
                          case fa@ Figure(wid, fig) =>
                            LabelWidgets.figure(
                              composeFigures(
                                Colorized(
                                  makeFringe(fig, Padding(4)),
                                  fg=Colors.Yellow, bg=Colors.Yellow,
                                  fgOpacity=0.2f, bgOpacity=0.2f
                                ),
                                fig
                              )
                            )

                          case _ => lw
                        }
                      })

                      (zoneId +: sels, newWidget)
                  }

                }
              }
            } yield ()

          case act@ LabelAction.SelectZone(zoneId) =>
            // println(s"SelectZone (disabled)")
            for {
              init <- State.get[InterpState]
            } yield ()

          case act: DeleteZone =>
            // println(s"DeleteZone")
            for {
              newSt <- State.modify[InterpState] { initState =>
                val initSelections =  initState.uiResponse.uiState.selections
                // println(s"DeleteZone:selections: [${initSelections}]")

                val finalLabelWidget = initSelections
                  .foldLeft(initState.labelWidget)({ case (accLabelWidget, zoneId) =>
                    // println(s"   DeleteZone(zoneId)")
                    docStore.deleteZone(zoneId)
                    removeNodesWithID(zoneId, accLabelWidget)
                  })

                // remove zone indicators
                val st0 = istate.selectionsL.set(initState) { Seq() }
                istate.labelWidgetL.set(st0)(finalLabelWidget)
              }
            } yield ()

          case act: MergeZones =>
            // println(s"MergeZone")
            for {
              init <- State.get[InterpState]
            } yield ()

          case act@ NavigateTo(pageNum) =>
            for {
              newSt <- State.modify[InterpState] { initState =>
                val newOptions = labelerOptions.copy(
                  pagination=labelerOptions.pagination.copy(
                    currentPage = PageNum(pageNum)
                  )
                )

                val newPanel = mkWidget(newOptions)
                val newWidget = newPanel.labelWidget

                val newIndex = LabelWidgetIndex.create(docStore, newWidget, mkWidget, newOptions, Some(self))

                val toAdd = newIndex.layout.positioning.toList.map{ pos =>
                  AddLw(pos.widget.wid, Option(pos))
                }

                (istate.changesL ~ istate.labelWidgetIndexL).modify(initState) {
                  case (uiChanges, lwiOpt) =>
                    (ClearAllLw :: toAdd, Some(newIndex))
                }
              }
            } yield ()
        }
      }

    }


  def runLabelAction[A](program: Free[LabelAction, A], uiResponse: UIResponse, widget: LabelWidget): InterpState = {
    program.foldMap(interpLabelAction)
      .apply(InterpState(uiResponse, widget))
      ._1
  }

  implicit class LabelActionOps[A](ma: Free[LabelAction, A]) {
    def exec(uiResponse: UIResponse, widget: LabelWidget): InterpState = runLabelAction(ma, uiResponse, widget)
  }


  def runClickedPanelAction(point: Point, initResponse: UIResponse): (UIResponse, LabelWidget) = {
    queryForPanels(point)
      .foldLeft((initResponse, layout.labelWidget)) {
        case ((accResponse, accWidget), (panel, qhit))  =>

          panel.interaction match {
            case InteractProg(prog) =>

              val run = prog.exec(accResponse, accWidget)

              (run.uiResponse, run.labelWidget)

            case _ =>
              // TODO
              (accResponse, accWidget)
          }
      }

  }


  def minorChangeUpdates(startingWidget: LabelWidget, endingWidget: LabelWidget, uiResponse: UIResponse): (UIResponse, LabelWidgetIndex) = {
    val lwdiff = labelWidgetDiff(startingWidget, endingWidget)

    // println("   ...Diff complete ")
    // println(drawLabelWidgetDiff(lwdiff))
    val mods = labelWidgetDiffToMods(lwdiff)

    // println("   ...Diff -> Mods ")
    val newIndex = LabelWidgetIndex.create(docStore, endingWidget, mkWidget, labelerOptions, Some(self))
    // println("   ...New index created")

    val absPositioned = layout.positioning ++ newIndex.layout.positioning
    val absPosMap = absPositioned.map(a => (a.widget.wid, a)).toMap

    val updates = mods.map { _ match {
      case AddLw(id, _) => AddLw(id, absPosMap.get(id))
      case RmLw(id, _) => RmLw(id, absPosMap.get(id))
      case ClearAllLw => ClearAllLw
    }}
    // println("   ... response constructed")
    // println(updates.mkString("\n  ", "\n  ", "\n"))

    val newResponse = uiResponse.copy(
      changes = updates.toList
    )

    (newResponse, newIndex)
  }


  // TODO this part really needs to be confined to WatrColors front end codebase
  // I think this should be an abstract function, then overridden per labeler type
  // map (UIState, Gesture) => (UIState, UIChanges)
  def userInteraction(uiState: UIState, gesture: Gesture): (UIResponse, LabelWidgetIndex) = {
    val UIState(constraint, maybeLabel, selections, currLabeler, pagination) = uiState
    val initResponse = UIResponse(uiState, List())


    val startingWidget = layout.labelWidget

    val (endingResponse, endingIndex) = gesture match {

      case MenuAction(action) =>
        println(s"MenuAction(${action})")

        val run = LabelAction.lift(action).exec(initResponse, startingWidget)

        run.labelWidgetIndex.map{ lwi =>
          (run.uiResponse, lwi)
        } getOrElse {
          minorChangeUpdates(startingWidget, run.labelWidget, run.uiResponse)
        }

      case Click(point) =>

        val (resp, endingWidget) = runClickedPanelAction(point, initResponse)

        minorChangeUpdates(startingWidget, endingWidget, resp)

      case DblClick(point) =>
        (initResponse, self)

      case SelectRegion(bbox) =>
        val resp = for {
          label <- maybeLabel
          //   println(s"adding label to bbox ${bbox}")
          zoneId <- addLabel(bbox, constraint, label)
        } yield {
          val newWidget = addZoneIndicator(zoneId, layout.labelWidget, labelerOptions, docStore)
          minorChangeUpdates(startingWidget, newWidget, initResponse)
        }

        resp.getOrElse {
          (initResponse, self)
        }

    }


    (endingResponse, endingIndex)
  }

  def debugPrint(query: Option[LTBounds] = None): Unit = {
    val fillers = "αßΓπΣσµτΦΘΩδ∞φε∩".toList
    var _filler = -1
    def nextFiller(): Char = {
      _filler = (_filler + 1) % fillers.length
      fillers(_filler)
    }

    val w: Int = (layout.bleedBounds.width).intValue()+1
    val h: Int = (layout.bleedBounds.height).intValue()+1

    val graphPaper = GraphPaper.create(w, h)
    val graphPaper2 = GraphPaper.create(w, h)

    layout.positioning.foreach { pos =>
      val gridbox = GraphPaper.ltb2box(pos.strictBounds)

      pos.widget match {
        case l @ RegionOverlay(wid, under, overlays) =>

          val pageId = under.page.pageId
          // println(s"debugPrint: ${l} @ ${gridbox}")
          val id = pageId.unwrap
          val fill = (id + '0'.toInt).toChar
          graphPaper.fillFg(fill, gridbox)
          graphPaper.border(gridbox, Colors.Red)

        case _ =>
      }
    }

    layout.positioning.foreach { pos =>
      val gridbox = GraphPaper.ltb2box(pos.strictBounds)
      pos.widget match {
        case l @ Panel(wid, a, i) =>
          graphPaper2.fillFg(nextFiller(), gridbox)
        case l @ Figure(wid, fig) =>
          graphPaper.fillFg(nextFiller(), gridbox)
        case l @ Identified(wid, a, id, cls)    =>
        case _ =>
      }
    }

    layout.positioning.foreach { pos =>
      val gridbox = GraphPaper.ltb2box(pos.strictBounds)
      pos.widget match {
        case Col(wid, as) => graphPaper.borderLeftRight(gridbox, Colors.Gray)
        case Row(wid, as) => graphPaper.borderTopBottom(gridbox, Colors.Red)

        // case l : RegionOverlay[A]     => l.overlays.traverse(f).map(ft => l.copy(overlays=ft))
        // case l @ Pad(a, pd, clr)      => f(a).map(Pad(_, pd, clr))
        // case l : LabeledTarget        => G.point(l.copy())
        // case l : TextBox              => G.point(l.copy())
        // case l : Reflow               => G.point(l.copy())
        // case l : Figure               => G.point(l.copy())
        // case l @ Panel(a, i)          => f(a).map(Panel(_, i))
        // case l @ Identified(a, id, cls)    => f(a).map(Identified(_, id, cls))
        case _ =>
      }
    }
    query foreach { q =>
      // println(s"query: $q")
      q.intersection(graphPaper.bbox).foreach{ clippedQuery =>
        println(s"clipped query: $clippedQuery")
        graphPaper.shadeBackground(GraphPaper.ltb2box(clippedQuery), Colors.Blue3)
        graphPaper2.shadeBackground(GraphPaper.ltb2box(clippedQuery), Colors.White)
      }
    }


    val grid1 = graphPaper.asString()
    val grid2 = graphPaper2.asString()

    println(grid1)
    // println
    // println(grid2)
    // println
  }
}
