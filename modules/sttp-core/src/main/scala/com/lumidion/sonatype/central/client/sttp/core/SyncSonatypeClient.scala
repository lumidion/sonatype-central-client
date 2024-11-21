package com.lumidion.sonatype.central.client.sttp.core

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  PublishingType,
  SonatypeCredentials
}

import java.io.File
import sttp.client4.{Response, ResponseAs, ResponseException, SyncBackend}
import sttp.client4.logging.LoggingOptions

class SyncSonatypeClient(
    credentials: SonatypeCredentials,
    backend: SyncBackend,
    loggingOptions: Option[LoggingOptions] = None,
    overrideEndpoint: Option[String] = None
) extends BaseSonatypeClient(credentials, loggingOptions, overrideEndpoint = overrideEndpoint) {
  def uploadBundle(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType]
  ): Response[Either[ResponseException[String, Exception], DeploymentId]] = {
    val request = uploadBundleRequest(localBundlePath, deploymentName, publishingType)
    request.send(backend)
  }

  def checkStatus[E](
      deploymentId: DeploymentId,
      timeout: Long = 5000
  )(
      jsonDecoder: => ResponseAs[Either[ResponseException[String, E], CheckStatusResponse]]
  ): Response[Either[ResponseException[String, E], CheckStatusResponse]] = {
    val request = checkStatusRequest(deploymentId, timeout)(jsonDecoder)
    request.send(backend)
  }

  def close(): Unit = backend.close()
}
