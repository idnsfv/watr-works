package edu.umass.cs.iesl.watr
package rtrees

import scala.collection.mutable

import watrmarks._
import geometry._
import geometry.syntax._

import rtrees._
import utils.OrderedDisjointSet

import utils.ExactFloats._
import textboxing.{TextBoxing => TB}
import scala.reflect.ClassTag
import com.google.{common => guava}
import guava.{collect => gcol}
import com.github.davidmoten.rtree.{geometry => RG}
import utils.DoOrDieHandlers._


object LabeledShapeIndex {


  def qualifyCluster(l: Label): Label = { l.qualifiedAs("cluster") }
  def qualifyOrdering(l: Label): Label = { l.qualifiedAs("ordering") }
  def qualifyRelation(l: Label): Label = { l.qualifiedAs("relation") }
  def qualifyRep(l: Label): Label = { l.qualifiedAs("rep") }

  import _root_.io.circe
  import circe._
  import circe.syntax._
  import circe.literal._

  implicit def LabelShapeIndexEncoder[
    A <: GeometricFigure,
    W,
    Shape <: LabeledShape.Aux[A, W] : Encoder
  ](implicit
    RTreeEncoder: Encoder[RTreeIndex[A, W, Shape]]
  ): Encoder[LabeledShapeIndex[A, W, Shape]] = {
    Encoder.instance[LabeledShapeIndex[A, W, Shape]]{ shapeIndex =>

      Json.obj(
        "rtree" := shapeIndex.shapeRIndex.asJson,
        "nextId" := shapeIndex.shapeIDGen.peekId.unwrap,
        "shapeMap" := shapeIndex.shapeMap.values.toList.sortBy(_.id.unwrap)
      )
    }
  }

  implicit def LabeledShapeIndexDecoder[
    A <: GeometricFigure,
    W,
    Shape <: LabeledShape.Aux[A, W] : Decoder
  ]: Decoder[LabeledShapeIndex[A, W, Shape]] = {
    Decoder.instance[LabeledShapeIndex[A, W, Shape]]{ c =>

      val rtreeIndex = RTreeIndex.empty[A, W, Shape]()
      val rtreeJson = c.downField("rtree").focus.orDie("no shapes field found")
      val rtree = rtreeJson.decodeOrDie[RTreeIndex[A, W, Shape]]("Invalid shape list")
      val shapeIndex =  LabeledShapeIndex.withRTree[A, W, Shape](rtree)


      val nextId = c.downField("nextId").focus.orDie().decodeOrDie[Int]()
      shapeIndex.shapeIDGen.setNextId(nextId)

      val shapeMapValues = c.downField("shapeMap").focus.orDie().decodeOrDie[List[Shape]]()
      shapeMapValues.foreach{ shape =>
        shapeIndex.shapeMap.put(shape.id.unwrap.longValue(), shape)
      }
      Right(shapeIndex)
    }

  }


  def empty[
    A <: GeometricFigure,
    W,
    Shape <: LabeledShape.Aux[A, W]
  ]: LabeledShapeIndex[A, W, Shape] = {
    new LabeledShapeIndex[A, W, Shape] {
      val shapeRIndex: RTreeIndex[A, W, Shape] = RTreeIndex.empty[A, W, Shape]()
    }
  }

  def withRTree[
    A <: GeometricFigure,
    W,
    Shape <: LabeledShape.Aux[A, W]
  ](rtree: RTreeIndex[A, W, Shape]): LabeledShapeIndex[A, W, Shape] = {
    new LabeledShapeIndex[A, W, Shape] {

      val items = rtree.getItems()
      val maxId = if (items.isEmpty) 0 else {
        items.maxBy(_.id.unwrap).id.unwrap
      }
      val shapeRIndex: RTreeIndex[A, W, Shape] = rtree
      shapeIDGen.setNextId(maxId+1)
      shapeMap ++= items.map{i => (i.id.unwrap.toLong, i)}
    }
  }



}

abstract class LabeledShapeIndex[A <: GeometricFigure, W, Shape <: LabeledShape.Aux[A, W]] {

  import LabeledShapeIndex._

  val shapeIDGen = utils.IdGenerator[ShapeID]()
  def shapeRIndex: RTreeIndex[A, W, Shape]

  val shapeMap: mutable.LongMap[Shape] = {
    mutable.LongMap[Shape]()
  }

  type LineShape  = LabeledShape[Line, W]
  type PointShape = LabeledShape[Point, W]
  type RectShape  = LabeledShape[LTBounds, W]
  type TrapezoidShape  = LabeledShape[Trapezoid, W]
  type AnyShape   = Shape

  def getAllShapes(): Seq[Shape] = {
    shapeMap.values.toSeq
  }

  def getById(id: Int@@ShapeID): Shape = {
    shapeMap(id.unwrap.toLong)
  }

  def initShape[S <: Shape](f: Int@@ShapeID => S): S = {
    val lshape = f(shapeIDGen.nextId)
    shapeMap.put(lshape.id.unwrap.toLong, lshape)
    lshape.setIndexed(false)
    lshape
  }

  def indexShape[S <: Shape](f: Int@@ShapeID => S): S = {
    val lshape = initShape(f)
    shapeRIndex.add(lshape)
    lshape.setIndexed(true)
    lshape
  }

  def unindexShape(lshape: Shape): Unit = {
    shapeRIndex.remove(lshape)
    lshape.setIndexed(false)
  }

  def reindexShapes(l: Label): Unit = {
    getAllShapes().foreach { shape =>
      if (shape.hasLabel(l) && !shape.isIndexed()) {
        shapeRIndex.add(shape)
        shape.setIndexed(true)
      }
    }
  }


