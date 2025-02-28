package com.lumidion.sonatype.central.client.sttp.core

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  GenericSonatypeClient,
  IsArtifactPublishedResponse,
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
import sttp.model.MediaType.MultipartFormData

class BaseSonatypeClient(
    credentials: SonatypeCredentials,
    loggingOptions: Option[LoggingOptions] = None,
    overrideEndpoint: Option[String] = None
) extends GenericSonatypeClient(overrideEndpoint) {
  private val finalLoggingOptions = loggingOptions.getOrElse(
    LoggingOptions(
      logRequestBody = None,
      logResponseBody = None,
      logRequestHeaders = None,
      logResponseHeaders = None
    )
  )
  private val baseRequest = quickRequest
    .loggingOptions(
      logRequestBody = finalLoggingOptions.logRequestBody,
      logResponseBody = finalLoggingOptions.logResponseBody,
      logRequestHeaders = finalLoggingOptions.logRequestHeaders,
      logResponseHeaders = finalLoggingOptions.logResponseHeaders
    )
    .headers(Map(HeaderNames.Authorization -> credentials.toAuthToken))

  def uploadBundleRequest(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType]
  ): Request[Either[ResponseException[String], DeploymentId]] = {
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
      .contentType(MultipartFormData.toString())
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
      jsonDecoder: ResponseAs[Either[ResponseException[String], CheckStatusResponse]]
  ): Request[Either[ResponseException[String], CheckStatusResponse]] = {
    val endpoint = uri"$clientCheckStatusUrl".addParam(
      CheckStatusRequestParams.DEPLOYMENT_ID.unapply,
      deploymentId.unapply
    )

    baseRequest
      .post(endpoint)
      .readTimeout(timeout.milliseconds)
      .response(jsonDecoder)
  }

  def deleteDeploymentRequest(deploymentId: DeploymentId): Request[Unit] = {
    val endpoint = uri"${clientDeleteDeploymentUrl(deploymentId)}"

    baseRequest
      .delete(endpoint)
      .mapResponse(_ => ())
  }

  def publishValidatedDeploymentRequest(deploymentId: DeploymentId): Request[Unit] = {
    val endpoint = uri"${clientPublishValidatedDeploymentUrl(deploymentId)}"

    baseRequest
      .post(endpoint)
      .mapResponse(_ => ())
  }

  def isArtifactPublishedRequest(
      namespace: String,
      name: String,
      version: String
  )(
      jsonDecoder: => ResponseAs[Either[ResponseException[String], IsArtifactPublishedResponse]]
  ): Request[Either[ResponseException[String], Boolean]] = {
    val endpoint = uri"$clientIsArtifactPublishedUrl"
      .addParam("namespace", namespace)
      .addParam("name", name)
      .addParam("version", version)
    baseRequest
      .get(endpoint)
      .response(jsonDecoder)
      .mapResponseRight[ResponseException[String], IsArtifactPublishedResponse, Boolean](
        _.published
      )
  }
}
