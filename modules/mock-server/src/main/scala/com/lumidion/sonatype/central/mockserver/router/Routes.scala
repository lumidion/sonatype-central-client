package com.lumidion.sonatype.central.mockserver.router

import com.lumidion.sonatype.central.client.core.{CheckStatusResponse, CheckPublishedStatusResponse, DeploymentId, DeploymentName, SonatypeCentralComponent}
import com.lumidion.sonatype.central.mockserver.router.Error.{
  DeploymentNotFound,
  InvalidPublishableDeployment
}
import com.lumidion.sonatype.central.mockserver.DeploymentRepository
import com.lumidion.sonatype.central.mockserver.Utils.encoders._
import com.lumidion.sonatype.central.mockserver.Utils.parseUUID

import java.util.UUID
import zio.http.{handler, Body, FormField, Handler, Header, MediaType, Request, Response, Status}
import zio.json._
import zio.ZIO

object Routes {
  def uploadBundleRoute(
      repository: DeploymentRepository
  ): Handler[Any, Response, Request, Response] = {
    Handler.fromFunctionZIO { (req: Request) =>
      if (req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`)) {
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
                case FormField.Binary(_, _, contentType, _, Some(filename))
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
            body = Body.fromString("Invalid content type. Content type must be multipart/form-data")
          )
        )
      }
    }
  }

  def getDeploymentStatusRoute(
      repository: DeploymentRepository
  ): Handler[Any, Nothing, Request, Response] = {
    handler { (req: Request) =>
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
  }

  def deleteDeployment(
      repository: DeploymentRepository
  ): Handler[Any, Nothing, (UUID, Request), Response] = {
    handler { (deploymentId: UUID, _: Request) =>
      repository.deleteDeployment(deploymentId).fold(Response.status(Status.NotFound)) { _ =>
        Response
          .status(Status.NoContent)
      }
    }
  }

  def publishValidatedDeployment(
      repository: DeploymentRepository
  ): Handler[Any, Nothing, (UUID, Request), Response] = {
    handler { (deploymentId: UUID, _: Request) =>
      repository.publishValidatedDeployment(deploymentId) match {
        case Right(_)                           => Response.status(Status.NoContent)
        case Left(InvalidPublishableDeployment) => Response.status(Status.BadRequest)
        case Left(DeploymentNotFound)           => Response.status(Status.NotFound)
      }
    }
  }
  
  def getPublishedStatusRoute(
    repository: DeploymentRepository
): Handler[Any, Nothing, Request, Response] = {
  handler { (req: Request) =>
    {
      for {
        groupId <- req
          .queryParam("namespace")
          .toRight(
            Response
              .text("No `namespace` present in query params")
              .status(Status.BadRequest)
          )
        artifactId <- req
          .queryParam("name")
          .toRight(
            Response
              .text("No `name` present in query params")
              .status(Status.BadRequest)
          )
        version <- req
          .queryParam("version")
          .toRight(
            Response
              .text("No `version` present in query params")
              .status(Status.BadRequest)
          )
        componentName = SonatypeCentralComponent(
          groupId,
          artifactId,
          version
        )
        isPublished = repository.isPublished(componentName)
        response = CheckPublishedStatusResponse(isPublished)
      } yield Response.json(response.toJson).status(Status.Ok)
    }.fold(
      errorRes => errorRes,
      successRes => successRes
    )}
  }
}
