package edu.umass.cs.iesl.watr
package labeling

import geometry._
// import spindex._
import docstore._
import LabelWidgetF._
import corpora._
import rindex._
import watrmarks.{StandardLabels => LB}


object LabelWidgetIndex extends LabelWidgetLayout {

  implicit object LabelWidgetIndexable extends SpatialIndexable[PosAttr] {
    def id(t: PosAttr): Int = t.id.unwrap
    def ltBounds(t: PosAttr): LTBounds = t.widgetBounds
  }

  def create(db: TextReflowDB, lwidget: LabelWidget): LabelWidgetIndex = {
    val lwIndex = SpatialIndex.createFor[PosAttr]()

    val layout0 = layoutWidgetPositions(lwidget)

    layout0.foreach({pos =>
      lwIndex.add(pos)
    })

    new LabelWidgetIndex {
      def docStore: DocumentCorpus = db.docstorage
      def layout: List[PosAttr] = layout0
      def index: SpatialIndex[PosAttr] = lwIndex
    }
  }
}


trait LabelWidgetIndex {

  def docStore: DocumentCorpus
  def layout: List[PosAttr]
  def index: SpatialIndex[PosAttr]

  def onClick(clickPoint: Point): List[LTBounds] = {

    val query = LTBounds(clickPoint.x, clickPoint.y, 1, 1)
    val positioned: Seq[PosAttr] = index.queryForIntersects(query)
    positioned.headOption.map{ p =>

      p.widget match {
        case Button(s) =>
        case LabeledTarget(t, label, score) =>

      }

    }


    List()
  }



  def getWidgetForTargetRegion(targetRegion: TargetRegion): PosAttr = {
    val stableId = targetRegion.stableId
    val docId = docStore.getDocument(stableId).get
    val pageId = docStore.getPage(docId, targetRegion.pageNum).get
    // Map TargetRegion -> PosAttr
    layout
      .collect({
        case p @ PosAttr(
          LabeledTarget(bbox, label, score),
          widgetBounds,
          pRegionId, _, _
        ) if targetRegion.id == targetRegion.id => p
      }).headOption
      .getOrElse(sys.error(s"getWidgetForTargetRegion: no entry for ${targetRegion}"))

    ???

  }

  def onSelect(bbox: LTBounds): List[LTBounds] = {

    val positioned: Seq[PosAttr] = index.queryForIntersects(bbox)

    val selectedTargets: List[(PosAttr, LabeledTarget)] = positioned.toList
      .map(p => index.getItem(p.id.unwrap))
      .filter(_.widget.isInstanceOf[LabeledTarget])
      .map(p => (p, p.widget.asInstanceOf[LabeledTarget]))

    val updates: Option[Seq[PosAttr]] = if (selectedTargets.nonEmpty) {

      // TODO: Target label set by radio button in UI
      val targetLabel: watrmarks.Label = LB.Authors

      val visualLineZones: Seq[Zone] = for {
        (posAttr, labeledTarget) <- selectedTargets
        zoneId <- docStore.getZoneForTargetRegion(labeledTarget.target.id, LB.VisualLine)
      } yield {
        docStore.getZone(zoneId)
      }

      // If any selected regions are already part of a zone...
      val resultZone = if (visualLineZones.nonEmpty) {
        // Merge them..
        def mergedZone: Zone =  ???

        // docStore.getZone(docStore.mergeZones(existingZones.map(_.id)))

        // Add all target regions to merged zone
        selectedTargets.map(tr => docStore.setZoneTargetRegions(
          mergedZone.id,
          mergedZone.regions :+ tr._2.target
        ))
        Option(mergedZone)

      } else {
        // Create a new Zone with given label
        val stableId = selectedTargets.head._2.target.stableId
        val docId = docStore
          .getDocument(stableId)
          .getOrElse(sys.error(s"onSelect() document ${stableId} not found"))

        val targetRegions = selectedTargets.map(_._2.target)
        val newZone = docStore.getZone(
          docStore.createZone(docId)
        )
        docStore.setZoneTargetRegions(newZone.id, targetRegions)
        docStore.addZoneLabel(newZone.id, targetLabel)

        Option(newZone)
      }

      //   resultZone.map({zone =>
      //     zone.regions
      //       .map(getWidgetForTargetRegion(_))

      //   })
      // updates0

      None
    } else {
      None
    }

    // updates
    //   .toList.flatten
    //   .map(p => p.widgetBounds)
    ???

  }
}
