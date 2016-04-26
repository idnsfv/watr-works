package edu.umass.cs.iesl.watr
package watrcolors
package client

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import native.fabric


import scala.concurrent.Future
import scala.async.Async.{async, await}

object handlers {

  def getUserBBox(c: fabric.Canvas): Future[BBox] = {

    val chan = CanvasMouseChannels(c)

    async {
      var res = await(chan.mousedown())
      val px1: Int = res.e.pageX
      val py1: Int = res.e.pageY

      res = await(chan.mousemove | chan.mouseup)
      while(res.e.`type` == "mousemove"){
        res = await(chan.mousemove | chan.mouseup)
      }

      val px2 = res.e.pageX
      val py2 = res.e.pageY
      val x = math.min(px1, px2)
      val y = math.min(py1, py2)
      val w = math.abs(px2 - px1)
      val h = math.abs(py2 - py1)

      BBox(x.toDouble, y.toDouble, w.toDouble, h.toDouble, "")
    }
  }

  def translateBBox(x0: Double, y0: Double, bb: BBox): BBox = {
    bb.copy(
      x = bb.x + x0,
      y = bb.y + y0
    )
  }

}


