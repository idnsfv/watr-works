package edu.umass.cs.iesl.watr
package extract
package images


import com.sksamuel.scrimage._

import edu.umass.cs.iesl.watr.images.ImageManipulation


case class PageImages(
  images: Seq[Image]
) {
  lazy val dims = images.map({image =>
    image.dimensions
  })

  override def toString():String = {
    dims
      .map({case (w, h) => s"img:${w}x${h}"})
      .mkString("[", ", ", "]")
  }

  def page(p: Int@@PageNum): Image = {
    if (p.unwrap < images.length) {
      images(p.unwrap)
    } else {
      sys.error(s"PageImage ${p} not found in pages ${toString()}")
    }
  }

  def pageBytes(p: Int@@PageNum): Array[Byte]= {
    val image = page(p)
    image.bytes
  }

}

object ExtractImages extends ImageManipulation {
  import ammonite.{ops => fs}, fs._

  implicit val writer = nio.PngWriter.NoCompression

  def load(rootPath: Path): PageImages = {
    val images = ls(rootPath)
      .filter(_.ext=="png")
      .sortBy(_.name.drop(5).dropRight(4).toInt)
      .map(p => Image.fromFile(p.toNIO.toFile))

    PageImages(images)
  }

  // HOTSPOT
  def extract(pdfPath: Path, outputPath: Path): PageImages = {
    import fs.ImplicitWd._
    val out = outputPath / "page-%d.png"

    val res = %%("mudraw", "-r", "128", "-o", out, pdfPath)

    load(outputPath)
  }



}
