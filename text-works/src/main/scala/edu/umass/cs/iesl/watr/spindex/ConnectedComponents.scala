package edu.umass.cs.iesl.watr
package spindex

import watrmarks._
import geometry._
import watrmarks.{StandardLabels => LB}


object Component {
  import rindex._
  implicit object ComponentIndexable extends RTreeIndexable[Component] {
    def id(t: Component): Int = t.id.unwrap
    def ltBounds(t: Component): LTBounds = t.bounds
  }

  import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

  object Serialization  {

    def serialize[C <: Component](value: C): Array[Byte] = {
      val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(stream)
      oos.writeObject(value)
      oos.close
      stream.toByteArray
    }

    def deserialize[C <: Component](bytes: Array[Byte]): C = {
      val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
      val value = ois.readObject
      ois.close
      value.asInstanceOf[C]
    }
  }
}

sealed trait Component {
  def id: Int@@ComponentID

  def roleLabel: Label

  // def labels: Set[Label]

  def setRole(l: Label): Component
  // def addLabel(l: Label): Component
  // def removeLabel(l: Label): Component

  def targetRegion(): PageRegion

  def bounds(): LTBounds = targetRegion().bbox

  def chars: String

  def getStableID(): String@@DocumentID = targetRegion.page.stable.stableId

  lazy val pageNum = targetRegion.page.stable.pageNum

}

case class RegionComponent(
  id: Int@@ComponentID,
  override val roleLabel: Label,
  override val targetRegion: PageRegion,
  text: Option[String] = None
  // otherLabels: Seq[Label] = Seq()
) extends Component {

  // override val labels: Set[Label] = (roleLabel +: otherLabels).toSet

  def setRole(l: Label): Component = copy(roleLabel = l)
  // def addLabel(l: Label) = copy(otherLabels = l +: otherLabels)
  // def removeLabel(l: Label) = copy(otherLabels = otherLabels.filterNot(_==l))

  def chars: String = text.getOrElse("")

  override def toString(): String = {
    s"<${roleLabel.key}.${id} ${targetRegion}"
  }
}

case class AtomicComponent(
  id: Int@@ComponentID,
  charAtom: CharAtom,
  override val roleLabel: Label = LB.PageAtom
  // otherLabels: Seq[Label] = Seq()
) extends Component {

  // override val labels: Set[Label] = (roleLabel +: otherLabels).toSet

  def setRole(l: Label): Component = copy(roleLabel = l)
  // def addLabel(l: Label) = copy(otherLabels = l +: otherLabels)
  // def removeLabel(l: Label) = copy(otherLabels = otherLabels.filterNot(_==l))

  def targetRegion: PageRegion = charAtom.pageRegion

  def chars: String = charAtom.char

  override def toString(): String = {
    s"<`${chars}`${id} ${charAtom.bbox.prettyPrint}>"
  }
}
