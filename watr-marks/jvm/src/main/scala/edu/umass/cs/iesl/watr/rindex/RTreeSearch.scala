package edu.umass.cs.iesl.watr
package rindex

import RGeometryConversions._
import rx.Observable
import rx.functions.{
  Func1
  // , Func2
}

import scala.collection.JavaConverters._

import com.github.davidmoten.rtree.{geometry => RG, _}
import edu.umass.cs.iesl.watr.{geometry => G}
import java.lang.{Boolean => JBool}

trait RTreeSearch[T] {

  def rtreeIndex: RTree[T, RG.Geometry]

  def indexable: RTreeIndexable[T]

  lazy implicit val si = indexable



  def search(
    queryFig:G.GeometricFigure,
    filter: T=>Boolean,
    // intersectFunc: (G.GeometricFigure, G.GeometricFigure) => Boolean = (_,_) => true,
    intersectFunc: (RG.Geometry, RG.Geometry) => Boolean = (_,_) => true,
  ): Seq[T] = {
    val filterFunc = new Func1[Entry[T, RG.Geometry], JBool]() {
      override def call(entry: Entry[T, RG.Geometry]): JBool = {
        filter(entry.value())
      }
    }

    val hits0 = queryFig match {
      case f: G.LTBounds => rtreeIndex.search(toRGRectangle(f))
      case f: G.Line     => rtreeIndex.search(toRGLine(f))
      case f: G.Point    => rtreeIndex.search(toRGPoint(f))
      case f: G.LBBounds => rtreeIndex.search(toRGRectangle(f.toLTBounds))
      case _             => sys.error("unsupported query shape")
    }

    val hits = hits0.filter(filterFunc)

    toScalaSeq(hits)
  }

  def queryForIntersects(q: G.LTBounds): Seq[T] = {
    toScalaSeq(rtreeIndex.search(toRGRectangle(q)))
  }

  def queryForIntersectedIDs(q:G.LTBounds): Seq[Int] = {
    toEntrySeq(rtreeIndex.search(toRGRectangle(q)))
      .map{ entry => si.id(entry.value()) }
  }

  protected def toScalaSeq(obs: Observable[Entry[T, RG.Geometry]]): Seq[T]  = {
    toEntrySeq(obs).toSeq.map{ _.value() }
  }

  protected def toEntrySeq(obs: Observable[Entry[T, RG.Geometry]]): Seq[Entry[T, RG.Geometry]]  = {
    obs.toBlocking().toIterable().asScala.toSeq
  }

  protected def toIdSeq(obs: Observable[Entry[T, RG.Geometry]]): Seq[Int]  = {
    toEntrySeq(obs).map{ entry => si.id(entry.value()) }
  }
}
