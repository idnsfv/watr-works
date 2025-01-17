package edu.umass.cs.iesl.watr
package watrcolors
package models

import cats.effect._
import io.circe.generic.JsonCodec
import org.http4s.circe._
import io.circe
import circe._
import circe.literal._
import circe.generic.auto._
import org.http4s.EntityDecoder

import tsec.passwordhashers._
import tsec.passwordhashers.imports._

import watrmarks._
import geometry._

import corpora.{RelationModel => R}


object users {

  case class User(
    id       : Int@@UserID,
    email    : String@@EmailAddr,
  )

  case class AuthInfo(
    userId: Int@@UserID,
    username : String@@Username,
    password: PasswordHash[SCrypt]
  )

}


object formdata {

  @JsonCodec case class LoginForm(
    email: String,
    password: String
  )

  object LoginForm {
    object LoginError extends Exception {
      override def getMessage: String = "Login Error"
      override def fillInStackTrace(): Throwable = this
    }

    implicit def decoder[F[_]: Effect]: EntityDecoder[F, LoginForm] = jsonOf[F, LoginForm]
  }

  case class SignupForm(
    email: String,
    username: String,
    password: String
  )

  object SignupForm {
    final object SignupError extends Exception {
      override def getMessage: String = "Signup Error"
      override def fillInStackTrace(): Throwable = this
    }
    implicit def entityD[F[_]: Effect]: EntityDecoder[F, SignupForm] = jsonOf[F, SignupForm]
  }
}

trait HttpPayloads  {

  @JsonCodec case class BBox(
    left: Double,
    top: Double,
    width: Double,
    height: Double
  )

  @JsonCodec case class LTarget(
    page: Int,
    bbox: BBox
  )


  @JsonCodec case class NewRegionLabel(
    stableId: String,
    labelChoice: Label,
    target: LTarget
  )


  @JsonCodec sealed trait ZoneUpdate
  object ZoneUpdate {
    case class MergeWith(zoneId: Int) extends ZoneUpdate
    case class SetText(gridJson: Json) extends ZoneUpdate
  }
}

@JsonCodec case class CurationWorkflowDef(
  workflow     : String,
  description  : String,
  labelSchemas : String
)

@JsonCodec sealed trait Mod
case class StatusUpdate(status: String) extends Mod
case class Unassign() extends Mod

@JsonCodec case class WorkflowMod(
  update: Mod
)

trait WorkflowCodecs  {
  import circe.generic.semiauto._
  import R._

  // implicit val encoder: Encoder[CurationWorkflowDef] = deriveEncoder
  // implicit val decoder: Decoder[CurationWorkflowDef] = deriveDecoder

  implicit val encoder2: Encoder[R.WorkflowDef] = deriveEncoder

  implicit def CurationWorkflowDef_EntityDecoder[F[_]: Effect]: EntityDecoder[F, CurationWorkflowDef] = jsonOf[F, CurationWorkflowDef]


}