  def deleteShape(lshape: Shape): Unit = {
    unindexShape(lshape)
    lshape.setIndexed(false)
    shapeMap.remove(lshape.id.unwrap.toLong)
  }

  def deleteShapes(): Unit = {
    shapeMap.clear()
    shapeRIndex.clearAll()
  }

  def getShapesWithLabel(l: Label): Seq[Shape] = {
    shapeRIndex.getItems
      .filter { item =>
        item.labels.contains(l)
      }
  }

  def searchShapes(
    query: GeometricFigure,
    intersectFunc: (GeometricFigure, GeometricFigure) => Boolean,
    labels: Label*
  ): Seq[Shape] = {
    shapeRIndex.search(query, { lshape =>
      (intersectFunc(lshape.shape, query)
        && labels.forall(lshape.hasLabel(_)))
    })
  }

  def searchShapes(
    query: GeometricFigure,
    labels: Label*
  ): Seq[Shape] = {
    shapeRIndex.search(query, { lshape =>
      labels.forall(lshape.hasLabel(_))
    })
  }

  val disjointSets: mutable.HashMap[Label, OrderedDisjointSet[Shape]] = mutable.HashMap()

  def ensureCluster[T <: GeometricFigure](l: Label): OrderedDisjointSet[Shape] = {
    disjointSets.getOrElseUpdate(l,
      OrderedDisjointSet.apply[Shape]()
    )
  }

  def addCluster[T <: GeometricFigure](l: Label, shapes: Seq[Shape]): Shape = {
    assume(shapes.nonEmpty)
    _addCluster(l, shapes)
  }

  def initClustering(l: Label, f: Shape => Boolean): Seq[Shape] = {
    assume(!disjointSets.contains(l))
    val toAdd = shapeRIndex.getItems.filter(f)
    disjointSets.getOrElseUpdate(l,
      OrderedDisjointSet.apply[Shape](toAdd:_*)
    )
    toAdd
  }


  private def _addCluster[T <: GeometricFigure, R <: GeometricFigure](
    l: Label, cs: Seq[Shape]
  ): Shape = {
    assume(cs.nonEmpty)

    val set = ensureCluster(l)

    val c0 = cs.head
    set.ensure(c0)
    cs.tail.foreach { cn =>
      set.ensure(cn)
      set.union(c0, cn)
    }
    val canonical = set.getCanonical(c0)
    canonical.asInstanceOf[Shape]
  }

  def unionAll(l: Label, cs: Seq[Shape]): Unit = {
    val _ = _addCluster(l, cs)
  }

  def union(l: Label, c1: Shape, c2: Shape): Unit = {
    val _ = _addCluster(l, Seq(c1, c2))
  }

  def getClusterMembers(l: Label, cc: Shape): Option[Seq[Shape]] = {
    _getClusterMembers(l, cc)
  }

  private def _getClusterMembers(l: Label, cc: Shape): Option[Seq[Shape]] = {
    disjointSets.get(l).map{set =>
      set.sets.toSeq.map(_.toSeq)
        .filter(_.contains(cc))
        .headOption
    } getOrElse(None)
  }


  def getClusters(l: Label): Option[Seq[Seq[Shape]]] = {
    disjointSets.get(l)
      .map{ set => set.sets }
  }

  def getClustersWithReprID(l: Label): Seq[(Int@@ShapeID, Seq[Shape])] = {
    disjointSets.get(l).map { disjointSet =>
      disjointSet.sets.map{ cluster =>
        val repr = disjointSet.getCanonical(cluster.head)
        (repr.id, cluster)
      }
    } getOrElse(Seq())
  }

  def getClusterRoots(l: Label): Seq[Shape] = {
    disjointSets.get(l).map{set =>
      set.sets.map{ cluster =>
        set.getCanonical(cluster.head)
      }
    } getOrElse(Seq())
  }

  def getClusterRoot(l: Label, s: Shape): Option[Shape] = {
    disjointSets.get(l).flatMap{set =>
      if (set.contains(s)) Some(set.getCanonical(s)) else None
    }
  }

  def reportClusters(): Unit = {
    import TB._
    val allSets = disjointSets.keys.toList
      .map{ l =>
        val dsets = disjointSets(l)
        val setStrs = dsets.sets().map{ set =>
          val canon = dsets.getCanonical(set.head)
          val members = set.take(2).map(_.id).mkString(", ")
          val membersFull = set.take(8).map(_.toString().box)

          s"[${canon}] = (${set.length}) ${members} ..." atop indent(2, vcat(left,membersFull))
        }

        vjoin(left,
          s"$l => ",
          indent(2, vjoinWith(left, vspace(1), setStrs)),
          "~"*20
        )
      }

    val res = vjoin(indent(4, vcat(left, allSets)))

    println(res)
  }

  val shapeAttributeTable = gcol.HashBasedTable.create[Int@@ShapeID, Label, Any]()

  def setShapeAttribute[C](shapeId: Int@@ShapeID, l: Label, attr: C)
    (implicit act: ClassTag[C]): Unit = {
    val typedLabel = l.qualifiedAs(act.runtimeClass.getSimpleName)
    shapeAttributeTable.put(shapeId, typedLabel, attr)
  }

  def getShapeAttribute[C](shapeId: Int@@ShapeID, l: Label)
    (implicit act: ClassTag[C]): Option[C] = {
    val typedLabel = l.qualifiedAs(act.runtimeClass.getSimpleName)
    if (shapeAttributeTable.contains(shapeId, typedLabel)) {
      Some(shapeAttributeTable.get(shapeId, typedLabel).asInstanceOf[C])
    } else None
  }


}
