package edu.umass.cs.iesl.watr
package tracing

import tracemacros._
import scala.language.experimental.macros

import geometry._
import textboxing.{TextBoxing => TB}

object VisualTracer {
  import scala.collection.mutable
  import TraceLog._
  import VisualTraceLevel._

  // implicit class RicherString(val s: String) extends AnyVal {}

  def message(s: TB.Box): TraceLog                      = {Message(s)}

  // var visualTraceLevel: VisualTraceLevel = VisualTraceLevel.Off

  val activeTraces = mutable.HashSet[VisualTraceLevel]()

  def clearTraceLevelw(): Unit = {
    activeTraces.clear()
  }

  def addTraceLevel(v: VisualTraceLevel): Unit = v match {
    case  EnterExit =>
      activeTraces += EnterExit
    case  Checkpoint =>
      activeTraces ++= Seq(EnterExit, Checkpoint)
    case  AccumLogs =>
      activeTraces ++= Seq(EnterExit, AccumLogs)
    case  PrintLogs =>
      activeTraces ++= Seq(EnterExit, PrintLogs)
  }


}

// import scala.language.dynamics

// trait TraceCallbacks extends TraceCallbacksT { self =>
//   // foo.field           ~~> foo.selectDynamic("field")
//   // foo.arr(10) = 13    ~~> foo.selectDynamic("arr").update(10, 13)
//   def selectDynamic[T](name: String): T = macro TraceCallbackMacros._selectDynamic[T]


//   // foo.varia = 10      ~~> foo.updateDynamic("varia")(10)
//   // def updateDynamic(name: String)(value: Any) = {}


//   // foo.method("blah")      ~~> foo.applyDynamic("method")("blah")
//   def applyDynamic[T](name: String)(value: Any): T = macro TraceCallbackMacros._applyDynamic[T]


//   // foo.method(x = "blah")  ~~> foo.applyDynamicNamed("method")(("x", "blah"))
//   // def applyDynamicNamed(name: String)(values: (String, Any)*) = {}

// }

trait VisualTracer { self =>
  import VisualTracer._

  lazy val tracer = self

  // def traceCallbacks(): TraceCallbacks

  // def traceLevel(): VisualTraceLevel = visualTraceLevel
  def traceLevels(): Seq[VisualTraceLevel] = activeTraces.toSeq

  def tracingEnabled(): Boolean = {
    activeTraces.nonEmpty
  }

  def apply(body: => Unit): Unit = ifTrace(VisualTraceLevel.PrintLogs)(body)


  def ifTrace(vtl: VisualTraceLevel)(body: => Unit): Unit = macro VisualTraceMacros.runOnTraceLevel[TraceLog]

  def enter()(implicit enclosing: sourcecode.Name): Unit = ifTrace(VisualTraceLevel.EnterExit){
    println(s"entered: ${enclosing.value}")
  }

  def exit()(implicit enclosing: sourcecode.Name): Unit = ifTrace(VisualTraceLevel.EnterExit) {
    println(s"exit: ${enclosing.value}")
  }


  def checkpoint(msg: String, args: Any*)(implicit
    enclosing: sourcecode.Name,
    loc: sourcecode.Enclosing
  ): Unit = ifTrace(VisualTraceLevel.Checkpoint) {
    // checkpoint0(loc.value, msg, args.toSeq)
    println(s"checkpoint@${enclosing.value}/${loc.value}:  '($msg)'")
  }


  // def checkpoint0(loc: String, msg: String, args: Seq[Any]): Unit = macro VisualTraceMacros.checkpointImpl[Unit]

}
