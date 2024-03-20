package com.lumidion.sonatype.central.client.sttp.core

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  GenericSonatypeClient,
  PublishingType,
  SonatypeCredentials
}
import com.lumidion.sonatype.central.client.core.RequestParams._

import java.io.File
import scala.concurrent.duration.DurationLong
import sttp.client4.{
  asString,
  multipartFile,
  quickRequest,
  Request,
  ResponseAs,
  ResponseException,
  UriContext
}
import sttp.client4.logging.LoggingOptions
import sttp.model.HeaderNames

abstract class BaseSonatypeClient(
    credentials: SonatypeCredentials,
    loggingOptions: Option[LoggingOptions] = None
) extends GenericSonatypeClient {
  private val baseRequest = quickRequest
    .logSettings(
      logRequestBody = loggingOptions.flatMap(_.logRequestBody),
      logResponseBody = loggingOptions.flatMap(_.logResponseBody),
      logRequestHeaders = loggingOptions.flatMap(_.logRequestHeaders),
      logResponseHeaders = loggingOptions.flatMap(_.logResponseHeaders)
    )

  private def authorizationHeader: (String, String) =
    (HeaderNames.Authorization, credentials.toAuthToken)

  def uploadBundleRequest(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType]
  ): Request[Either[ResponseException[String, Exception], DeploymentId]] = {
    val endpoint = uri"$clientUploadBundleUrl"
      .addParam(
        UploadBundleRequestParams.BUNDLE_PUBLISHING_TYPE.unapply,
        publishingType.map(_.unapply)
      )
      .addParam(UploadBundleRequestParams.BUNDLE_NAME.unapply, deploymentName.unapply)

    val responseParser =
      asString.mapWithMetadata(ResponseAs.deserializeRightCatchingExceptions(DeploymentId.apply))

    baseRequest
      .post(endpoint)
      .headers(
        Map(authorizationHeader)
      )
      .contentType("multipart/form-data")
      .multipartBody(
        multipartFile(
          uploadBundleMultipartFileName,
          localBundlePath
        )
      )
      .response(responseParser)
  }

  def checkStatusRequest[E](
      deploymentId: DeploymentId,
      timeout: Long
  )(
      jsonDecoder: ResponseAs[Either[ResponseException[String, E], CheckStatusResponse]]
  ): Request[Either[ResponseException[String, E], CheckStatusResponse]] = {
    val endpoint = uri"$clientCheckStatusUrl".addParam(
      CheckStatusRequestParams.DEPLOYMENT_ID.unapply,
      deploymentId.unapply
    )

    baseRequest
      .post(endpoint)
      .headers(
        Map(authorizationHeader)
      )
      .readTimeout(timeout.milliseconds)
      .response(jsonDecoder)
  }
}
