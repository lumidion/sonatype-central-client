package com.lumidion.sonatype.central.mockserver

import com.lumidion.sonatype.central.client.core.{CheckStatusResponse, DeploymentId, DeploymentName}
import com.lumidion.sonatype.central.mockserver.Utils.encoders._
import com.lumidion.sonatype.central.mockserver.Utils.parseUUID

import java.util.Base64
import zio._
import zio.http._
import zio.http.Header.Authorization
import zio.http.Header.Authorization.Basic
import zio.json._
import zio.Config.Secret

object Main extends ZIOAppDefault {
  private val repository = new DeploymentRepository()

  private def parseBasic(value: String): Either[String, Authorization] = {
    try {
      val partsOfBasic = new String(Base64.getDecoder.decode(value)).split(":")
      if (partsOfBasic.length == 2) {
        Right(Basic(partsOfBasic(0), Secret(partsOfBasic(1))))
      } else {
        Left("Basic Authorization header value is not in the format username:password")
      }
    } catch {
      case _: IllegalArgumentException =>
        Left("Basic Authorization header value is not a valid base64 encoded string")
    }
  }

  private val authMiddleware = {
    Middleware.customAuthZIO(
      req => {
        for {
          rawHeaderValue <- ZIO
            .fromOption(req.headers.get("authorization"))
            .mapError(_ => Response.status(Status.Unauthorized))
          _ <- ZIO.logInfo(rawHeaderValue)
          res <- {
            val parts = rawHeaderValue.split(" ")
            if (parts.length == 2 && parts(0) == "UserToken") {
              val parsedAuth = parseBasic(parts(1))
              parsedAuth match {
                case Right(Basic(username, password))
                    if (username == "admin" && password.value.asString == "admin") =>
                  ZIO.succeed(true)
                case _ => ZIO.fail(Response.status(Status.Unauthorized))
              }
            } else {
              ZIO.fail(Response.status(Status.Unauthorized))
            }
          }
        } yield res
      },
      Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))
    )
  }

  private val routes: Routes[Any, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "publisher" / "upload" -> Handler.fromFunctionZIO {
        (req: Request) =>
          if (
            req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`)
          ) {
            for {
              form <- req.body.asMultipartForm.mapError(ex =>
                Response(
                  Status.InternalServerError,
                  body = Body.fromString(
                    s"Failed to decode body as multipart/form-data (${ex.getMessage}"
                  )
                )
              )
              response <- form.get("bundle") match {
                case Some(file) =>
                  file match {
                    case FormField.Binary(_, data, contentType, transferEncoding, Some(filename))
                        if contentType.fullType == "application/octet-stream" =>
                      val deploymentName =
                        DeploymentName(req.queryParam("name").getOrElse(filename))
                      val deploymentId = repository.createDeployment(deploymentName)

                      ZIO.succeed(
                        Response.text(deploymentId.toString)
                      )
                    case _ =>
                      ZIO.fail(
                        Response(
                          Status.BadRequest,
                          body = Body.fromString(
                            "Parameter 'bundle' must be a binary file with an application/octet-stream content type and a defined filename"
                          )
                        )
                      )
                  }
                case None =>
                  ZIO.fail(
                    Response(
                      Status.BadRequest,
                      body = Body.fromString("Missing 'bundle' from body")
                    )
                  )
              }
            } yield response
          } else {
            ZIO.fail(
              Response(
                Status.BadRequest,
                body =
                  Body.fromString("Invalid content type. Content type must be multipart/form-data")
              )
            )
          }
      },
      Method.POST / "api" / "v1" / "publisher" / "status" -> handler { (req: Request) =>
        {
          for {
            rawId <- req
              .queryParam("id")
              .toRight(Response.text("No `id` present in query params").status(Status.BadRequest))
            id <- parseUUID(rawId).toRight(
              Response
                .text("Query param `id` could not be parsed to uuid")
                .status(Status.BadRequest)
            )
            deployment <- repository
              .getDeployment(id)
              .toRight(
                Response
                  .status(Status.NotFound)
              )
            response = CheckStatusResponse(
              DeploymentId(id.toString),
              deployment._1,
              deployment._2,
              Vector.empty
            )
          } yield response
        }.fold(
          errorRes => errorRes,
          { successRes =>
            Response.json(successRes.toJson).status(Status.Ok)
          }
        )
      }
    ) @@ authMiddleware

  def run: ZIO[Any, Throwable, Nothing] = Server.serve(routes).provide(Server.default)
}
