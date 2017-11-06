package edu.umass.cs.iesl.watr
package watrcolors
package server

import scalaz.Kleisli
import scalaz.concurrent.Task

import akka.actor._
import akka.util.Timeout
import akka.pattern.{ ask }
import concurrent.duration._

import corpora._
import corpora.filesys._
import workflow._

import org.http4s
import org.http4s._
import org.http4s.{headers => H}
import org.http4s.dsl._
import org.http4s.server._
import org.http4s.server.syntax._
import org.http4s.server.staticcontent._
import org.http4s.server.blaze._
import TypeTags._
import upickle.{default => UPickle}

import scala.concurrent._


class Http4sService(
  corpusAccessApi: CorpusAccessApi,
  url: String,
  port: Int
) extends AuthServer {

  val docStore: DocumentZoningApi = corpusAccessApi.docStore
  val workflowApi: WorkflowApi = corpusAccessApi.workflowApi
  override val userbaseApi: UserbaseApi = corpusAccessApi.userbaseApi
  val corpus: Corpus = corpusAccessApi.corpus


  val assetService = resourceService(ResourceService.Config(
    basePath = "",
    pathPrefix = "/assets"
  ))

  val webJarService = resourceService(ResourceService.Config(
    basePath = "/META-INF/resources/webjars",
    pathPrefix = "/webjars"
  ))

  val corpusRoot = corpus.corpusRoot.toNIO

  val regionImageService = HttpService {
    case req @ GET -> Root / "region" / IntVar(regionId0) =>
      val regionId = RegionID(regionId0)
      val resp = corpusAccessApi.serveTargetRegionImageUpdate(regionId)
      RegionImageResponse.fold(resp)(
        err => {
          Ok(err)
        },
        bytes => {
          Ok(bytes).putHeaders(
            H.`Content-Type`(MediaType.`image/png`)
          )
        },
        path => {
          Task.now{
            StaticFile.fromFile(path.toFile(), Some(req))
              .getOrElse { Response(http4s.Status(500)(s"could not serve image ${regionId}")) }
          }
        }
      )
  }

  def htmlPage(pageName: String, user: Option[String]): Task[Response]= {
    Ok(html.ShellHtml(pageName, user).toString())
      .putHeaders(
        H.`Content-Type`(MediaType.`text/html`)
      )
  }

  // Pages
  val userRegistration = HttpService {
    case req @ GET -> Root / "register" =>
      htmlPage("Registration", None)

    case req @ POST -> Root / "login" =>
      logInService(req)
  }


  val authedPages = AuthedService[UserData] {

    case request @ GET -> Root / pageName as user =>
      pageName match {
        case "browse"    => htmlPage("BrowseCorpus", Some(user.emailAddr.unwrap))
        case "label"     => htmlPage("WatrColors", Some(user.emailAddr.unwrap))
      }

    case request @ GET -> Root  as user =>
      SeeOther(uri("/browse"))
  }

  val redirectOnFailure: AuthedService[String] = Kleisli(req => SeeOther(uri("/user/register")))

  val serveAuthorizedPages = AuthMiddleware(
    authUser,
    redirectOnFailure
  )( userStatusAndLogout orElse authedPages )


  val actorSystem = ActorSystem()
  import actorSystem.dispatcher

  lazy val browseCorpusServer = new BrowseCorpusApiListeners(corpusAccessApi)

  val userSessions = actorSystem.actorOf(SessionsActor.props(corpusAccessApi), "sessionsActor")

  val authedAutowire = AuthedService[UserData] {
    case req @ POST -> "api" /: path as user =>
      path.toList.headOption match {
        case Some("browse") =>

          import UPicklers._
          val router = ShellsideServer.route[BrowseCorpusApi](browseCorpusServer)
          Ok {
            req.req.bodyAsText().map{ body =>
              router(
                autowire.Core.Request(
                  path.toList.tail,
                  UPickle.read[Map[String, String]](body)
                )
              ).map{ responseData =>
                responseData.getBytes
              }
            }
          }

        case Some("shell") =>

          implicit val timeout = Timeout(20.seconds)

          Ok {
            req.req.bodyAsText().map{ body =>
              for {
                resp <- ask(userSessions, RoutingRequest(
                  user,
                  path.toList.tail,
                  body
                )).mapTo[RoutingResponse]
                respData <- resp.response

              } yield respData.getBytes()
            }
          }

        case Some(path) =>
          sys.error(s"autowire request to unknown url ${path}")

        case None =>
          sys.error(s"autowire request to empty path")
      }
  }


  val autowireService = authOrForbid(authedAutowire)


  val builder = BlazeBuilder.bindHttp(port, url)
    .mountService(serveAuthorizedPages)
    .mountService(webJarService)
    .mountService(assetService)
    .mountService(regionImageService, "/img")
    .mountService(autowireService, "/autowire")
    .mountService(userRegistration, "/user")

  def run(): Server = {
    builder.run
  }

  def shutdown(): Unit = {
    Await.result(
      actorSystem.terminate(),
      Duration.Inf
    )
  }
}










  // def imagePathCollector(f:File, config: Config, req:Request): Task[Option[Response]] = {
  //   // val Rel = RelationModel
  //   println(s"imagePathCollector(f:${f}, conf:${config})")
  //   println(s"                  (req:${req})")

  //   req match {
  //     case request @ GET -> Root / "region" / IntVar(regionId0) =>
  //       val regionId = RegionID(regionId0)
  //       val targetRegion = docStore.getTargetRegion(regionId)
  //       val pageId = targetRegion.page.pageId
  //       val (rPage, rDoc) = corpusAccessApi.getPageAndDocument(pageId)
  //       val tbbox = targetRegion.bbox
  //       val pbbox = rPage.bounds
  //       if (tbbox === pbbox) {
  //         val entryPath = rDoc.stableId.unwrap
  //         val imagePath = corpusRoot
  //           .resolve(entryPath)
  //           .resolve("page-images")
  //           .resolve(s"page-${rPage.pagenum.unwrap}.opt.png")

  //         println(s"pageImageService:/page-images pageId:${pageId}; imagePath: ${imagePath}")

  //         Task.now{
  //           StaticFile.fromFile(imagePath.toFile(), Some(req))
  //         }

  //       } else {
  //         println(s"Shouldn't get here yet....")
  //         val bytes = corpusAccessApi.serveTargetRegionImage(regionId)
  //         Ok(bytes).putHeaders(
  //           H.`Content-Type`(MediaType.`image/png`)
  //         ).map(Some(_))
  //       }

  //     case GET -> Root / "page-image" / IntVar(pageId) =>
  //       val (rPage, rDoc) = corpusAccessApi.getPageAndDocument(PageID(pageId))
  //       val entryPath = rDoc.stableId.unwrap
  //       val imagePath = corpusRoot
  //         .resolve(entryPath)
  //         .resolve("page-images")
  //         .resolve(s"page-${rPage.pagenum.unwrap}.opt.png")

  //       println(s"pageImageService:/page-images pageId:${pageId}; imagePath: ${imagePath}")

  //       Task.now{
  //         StaticFile.fromFile(imagePath.toFile(), Some(req))
  //       }

  //     case GET -> Root / "page-thumb" / IntVar(pageId) =>
  //       val (rPage, rDoc) = corpusAccessApi.getPageAndDocument(PageID(pageId))
  //       val entryPath = rDoc.stableId.unwrap
  //       val imagePath = corpusRoot
  //         .resolve(entryPath)
  //         .resolve("page-thumbs")
  //         .resolve(s"page-${rPage.pagenum.unwrap}.png")

  //       println(s"pageImageService:/page-thumbs pageId:${pageId}; imagePath: ${imagePath}")

  //       Task.now{
  //         StaticFile.fromFile(imagePath.toFile(), Some(req))
  //       }
  //   }
  // }

  // val pageImageService = fileService(FileService.Config(
  //   corpus.corpusRoot.toIO.toString(),
  //   pathPrefix = "/img",
  //   pathCollector = imagePathCollector
  // ))


      // val targetRegion = docStore.getTargetRegion(regionId)
      // val pageId = targetRegion.page.pageId
      // val (rPage, rDoc) = corpusAccessApi.getPageAndDocument(pageId)
      // val tbbox = targetRegion.bbox
      // val pbbox = rPage.bounds
      // if (tbbox === pbbox) {
      //   // Client is requesting the entire page
      //   val entryPath = rDoc.stableId.unwrap
      //   val imagePath = corpusRoot
      //     .resolve(entryPath)
      //     .resolve("page-images")
      //     .resolve(s"page-${rPage.pagenum.unwrap+1}.opt.png")

      //   println(s"pageImageService:/page-images pageId:${pageId}; imagePath: ${imagePath}")

      //   Task.now{
      //     StaticFile.fromFile(imagePath.toFile(), Some(req))
      //       .getOrElse { Response(http4s.Status(500)(s"could not serve image ${regionId}")) }
      //   }

      // } else {
      //   // Client is requesting a clipped page region
      //   val bytes = corpusAccessApi.serveTargetRegionImage(regionId)
      //   Ok(bytes).putHeaders(
      //     H.`Content-Type`(MediaType.`image/png`)
      //   )
      // }
