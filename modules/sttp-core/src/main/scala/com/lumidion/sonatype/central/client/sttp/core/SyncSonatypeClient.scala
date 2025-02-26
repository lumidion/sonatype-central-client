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
) {

  private val baseClient =
    new BaseSonatypeClient(credentials, loggingOptions, overrideEndpoint = overrideEndpoint)

  /** Uploads a bundle to Sonatype Central for validation and potential deployment
    *
    * @param localBundlePath
    *   The file to be uploaded
    * @param deploymentName
    *   The deployment name that will be shown in the ui in the Sonatype Central portal
    * @param publishingType
    *   The publishing type. `AUTOMATIC` will publish the deployment immediately upon validation,
    *   whereas `USER_MANAGED` will require the user to manually publish it in the console or via
    *   another api call.
    * @example
    *   {{{
    * import com.lumidion.sonatype.central.client.core.{
    *   DeploymentName,
    *   PublishingType,
    *   SonatypeCredentials
    * }
    * import java.io.File
    * import sttp.client4.httpurlconnection.HttpURLConnectionBackend
    *
    * val backend             = HttpURLConnectionBackend()
    * val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    * val client              = new SyncSonatypeClient(sonatypeCredentials, backend)
    * val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    * for {
    *   id <- client
    *     .uploadBundle(
    *       zippedBundle,
    *       DeploymentName("com.testing.project-1.0.0"),
    *       Some(PublishingType.USER_MANAGED)
    *     ).body
    * } yield ()
    *   }}}
    * @return
    *   A sttp response with a json body parsed to a deployment id
    */
  def uploadBundle(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType]
  ): Response[Either[ResponseException[String, Exception], DeploymentId]] = {
    val request = baseClient.uploadBundleRequest(localBundlePath, deploymentName, publishingType)
    request.send(backend)
  }

  /** Checks the status of an existing deployment
    *
    * @param deploymentId
    *   The deployment id (generally received from the `uploadBundle` function)
    * @param timeout
    *   The read timeout for the request
    * @param jsonDecoder
    *   The sttp json decoder (generally imported from another lib). E.g.
    *   sttp.client4.ziojson.asJson from the "com.softwaremill.sttp.client4" %% "zio-json" lib
    * @tparam E
    *   The error type of the json decoder
    * @example
    *   {{{
    * import com.lumidion.sonatype.central.client.core.{
    *   CheckStatusResponse
    *   DeploymentName,
    *   PublishingType,
    *   SonatypeCredentials
    * }
    * import java.io.File
    * import sttp.client4.httpurlconnection.HttpURLConnectionBackend
    * import sttp.client4.ziojson.asJson
    *
    * val backend             = HttpURLConnectionBackend()
    * val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    * val client              = new SyncSonatypeClient(sonatypeCredentials, backend)
    * val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    * for {
    *   id <- client
    *     .uploadBundle(
    *       zippedBundle,
    *       DeploymentName("com.testing.project-1.0.0"),
    *       Some(PublishingType.USER_MANAGED)
    *     ).body
    *   status <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
    * } yield ()
    *   }}}
    * @return
    *   A sttp response that contains a parsed json body: status <-
    *   client.checkStatus(id)(asJson[CheckStatusResponse]).body
    */
  def checkStatus[E](
      deploymentId: DeploymentId,
      timeout: Long = 5000
  )(
      jsonDecoder: => ResponseAs[Either[ResponseException[String, E], CheckStatusResponse]]
  ): Response[Either[ResponseException[String, E], CheckStatusResponse]] = {
    val request = baseClient.checkStatusRequest(deploymentId, timeout)(jsonDecoder)
    request.send(backend)
  }

  /** Deletes a deployment that has not yet been published.
    *
    * @param deploymentId
    *   The deployment id (generally received from the `uploadBundle` function)
    * @example
    *   {{{
    * import com.lumidion.sonatype.central.client.core.{
    *   DeploymentName,
    *   PublishingType,
    *   SonatypeCredentials
    * }
    * import java.io.File
    * import sttp.client4.httpurlconnection.HttpURLConnectionBackend
    *
    * val backend             = HttpURLConnectionBackend()
    * val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    * val client              = new SyncSonatypeClient(sonatypeCredentials, backend)
    * val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    * for {
    *   id <- client
    *     .uploadBundle(
    *       zippedBundle,
    *       DeploymentName("com.testing.project-1.0.0"),
    *       Some(PublishingType.USER_MANAGED)
    *     ).body
    *   _ = client.deleteDeployment(id)
    * } yield ()
    *   }}}
    * @return
    *   `Response[Unit]`
    */
  def deleteDeployment(deploymentId: DeploymentId): Response[Unit] = {
    val request = baseClient.deleteDeploymentRequest(deploymentId)
    request.send(backend)
  }

  /** Publishes a deployment that is currently in the "validated" state.
    *
    * @param deploymentId
    *   The deployment id (generally received from the `uploadBundle` function)
    * @example
    *   {{{
    * import com.lumidion.sonatype.central.client.core.{
    *   DeploymentName,
    *   PublishingType,
    *   SonatypeCredentials
    * }
    * import java.io.File
    * import sttp.client4.httpurlconnection.HttpURLConnectionBackend
    *
    * val backend             = HttpURLConnectionBackend()
    * val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    * val client              = new SyncSonatypeClient(sonatypeCredentials, backend)
    * val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    * for {
    *   id <- client
    *     .uploadBundle(
    *       zippedBundle,
    *       DeploymentName("com.testing.project-1.0.0"),
    *       Some(PublishingType.USER_MANAGED)
    *     ).body
    *   _ = client.publishValidatedDeployment(id)
    * } yield ()
    *   }}}
    * @return
    *   `Response[Unit]`
    */
  def publishValidatedDeployment(deploymentId: DeploymentId): Response[Unit] = {
    val request = baseClient.publishValidatedDeploymentRequest(deploymentId)
    request.send(backend)
  }

  /** Closes the client http backend
    */
  def close(): Unit = backend.close()
}
