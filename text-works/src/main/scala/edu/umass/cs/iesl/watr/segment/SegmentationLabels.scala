package edu.umass.cs.iesl.watr
package segment

import watrmarks._


object SegmentationLabels extends DocSegLabels {

  val Cluster  = Label.auto
  val Evidence = Label.auto
  val Attr     = Label.auto
  val Order    = Label.auto
  val Rel      = Label.auto
  val Has      = Label.auto

  val Caption = Label.auto
  val Image = Label.auto

  val Para = Label.auto

  val CharRunFontBaseline     = Label.auto
  val SymbolicGlyphLine       = Label.auto

  val Glyph                   = Label.auto
  val NatLangGlyph            = Label.auto
  val SymbolicGlyph           = Label.auto
  val ContiguousGlyphs        = Label.auto
  val ContiguousGlyphBaseline = Label.auto
  val CapDescenderBand        = Label.auto
  val BaselineMidriseBand     = Label.auto

  val Fonts                   = Label.auto :: Attr

  val ExtractedItems          = Label.auto

  val PageAtom                = Label.auto

  val LeftAlignedCharCol     = Label.auto
  val RightAlignedCharCol     = Label.auto
  val PathBounds             = Label.auto

}
