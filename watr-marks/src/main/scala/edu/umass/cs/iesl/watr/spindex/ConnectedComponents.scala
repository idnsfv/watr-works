package edu.umass.cs.iesl.watr
package spindex

import scalaz.@@
import watrmarks._

import IndexShapeOperations._
import GeometricFigure._

import TypeTags._
import utils.VisualTracer._
import watrmarks.{StandardLabels => LB}
import scalaz.Tree

/*

 Connected components represent a (rectangular) query against a spatial index

 PageAtoms are the smallest rectangular blocks extracted from PDFs.
 PageComponent is the query that selects a single PageAtom
 RegionComponent is the query that selects everything within its boundary (whitespace and/or PageComponent)


 child/descendant methods on Component should go away, as there is no strict hierarchical structure to CCs

 Instead of child/desc methods, we introduce query types, which select everything within a given region,
   filtered by predicates, including labels on regions, and query modifiers such as "contains|intersects|isInside|etc.."


 Ordering of components within a region (e.g., char/word ordering in a visual line, or line ordering within text blocks),
 is a function of the containing component, e.g., a text block region imposes an ordering on all contained line regions, with
 a vertical increasing Y ordering (implicit), or a link to an explicit ordering

 */

object Query {
  sealed trait Filter
  sealed trait Type
  case object Intersects extends Type
  case object Contains extends Type
}

sealed trait Quantifier
object Quantifier {
  case object All extends Quantifier
  case object Exists extends Quantifier
}


sealed trait Component {
  def id: Int@@ComponentID

  def zoneIndex: ZoneIndexer
  def vtrace = zoneIndex.vtrace

  def roleLabel: Label

  def queryAtoms(): Seq[PageComponent] = {
    queryInside(LB.PageAtom).map(_.asInstanceOf[PageComponent])
  }

  def queryInside(role: Label): Seq[Component] = {
    getChildTree(role)
      .getOrElse ({
        val pinfo = zoneIndex.getPageInfo(zoneIndex.getPageForComponent(this))
        val bounds = this.targetRegion.bbox
        pinfo.componentIndex
          .queryForContained(bounds)
          .filter(_.roleLabel == role)
      })
  }

  def queryFor(qt: Query.Type, quantifier: Quantifier, labels: Label*): Seq[Component] = {
    val pinfo = zoneIndex.getPageInfo(zoneIndex.getPageForComponent(this))
    val cindex = pinfo.componentIndex
    val bounds = this.targetRegion.bbox
    val hits = qt match {
      case Query.Intersects => cindex.queryForIntersects(bounds)
      case Query.Contains   => cindex.queryForContained(bounds)
    }

    val labelSet = labels.toSet

    quantifier match {
      case Quantifier.All    => hits.filter(labelSet subsetOf _.getLabels)
      case Quantifier.Exists => hits.filterNot(_.getLabels.intersect(labelSet).isEmpty)
    }
  }

  def targetRegions: Seq[TargetRegion]

  // TODO target region only makes sense for some connected components
  def targetRegion: TargetRegion = {
    targetRegions.headOption
      .map(tr => TargetRegion(RegionID(0), tr.target, bounds))
      .getOrElse {  sys.error("no target region found in Component}") }
  }

  def chars: String

  // TODO: this is a convoluted function:
  def getChildren(): Seq[Component] = {
    getChildren(roleLabel)
  }

  def getChildren(l: Label): Seq[Component] = {
    getChildTree(l).getOrElse {Seq()}
  }


  def getChildTree(l: Label): Option[Seq[Component]] = {
    zoneIndex.getChildTree(this, l)
  }

  def setChildTree(l: Label, tree: Seq[Component]): Unit = {
    zoneIndex.setChildTreeWithLabel(this, l, tree.map(_.id))
  }

  def connectChildren(l: Label, cs: Seq[Component]): Unit = {
    zoneIndex.setChildTreeWithLabel(this, l, cs.map(_.id))
  }


  def atomicComponents: Seq[PageComponent] =  {
    queryFor(Query.Contains, Quantifier.All, LB.PageAtom)
      .map(_.asInstanceOf[PageComponent])
  }


  def mapChars(subs: Seq[(Char, String)]): Component

  def toText(implicit idgen:Option[CCRenderState] = None): String

  // TODO: This is redundant w/targetregion
  def bounds: LTBounds

  def orientation: Double = 0.0d // placeholder until this is implemented for real

  import utils.VisualTracer._

  def addLabel(l: Label): Component = {
    vtrace.trace("addLabel" withTrace link(showComponent(this), showLabel(l)))
    zoneIndex.addLabel(this, l)
  }

  def removeLabel(l: Label): Component = {
    vtrace.trace("removeLabel())" withTrace link(showComponent(this), showLabel(l)))
    zoneIndex.removeLabel(this, l)
  }

  def getLabels(): Set[Label] = {
    zoneIndex.getLabels(this)
  }

  def setChildren(l: Label, cs: Seq[Component]): Unit = {
    setChildTree(l, cs)
  }

