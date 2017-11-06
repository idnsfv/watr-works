package edu.umass.cs.iesl.watr
package utils

object PathUtils {

  import ammonite.{ops => fs}

  def appendTimestamp(path: String): String = {
    import java.text.SimpleDateFormat
    import java.util.Date
    val dateStamp = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date())
    s"$path-$dateStamp"
  }


  // def cleanFile(p: fs.Path, clean: Boolean): Unit = {
  //   fs.stat(p).isDir
  //   if (fs.exists(p) || clean) {
  //     fs.rm(p)
  //   }
  //   if (!fs.exists(p)) {
  //     fs.mkdir(p)
  //   }
  // }
  // def ensureDir(p: fs.Path, clean: Boolean): Unit = {
  //   if (fs.exists(p) || clean) {
  //     fs.rm(p)
  //   }
  //   if (!fs.exists(p)) {
  //     fs.mkdir(p)
  //   }
  // }
  // def ensurePath(p: fs.Path, clean: Boolean): Unit = {
  //   if (fs.exists(p) || clean) {
  //     fs.rm(p)
  //   }
  //   if (!fs.exists(p)) {
  //     fs.mkdir(p)
  //   }
  // }
}
