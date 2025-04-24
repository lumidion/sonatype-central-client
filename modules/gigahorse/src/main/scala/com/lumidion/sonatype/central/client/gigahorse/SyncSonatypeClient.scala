package com.lumidion.sonatype.central.client.gigahorse

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  GenericSonatypeClient,
  PublishingType,
  SonatypeCentralError,
  SonatypeCredentials
}
import com.lumidion.sonatype.central.client.core.RequestParams.{
  CheckStatusRequestParams,
  UploadBundleRequestParams
}
import com.lumidion.sonatype.central.client.core.SonatypeCentralError.{
  AuthorizationError,
  GenericError,
  GenericUserError,
  InternalServerError
}
import com.lumidion.sonatype.central.client.upickle.decoders._

import gigahorse.{
  FileBody,
  FormPart,
  FullResponse,
  HttpClient,
  HttpVerbs,
  MultipartFormBody,
  Request
}
import gigahorse.support.okhttp.Gigahorse
import java.io.File
import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import upickle.default._

class SyncSonatypeClient(
    credentials: SonatypeCredentials,
    backendClient: HttpClient,
    requestTimeoutMs: Int = 600 * 1000,
    overrideEndpoint: Option[String] = None
)(implicit ec: ExecutionContext)
    extends GenericSonatypeClient(overrideEndpoint) {

  private def paramsToString(params: Map[String, String]): String = {
    params.toVector
      .map { case (key, value) =>
        s"$key=$value"
      }
      .mkString("&")
  }

  private val defaultAwaitTimeout = requestTimeoutMs + 50

  private val authHeader: (String, String) = ("Authorization", credentials.toAuthToken)

  @tailrec
  private def withRetry(
      request: Request,
      awaitTimeout: Int = defaultAwaitTimeout,
      timeoutInterval: Int = 100,
      totalAwaitTime: Int = 0
  ): Future[Either[SonatypeCentralError, FullResponse]] = {
    var shouldRetry = false

    val res = backendClient.processFull(request).flatMap { response =>
      val statusInitialChar = response.status.toString.charAt(0)
      val is5xx             = statusInitialChar == '5'
      val is2xx             = statusInitialChar == '2'
      if (is5xx && totalAwaitTime <= awaitTimeout) {
        Thread.sleep(timeoutInterval)
        shouldRetry = true
        Future.successful {
          Left {
            InternalServerError(
              s"Sonatype Central returned ${response.status}\n${response.bodyAsString}"
            )
          }
        }

      } else if (is5xx) {
        Future.successful {
          Left {
            InternalServerError(
              s"Sonatype Central returned ${response.status}\n${response.bodyAsString}"
            )
          }
        }
      } else if (response.status == 404) {
        Future.successful(Right(response))
      } else if (response.status == 401 || response.status == 403) {
        Future.successful(
          Left(
            AuthorizationError(
              s"Sonatype Central returned ${response.status}. Error message: ${response.bodyAsString}"
            )
          )
        )
      } else if (response.status == 400) {
        Future.successful(
          Left(
            GenericUserError(
              s"Sonatype Central returned ${response.status}. Error message: ${response.bodyAsString}"
            )
          )
        )
      } else if (!(is2xx)) {
        Future.successful(
          Left(
            GenericError(
              new Exception(
                s"Sonatype Central returned ${response.status}. Error message: ${response.bodyAsString}"
              )
            )
          )
        )
      } else {
        Future.successful(Right(response))
      }
    }

    if (shouldRetry) {
      withRetry(request, timeoutInterval * 2, totalAwaitTime + timeoutInterval)
    } else {
      res
    }
  }

  def uploadBundle(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType],
      timeoutMs: Int = defaultAwaitTimeout
  ): Either[SonatypeCentralError, DeploymentId] = {
    val deploymentNameParams = Map(
      (UploadBundleRequestParams.BUNDLE_NAME.unapply, deploymentName.unapply)
    )
    val publishingTypeParams = publishingType
      .map(publishingType =>
        Map(UploadBundleRequestParams.BUNDLE_PUBLISHING_TYPE.unapply -> publishingType.unapply)
      )
      .getOrElse(Seq.empty)

    val finalParamsAsString = paramsToString(deploymentNameParams ++ publishingTypeParams)
    val finalEndpoint       = s"$clientUploadBundleUrl?$finalParamsAsString"

    val request = Gigahorse
      .url(finalEndpoint)
      .withHeaders(authHeader)
      .withRequestTimeout((timeoutMs - 50).milliseconds)
      .post(MultipartFormBody(FormPart(uploadBundleMultipartFileName, FileBody(localBundlePath))))

    // Retry timeout needs to be less than the future timeout (below), otherwise the future may time out before the retry, causing an error to be thrown.
    // All errors should be captured as Lefts.
    val res = withRetry(request, timeoutMs - 50).map(eitherRes => {
      eitherRes.map(res => DeploymentId(res.bodyAsString))
    })

    Await.result(res, timeoutMs.milliseconds)
  }

  def checkStatus(
      deploymentId: DeploymentId,
      timeoutMs: Int = 60000
  ): Either[SonatypeCentralError, Option[CheckStatusResponse]] = {
    val deploymentIdParams = Map(
      (CheckStatusRequestParams.DEPLOYMENT_ID.unapply -> deploymentId.unapply)
    )
    val finalParams   = paramsToString(deploymentIdParams)
    val finalEndpoint = s"$clientCheckStatusUrl?$finalParams"

    val request = Gigahorse
      .url(finalEndpoint)
      .withHeaders(authHeader)
      .withContentType("text/plain")
      .withRequestTimeout(timeoutMs.milliseconds)
      .withMethod(HttpVerbs.POST)

    val res = withRetry(request, timeoutMs - 50).map(eitherRes => {
      for {
        fullRes <- eitherRes
        finalRes <- {
          if (fullRes.status == 404) {
            Right(None)
          } else {
            val str = Gigahorse.asString(fullRes)
            try {
              Right(Some(read[CheckStatusResponse](str)))
            } catch {
              case ex: Throwable => Left(GenericError(ex))
            }
          }
        }
      } yield finalRes
    })

    Await.result(res, timeoutMs.milliseconds)
  }

  def deleteDeployment(
      deploymentId: DeploymentId,
      timeoutMs: Int = 60000
  ): Either[SonatypeCentralError, Option[Unit]] = {

    val finalEndpoint = clientDeleteDeploymentUrl(deploymentId)
    val request = Gigahorse
      .url(finalEndpoint)
      .withHeaders(authHeader)
      .withContentType("text/plain")
      .delete

    val res = withRetry(request, timeoutMs - 50).map { eitherRes =>
      for {
        fullRes <- eitherRes
        finalRes <- {
          if (fullRes.status == 404) {
            Right(None)
          } else {
            Right(Some(()))
          }
        }
      } yield finalRes
    }

    Await.result(res, timeoutMs.milliseconds)
  }

  def publishValidatedDeployment(
      deploymentId: DeploymentId,
      timeoutMs: Int = 60000
  ): Either[SonatypeCentralError, Option[Unit]] = {

    val finalEndpoint = clientPublishValidatedDeploymentUrl(deploymentId)
    val request = Gigahorse
      .url(finalEndpoint)
      .withHeaders(authHeader)
      .withContentType("text/plain")
      .withMethod(HttpVerbs.POST)

    val res = withRetry(request, timeoutMs - 50).map { eitherRes =>
      for {
        fullRes <- eitherRes
        finalRes <- {
          if (fullRes.status == 404) {
            Right(None)
          } else {
            Right(Some(()))
          }
        }
      } yield finalRes
    }

    Await.result(res, timeoutMs.milliseconds)
  }
}
