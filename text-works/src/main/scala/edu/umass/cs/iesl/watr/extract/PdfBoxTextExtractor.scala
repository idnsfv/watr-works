package edu.umass.cs.iesl.watr
package extract

import java.awt.{Shape}
import java.awt.geom._
import java.awt.geom.AffineTransform

import scala.collection.mutable

import ammonite.{ops => fs}
import ammonite.ops._
import org.apache.fontbox.util.BoundingBox
import org.apache.pdfbox.contentstream._
import org.apache.pdfbox.pdmodel._
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import org.apache.pdfbox.pdmodel.font._
import org.apache.pdfbox.util.{Matrix, Vector}
// import scala.collection.JavaConverters._
// import utils.Debugging._



import TypeTags._
import geometry._
import geometry.syntax._
import utils._

import ExtractionImplicits._


class PdfBoxTextExtractor(
  page: PDPage,
  charIdGen: IdGenerator[CharID],
  pageNum: Int@@PageNum,
  fontDefs: FontDefs,
  pageBoundsPdfCoords: PDRectangle
) extends PDFGraphicsStreamEngine(page) {
  import ExtractedItem._
  import FontDefs._
  // Limit the # of chars that can be extracted per page to prevent pathological cases (e.g., embedded charts using symbol font-based dots)
  val MAX_EXTRACTED_CHARS_PER_PAGE = 10000

  var totalCharCount = 0

  var extractedItems = List[ExtractedItem]()

  def getPageItems(): Seq[ExtractedItem] = {
    extractedItems.reverse
  }

  protected def debugPrintPageItems(rows: List[List[ExtractedItem]]): Unit = {
    val rowStrs = rows.map { row =>
      // val runId = row.headOption.map(_.charProps.charRunId).getOrElse(-1)
      val chars = row.map(_.strRepr()).mkString
      s">> $chars"
    }

    val pageStr = rowStrs.mkString("\n  ", "\n  ", "\n")

    println(pageStr)
  }

  def addPathItem(item: ExtractedItem.PathItem): Unit = {
    extractedItems = item :: extractedItems
  }

  def addImgItem(item: ExtractedItem.ImgItem): Unit = {
    // println(s"addImgItem: ${item}")
    extractedItems = item :: extractedItems
  }

  def addCharItem(charAtom: ExtractedItem.CharItem): Unit = {
    extractedItems = charAtom :: extractedItems
  }

  def stashChar(c: Char): Unit = {
    // addCharItem(CharItem(charIdGen.nextId, charBounds, c))
  }

  def traceFunc()(implicit enc: sourcecode.Enclosing): Unit = {
    println(s"running ${enc.value}")
  }

  override def appendRectangle(p0: Point2D, p1: Point2D, p2: Point2D, p3: Point2D): Unit = {
    // p0.getX() p0.getY() p1.getX() p1.getY() p2.getX() p2.getY() p3.getX() p3.getY()
    // traceFunc()
  }

  override def clip(x1: Int): Unit = {
    // traceFunc()
  }

  override def closePath(): Unit = {
    // traceFunc()
  }

  override def curveTo(x1: Float,x2: Float,x3: Float,x4: Float,x5: Float,x6: Float): Unit = {
    // traceFunc()
  }

  override def drawImage(image: PDImage): Unit = {
    val ctm = getGraphicsState.getCurrentTransformationMatrix()
    val x1 = ctm.getValue(2, 0).toDouble
    val y1 = ctm.getValue(2, 1).toDouble
    val x2 = x1 + ctm.getValue(0, 0)
    val y2 = y1 + ctm.getValue(1, 1)

    val width = x2 - x1
    val height = y2 - y1

    val left = x1 - pageBoundsPdfCoords.getLowerLeftX
    val top = pageBoundsPdfCoords.getUpperRightY - y2

    val imgBounds = LTBounds.Doubles(left, top, width, height)

    val item = ExtractedItem.ImgItem(
      charIdGen.nextId,
      imgBounds
    )

    addImgItem(item)

    // traceFunc()
  }

  override def endPath(): Unit = {
    // traceFunc()
  }

  override def fillAndStrokePath(x1: Int): Unit = {
    // traceFunc()
  }

  override def fillPath(x1: Int): Unit = {
    // traceFunc()
  }

  override def getCurrentPoint(): Point2D = {
    // traceFunc()
    // if you want to build paths, you'll need to keep track of this
    new Point2D.Float(0, 0)
  }

  override def lineTo(x1: Float,x2: Float): Unit = {
    // traceFunc()
  }

  override def moveTo(x1: Float,x2: Float): Unit = {
    // traceFunc()
  }

  override def shadingFill(x1: org.apache.pdfbox.cos.COSName): Unit = {
    // traceFunc()
  }

  override def strokePath(): Unit = {
    // traceFunc()
  }


  override def showTextString(string: Array[Byte]): Unit = {
    super.showTextString(string)
  }


  def printSubs(): Unit = {
    // val hexOutput = cs.map(_.toInt.toHexString).mkString(", ")
    // val hexIn = unicode.head.toInt.toHexString
    // println(s"subbed unicode: ${unicode} => ${cs.mkString(','.toString())}")
    // println(s"              : ${hexIn} => ${hexOutput}")
  }

  def isCombiningMark(c: Char): Boolean = {
    val cint = c.toInt
    lazy val ext1 = 0x1AB0 <= cint && cint  <= 0x1AFF
    lazy val ext2 = 0x1DC0 <= cint && cint  <= 0x1DFF
    lazy val ext3 = 0x20D0 <= cint && cint  <= 0x20FF
    lazy val ext4 = 0xFE20 <= cint && cint  <= 0xFE2F

    (0x300 <= cint && cint <= 0x36F
      || ext1 || ext2 || ext3 || ext4
    )
  }

  def isSurrogateCodepoint(ch: Char): Boolean = {
    val cint = ch.toInt
    0xD800 <= cint && cint  <= 0xDFFF
  }

  private def getFontBounds(font: PDFont): PDRectangle = {
    val bbox = font.getBoundingBox
    val xmin = bbox.getLowerLeftX
    val ymin = bbox.getLowerLeftY
    val xmax = bbox.getUpperRightX
    val ymax = bbox.getUpperRightY
    val w = xmax - xmin
    val h = ymax - ymin
    new PDRectangle(xmin, ymin, w, h)
  }

  var pageSpaceTransform: PageSpaceTransforms = null

  lazy val pageLTBounds = getPageGeometry().bounds

  override protected def showGlyph(
    textRenderingMatrix: Matrix,
    pdFont: PDFont,
    code: Int,
    unicode: String,
    displacement: Vector
  ): Unit = {
    val isSpace = code == 32 && unicode.head == ' '

    if (!isSpace) {

      val glyphProps = calculateGlyphBounds(textRenderingMatrix, pdFont, code)

      // println(s"glyphProps: ${glyphProps}")

      fontDefs.addFont(pdFont)


      glyphProps.finalGlyphBounds.foreach { finalGlyphBounds =>
        val glyphBounds = finalGlyphBounds.getBounds2D.toLTBounds()
        val isContained = glyphBounds.isContainedBy(pageLTBounds) && glyphProps.fontBBox.isContainedBy(pageLTBounds)

        def appendChar(strRepr: String): Unit = {
          if (isContained) {
            addCharItem(CharItem(charIdGen.nextId, strRepr, getFontName(pdFont), glyphProps))
          }
        }

        if (unicode == null) {
         appendChar(s"¿${code};")
        } else {
          val isSurrogate =  unicode.exists(isSurrogateCodepoint(_))
          if (isSurrogate) {
            appendChar(s"¿${code};")

          } else {

            // if (unicode.length() > 1) { println(s"unicode > 1: ${unicode} = ${unicode.map(_.toInt.toHexString).toList}, code: ${code}") }

            val isAsciiCode =  unicode.forall(_.toInt == code)

            unicode.foreach { ch =>

              if (isAsciiCode) {
                fontDefs.addGlyphEvidence(pdFont, ch, glyphProps, pageNum)
              }

              UnicodeUtil.maybeSubChar(ch) match {
                case Left(c)   =>

                  if (isCombiningMark(c)) stashChar(c) else appendChar(c.toString())

                case Right(cs) =>

                  cs.foreach{ c =>
                    if (isCombiningMark(c)) stashChar(c) else appendChar(c.toString())
                  }
              }
            }
          }

        }
      }
    }
  }

  // var i = 4;

  def calculateGlyphBounds(textRenderingMatrix: Matrix,  font: PDFont,  code: Int) : GlyphProps = {

    // if (i > 0) {
    //   val fontMatrix = font.getFontMatrix.clone()
    //   val trm = textRenderingMatrix.clone()

    //   println(s"${i}. calculateGlyphBounds: code: ${code.toChar}")
    //   println(s"Page Box       : ${pageBounds.toLTBounds()}")
    //   println(s"Font Matrix    : ${fontMatrix}")
    //   println(s"TRM            : ${trm}")
    //   trm.concatenate(fontMatrix)

    //   val affineTRM = trm.createAffineTransform()
    //   println(s"F+TRM          : ${trm}")
    //   println(s"  (as affine)  : ${affineTRM}")

    //   val fontBounds = getFontBounds(font).toGeneralPath().getBounds2D() /// .toLTBounds()

    //   println(s"FontBounds     : ${fontBounds.toLTBounds()}")

    //   val ftrmFontBounds = affineTRM.createTransformedShape(fontBounds)
    //   println(s" apply F+TRM   : ${ftrmFontBounds.getBounds2D.toLTBounds()}")

    //   val pageAligned = pageSpaceTransform.transform(ftrmFontBounds)
    //   println(s" page-aligned  : ${pageAligned.getBounds2D.toLTBounds()}")


    //   println("\n" * 3)

    //   i = i-1
    // }

    textRenderingMatrix.concatenate(font.getFontMatrix)
    val affineTr = textRenderingMatrix.createAffineTransform()

    def trans(p: GeneralPath): Shape = {
      val sh = affineTr.createTransformedShape(p.getBounds2D)
      pageSpaceTransform.transform(sh)
    }

    var initGlyphProps = GlyphProps(
      None,
      trans(getFontBounds(font).toGeneralPath()),
      affineTr
    )

    if (font.isInstanceOf[PDType3Font]) {

      val t3Font : PDType3Font =  font.asInstanceOf[PDType3Font]
      val charProc : PDType3CharProc = t3Font.getCharProc(code)


      if (charProc != null) {
        val fontBBox : BoundingBox = t3Font.getBoundingBox()
        val glyphBBox : PDRectangle = charProc.getGlyphBBox()
        if (glyphBBox != null) {
          // PDFBOX-3850: glyph bbox could be larger than the font bbox
          glyphBBox.setLowerLeftX(math.max(fontBBox.getLowerLeftX(), glyphBBox.getLowerLeftX()))
          glyphBBox.setUpperRightX(math.min(fontBBox.getUpperRightX(), glyphBBox.getUpperRightX()))

          glyphBBox.setLowerLeftY(math.max(fontBBox.getLowerLeftY(), glyphBBox.getLowerLeftY()))
          glyphBBox.setUpperRightY(math.min(fontBBox.getUpperRightY(), glyphBBox.getUpperRightY()))

          val gpath = glyphBBox.toGeneralPath() // .getBounds2D
            initGlyphProps = initGlyphProps.copy(
              finalGlyphBounds = Some(trans(gpath))
            )
        }
      }
    } else if (font.isInstanceOf[PDVectorFont]) {


      if (font.isInstanceOf[PDTrueTypeFont]) {
        val ttFont: PDTrueTypeFont = font.asInstanceOf[PDTrueTypeFont]
        val unitsPerEm: Int= ttFont.getTrueTypeFont().getHeader().getUnitsPerEm()
        affineTr.scale(1000d / unitsPerEm, 1000d / unitsPerEm)
      }

      if (font.isInstanceOf[PDType0Font]) {
        val t0font : PDType0Font=  font.asInstanceOf[PDType0Font]
        if (t0font.getDescendantFont().isInstanceOf[PDCIDFontType2]) {
          val unitsPerEm: Int = t0font.getDescendantFont().asInstanceOf[PDCIDFontType2]
            .getTrueTypeFont()
            .getHeader()
            .getUnitsPerEm()
          affineTr.scale(1000d / unitsPerEm, 1000d / unitsPerEm)
        }
      }

      val gpath = font.asInstanceOf[PDVectorFont].getPath(code)

      initGlyphProps = initGlyphProps.copy(
        finalGlyphBounds = Some(trans(gpath))
      )
    } else if (font.isInstanceOf[PDSimpleFont]) {
      val simpleFont : PDSimpleFont = font.asInstanceOf[PDSimpleFont]

      // these two lines do not always work, e.g. for the TT fonts in file 032431.pdf
      // which is why PDVectorFont is tried first.
      val name: String = simpleFont.getEncoding().getName(code)

      val gpath = simpleFont.getPath(name)
      initGlyphProps = initGlyphProps.copy(
        finalGlyphBounds = Some(trans(gpath))
      )
    } else {
      println("Unknown font class: " + font.getClass())
    }

    initGlyphProps
  }


  private def findPageSpaceTransforms(pdPage: PDPage): PageSpaceTransforms = {
    // flip y-axis
    val flipTr = new AffineTransform()
    flipTr.translate(0, pageBoundsPdfCoords.getHeight().toDouble)
    flipTr.scale(1, -1)

    // page rotation
    val rotateTr = new AffineTransform()
    val rotation : Int = pdPage.getRotation()
    if (rotation != 0) {
      println(s"Rotated page: ${rotation}")
      rotation match {
        case 90 =>
          rotateTr.translate(pageBoundsPdfCoords.getHeight().toDouble, 0)
        case 270 =>
          rotateTr.translate(0, pageBoundsPdfCoords.getWidth().toDouble)
        case 180 =>
          rotateTr.translate(pageBoundsPdfCoords.getWidth().toDouble, pageBoundsPdfCoords.getHeight().toDouble)
        case rot =>
          println(s"Page rotation of ${rot} encountered")
      }
      rotateTr.rotate(Math.toRadians(rotation.toDouble))
    }

    // position within pageBounds
    // val transTr = AffineTransform.getTranslateInstance(-pageBounds.getLowerLeftX().toDouble, pageBounds.getLowerLeftY().toDouble)
    val transTr = AffineTransform.getTranslateInstance(
      -pageBoundsPdfCoords.getLowerLeftX().toDouble,
      pageBoundsPdfCoords.getLowerLeftY().toDouble
    )
    // val transTr = new AffineTransform()

    PageSpaceTransforms(flipTr, rotateTr, transTr)
  }

  def stripPage(document: PDDocument, page: Int): Unit = {
    val pdPage = document.getPage(page)

    pageSpaceTransform = findPageSpaceTransforms(pdPage)

    processPage(pdPage)
  }

  def getPageGeometry(): PageGeometry = {
    val pageBox = LTBounds.Floats(
      0, 0,
      pageBoundsPdfCoords.getWidth,
      pageBoundsPdfCoords.getHeight
    )

    PageGeometry(pageNum, pageBox)
  }
}

