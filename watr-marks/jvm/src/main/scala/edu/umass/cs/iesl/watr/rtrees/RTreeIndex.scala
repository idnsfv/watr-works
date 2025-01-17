package edu.umass.cs.iesl.watr
package rtrees

import scala.collection.JavaConverters
import utils.DoOrDieHandlers._

import com.github.davidmoten.rtree.{geometry => RG, _}
import com.github.davidmoten.rtree
import rx.functions.Func1
import geometry._
import geometry.syntax._

/**
  * Wrapper around Java-based R-Tree implementation, for better scala interop
  * Supports shapes based on watrmarks geometry types, with an optional associated attribute
  * Provides JSon serialization
  **/

class RTreeIndex[A <: GeometricFigure, W, Shape <: LabeledShape.Aux[A, W]](
  var spatialIndex: RTree[Shape, RG.Geometry]
) extends RTreeSearch[A, W, Shape] {
  import RGeometryConversions._

  override def rtreeIndex: RTree[Shape, RG.Geometry] = spatialIndex

  def clearAll(): Unit = {
    spatialIndex = RTree.create[Shape, RG.Geometry]()
  }

  def remove(item: Shape): Unit = {
    spatialIndex = spatialIndex.delete(
      item,
      toRGRectangle(item.bounds)
    )
  }

  def add(item: Shape): Unit = {
    spatialIndex = spatialIndex.add(
      item,
      toRGRectangle(item.bounds)
    )
  }

  def getItems(): Seq[Shape] = {
    toScalaSeq(spatialIndex.entries())
  }

}

object RTreeIndex {
  import RGeometryConversions._

  def empty[A <: GeometricFigure, W, Shape <: LabeledShape.Aux[A, W]](): RTreeIndex[A, W, Shape] = {
    val init = RTree.create[Shape, RG.Geometry]()
    new RTreeIndex[A, W, Shape](init)
  }

  import _root_.io.circe
  import circe._
  import circe.syntax._
  import circe.literal._

  implicit def RTreeEncoder[
    A <: GeometricFigure,
    W,
    Shape <: LabeledShape.Aux[A, W] : Encoder
  ]: Encoder[RTreeIndex[A, W, Shape]] =
    Encoder.instance[RTreeIndex[A, W, Shape]]{ shapeIndex =>
      val shapes = shapeIndex.getItems

      Json.obj(
        "shapes" := shapes.sortBy(_.id.unwrap)
      )
    }

  implicit def RTreeDecoder[
    A <: GeometricFigure,
    W,
    Shape <: LabeledShape.Aux[A, W] : Decoder
  ]: Decoder[RTreeIndex[A, W, Shape]] =
    Decoder.instance[RTreeIndex[A, W, Shape]]{ c =>

      val rtreeIndex = RTreeIndex.empty[A, W, Shape]()
      val shapeJson = c.downField("shapes").focus.orDie("no shapes field found")
      val shapes = shapeJson.decodeOrDie[List[Shape]]("Invalid shape list")
      shapes.foreach { shape =>
        rtreeIndex.add(shape)
      }

      Right(rtreeIndex)
    }

}
