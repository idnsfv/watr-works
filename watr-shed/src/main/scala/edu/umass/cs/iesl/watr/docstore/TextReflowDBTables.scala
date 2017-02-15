package edu.umass.cs.iesl.watr
package docstore

import doobie.imports._
import scalaz.concurrent.Task

import databasics._

class TextReflowDBTables(
  val xa: Transactor[Task]
) extends DoobiePredef {

  val createDocumentTable: Update0 = sql"""
      CREATE TABLE document (
        document      SERIAL PRIMARY KEY,
        stable_id     VARCHAR(128) UNIQUE
      );
      CREATE INDEX document_stable_id ON document USING hash (stable_id);
    """.update

  val createPageTable: Update0 = sql"""
      CREATE TABLE page (
        page        SERIAL PRIMARY KEY,
        document    INTEGER REFERENCES document NOT NULL,
        pagenum     SMALLINT,
        imageclip   INTEGER REFERENCES imageclips,
        pageimg     BYTEA,
        bleft       INTEGER,
        btop        INTEGER,
        bwidth      INTEGER,
        bheight     INTEGER
      );
    """.update

  val createImageClipTable: Update0 = sql"""
      CREATE TABLE imageclips (
        imageclip   SERIAL PRIMARY KEY,
        image       BYTEA
      );
    """.update

  val createZoneTable: Update0 = sql"""
      CREATE TABLE zone (
        zone        SERIAL PRIMARY KEY,
        document    INTEGER REFERENCES document NOT NULL
      );
    """.update


  // zone - label :: * - *
  val createZoneToLabelTable: Update0 = sql"""
      CREATE TABLE zone_to_label (
        zone         INTEGER REFERENCES zone NOT NULL,
        label        INTEGER REFERENCES label NOT NULL,
        PRIMARY KEY (zone, label)
      );
    """.update

  // zone - targetregion :: * - * (NB not sure if this is the right way to connect these)
  val createZoneToTargetRegion: Update0 = sql"""
      CREATE TABLE zone_to_targetregion (
        zone          INTEGER REFERENCES zone NOT NULL,
        targetregion  INTEGER REFERENCES targetregion NOT NULL
      );
      CREATE UNIQUE INDEX uniq__zone_to_targetregion ON zone_to_targetregion (zone, targetregion);
    """.update

  val createTargetRegion: Update0 = sql"""
      CREATE TABLE targetregion (
        targetregion  SERIAL PRIMARY KEY,
        page          INTEGER REFERENCES page,
        imageclip     INTEGER REFERENCES imageclips,
        bleft         INTEGER,
        btop          INTEGER,
        bwidth        INTEGER,
        bheight       INTEGER,
        uri           VARCHAR(256) UNIQUE NOT NULL
      );
      CREATE INDEX targetregion_uri ON targetregion USING hash (uri);
    """.update


  val createTextReflowTable: Update0 = sql"""
      CREATE TABLE textreflow (
        textreflow  SERIAL PRIMARY KEY,
        reflow      TEXT NOT NULL,
        zone        INTEGER REFERENCES zone
      )
    """.update


  val createLabelTable: Update0 = sql"""
      CREATE TABLE label (
        label          SERIAL PRIMARY KEY,
        key            VARCHAR(50) UNIQUE NOT NULL
      );
      CREATE INDEX label_key ON label USING hash (key);
    """.update

  val createLabelerTable: Update0 = sql"""
      CREATE TABLE labelers (
        labeler     SERIAL PRIMARY KEY,
        widget      TEXT
      );
    """.update

  // title/author
  // created/todo/assigned/skipped/done
  val createLabelingTaskTable: Update0 = sql"""
      CREATE TABLE labelingtasks (
        labelingtask     SERIAL PRIMARY KEY,
        taskname         VARCHAR(128),
        progress         VARCHAR(64),
        labeler          INTEGER REFERENCES labelers
      );
    """.update

  def createAll = for {
    _ <- putStrLn("create imageclips")
    _ <- createImageClipTable.run
    _ <- putStrLn("create doc")
    _ <- createDocumentTable.run
    _ <- putStrLn("create page")
    _ <- createPageTable.run
    _ <- putStrLn("create label")
    _ <- createLabelTable.run
    _ <- putStrLn("create targetregion")
    _ <- createTargetRegion.run
    _ <- putStrLn("create zone")
    _ <- createZoneTable.run
    _ <- putStrLn("create z-tr")
    _ <- createZoneToTargetRegion.run
    _ <- putStrLn("create z-lbl")
    _ <- createZoneToLabelTable.run
    _ <- putStrLn("create textreflow")
    _ <- createTextReflowTable.run
    _ <- putStrLn("create labelers")
    _ <- createLabelerTable.run
    _ <- putStrLn("create labelingtasks")
    _ <- createLabelingTaskTable.run
  } yield ()

  val dropAll: Update0 = sql"""
    DROP TABLE IF EXISTS labelingtasks;
    DROP TABLE IF EXISTS labelers;
    DROP TABLE IF EXISTS textreflow;
    DROP TABLE IF EXISTS zone_to_targetregion;
    DROP TABLE IF EXISTS zone_to_label;
    DROP TABLE IF EXISTS zone;
    DROP TABLE IF EXISTS label;
    DROP TABLE IF EXISTS targetregion_image;
    DROP TABLE IF EXISTS targetregion;
    DROP TABLE IF EXISTS page;
    DROP TABLE IF EXISTS imageclips;
    DROP TABLE IF EXISTS document;
  """.update

  def dropAndCreateAll = for{
    _ <- dropAll.run
    _ <- createAll
  } yield ()

  def dropAndCreate = {
    val run = for{
      _ <- dropAll.run
      _ <- createAll
    } yield ()
    run.transact(xa)
  }

}