object PdfBoxTextExtractor {



  def extractPages(stableId: String@@DocumentID, pdfPath: Path): (List[(Seq[ExtractedItem], PageGeometry)], FontDefs) = {
    val pages = mutable.ListBuffer[(Seq[ExtractedItem], PageGeometry)]()
    val charIdGen = IdGenerator[CharID]()

    var currPageGeometry: PageGeometry = null

    var fontDefs: FontDefs = null

    var document: PDDocument = null
    try {
      document = PDDocument.load(pdfPath.toIO)

      val numOfPages = document.getNumberOfPages

      fontDefs = new FontDefs(numOfPages)

      for { page <- 0 until numOfPages } {
        val pdfPage = document.getPage(page)

        // val allMediaBoxes = List(
        //   pdfPage.getBBox,
        //   pdfPage.getArtBox,
        //   pdfPage.getTrimBox,
        //   pdfPage.getCropBox,
        //   pdfPage.getBleedBox,
        //   pdfPage.getMediaBox
        // ).map {b => (b.getUpperRightX, b.getUpperRightY) }


        val effectivePageBox = pdfPage.getBBox


        val extractor = new PdfBoxTextExtractor(
          pdfPage,
          charIdGen,
          PageNum(page),
          fontDefs,
          effectivePageBox
        )

        currPageGeometry = extractor.getPageGeometry()

        extractor.stripPage(document, page)

        val items = extractor.getPageItems()

        (items, currPageGeometry)
        pages.append((items, currPageGeometry))
      }

    } catch {
      case t: Throwable =>
        println(s"Error: ${t}: ${t.getCause}: ${t.getMessage}")
        t.printStackTrace()
        pages.append((Seq(), currPageGeometry))

    } finally {
      document.close()
    }

    (pages.toList, fontDefs)

  }

  def main(args: Array[String]): Unit = {
    println("Hello")

    extractPages(DocumentID("dummy"), fs.pwd / RelPath(args(0)))

  }

}