  def toTree(roles: Label*): Tree[Component] = {
    if (roles.isEmpty) {
      // println(s"toTree: empty ${this}")
      Tree.Leaf(this)
    } else {
      // println(s"""toTree: ${this} / ${roles.mkString(", ")}""")
      val childForest = getChildren(roles.head)
        .map(_.toTree(roles.tail:_*))
        .toStream

      Tree.Node(this, childForest)
    }
  }
}



case class RegionComponent(
  id: Int@@ComponentID,
  override val roleLabel: Label,
  var region: TargetRegion,
  override val zoneIndex: ZoneIndexer
) extends Component {

  def cloneAs(l: Label): RegionComponent = {
    val atoms = queryAtoms()
    val clone = copy(
      id = zoneIndex.componentIdGen.nextId,
      roleLabel = l,
      region = region.copy(
        id = zoneIndex.regionIdGen.nextId
      )
    )
    zoneIndex.addComponent(clone)
    clone.setChildTree(LB.PageAtom, atoms)
    clone
  }

  def splitAtomsIf(splitf: (PageComponent, PageComponent, Int) => Boolean): Seq[RegionComponent] = {
    import utils.SlicingAndDicing._

    def cbounds(cs: Seq[Component]) = {
      cs.map(_.bounds)
        .reduce(_ union _)
    }

    val splitAtoms = queryAtoms().splitOnPairsWithIndex(splitf)

    val splitRegions = splitAtoms.map({case atoms =>
      val rc = cloneAs(roleLabel)
      val split = rc.copy(
        region = rc.region.copy(
          bbox = cbounds(atoms)))

      split.setChildren(LB.PageAtom, atoms)
      split
    })

    splitRegions
  }

  def targetRegions: Seq[TargetRegion] = Seq(region)

  def chars: String = ""

  def bounds: LTBounds = region.bbox

  def toText(implicit idgen:Option[CCRenderState] = None): String = {
    ""
  }

  def mapChars(subs: Seq[(Char, String)]): Component = {
    this
  }


  def extendRegion(r: TargetRegion): Unit = {
    region = region.copy(bbox = region.bbox union r.bbox)
    vtrace.trace(message(s"extendRegion: ${this.toString()}"))
  }

  override def toString(): String = {
    s"<${roleLabel}#${id} ${region}>"
  }
}


case class PageComponent(
  id: Int@@ComponentID,
  component: PageAtom,
  override val zoneIndex: ZoneIndexer
) extends Component {
  def roleLabel: Label = LB.PageAtom

  def targetRegions: Seq[TargetRegion] = Seq(component.region)

  // def children(): Seq[Component] = Seq()

  // def replaceChildren(ch: Seq[Component]): Unit = ()

  // def charComponents: Seq[PageComponent] = Seq(this)

  // def atoms: Seq[PageAtom] = Seq(component)

  def char = component match {
    case rg: CharAtom => rg.char.toString
    case rg: ImgAtom => ""
  }

  def mapChars(subs: Seq[(Char, String)]): Component  = {
    subs
      .find(_._1.toString==char)
      .map({case (_, sub) =>
        component match {
          case rg: CharAtom => this.copy(
            component= CharAtom.apply(rg.region, sub, rg.wonkyCharCode))


          case rg: ImgAtom  => this
        }
      })
      .getOrElse(this)
  }


  val bounds = component.region.bbox

  def toText(implicit idgen:Option[CCRenderState] = None): String = {
    component match {
      case rg: CharAtom => rg.char.toString
      case rg: ImgAtom => ""
    }
  }

  def chars: String = toText

  override def toString(): String = {
    s"<${roleLabel}#${id} ${chars}>"
  }

}


// object ConnectedComponent {
//   def apply(
//     id: Int@@ComponentID,
//     components: Seq[Component],
//     zoneIndex: ZoneIndexer
//   ): ConnectedComponents = ConnectedComponents(
//     id, mutable.MutableList(components:_*), zoneIndex
//   )
// }


// case class ConnectedComponents(
//   id: Int@@ComponentID,
//   components: mutable.MutableList[Component],
//   override val zoneIndex: ZoneIndexer
// ) extends Component {

//   def targetRegions: Seq[TargetRegion] = components.flatMap(_.targetRegions)

//   def replaceChildren(ch: Seq[Component]): Unit = {
//     this.components.clear()
//     this.components ++= ch
//   }

//   def atoms: Seq[PageAtom] = components.flatMap(_.atoms)

//   def children(): Seq[Component] = components

//   def mapChars(subs: Seq[(Char, String)]): Component  = {
//     copy(
//       components = components.map(_.mapChars(subs))
//     )
//   }

//   def chars:String = {
//     components.map(_.chars).mkString
//   }

//   def charComponents: Seq[PageComponent] =
//     components.flatMap(_.charComponents)

//   def toText(implicit idgen:Option[CCRenderState] = None): String ={
//     val ccs = renderConnectedComponents(this)
//     TB.hcat(ccs).toString()
//   }


//   def bounds: LTBounds = components.tail
//     .map(_.bounds)
//     .foldLeft(components.head.bounds)( { case (b1, b2) =>
//       b1 union b2
//     })

//   override def toString(): String = {
//     s"""cc:${components.map(_.toString()).mkString("")}"""
//   }




// }
