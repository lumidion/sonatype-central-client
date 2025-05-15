package com.lumidion.sonatype.central.client.sttp.core

import com.lumidion.sonatype.central.client.core.{
  CheckPublishedStatusResponse,
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  GenericSonatypeClient,
  PublishingType,
  SonatypeCentralComponent,
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
  private val finalLoggingOptions = loggingOptions.getOrElse(LoggingOptions(None, None, None, None))
  private val baseRequest = quickRequest
    .logSettings(
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
      jsonDecoder: ResponseAs[Either[ResponseException[String, E], CheckStatusResponse]]
  ): Request[Either[ResponseException[String, E], CheckStatusResponse]] = {
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

  def checkPublishedStatusRequest[E](
      componentName: SonatypeCentralComponent,
      timeout: Long
  )(
      jsonDecoder: ResponseAs[Either[ResponseException[String, E], CheckPublishedStatusResponse]]
  ): Request[Either[ResponseException[String, E], CheckPublishedStatusResponse]] = {
    val endpoint = uri"$clientCheckPublishedUrl"
      .addParam(
        CheckPublishedRequestParams.NAMESPACE.unapply,
        componentName.groupId
      )
      .addParam(
        CheckPublishedRequestParams.NAME.unapply,
        componentName.artifactId
      )
      .addParam(
        CheckPublishedRequestParams.VERSION.unapply,
        componentName.version
      )

    baseRequest
      .get(endpoint)
      .readTimeout(timeout.milliseconds)
      .response(jsonDecoder)
  }

}
