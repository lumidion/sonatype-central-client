package com.lumidion.sonatype.central.client.requests

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
import com.lumidion.sonatype.central.client.core.RequestParams.{
  CheckPublishedRequestParams,
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

import java.io.File
import requests.{BaseSession, MultiItem, MultiPart, Session}
import scala.annotation.tailrec
import upickle.default._

class SyncSonatypeClient(
    credentials: SonatypeCredentials,
    readTimeout: Int = 600 * 1000,
    connectTimeout: Int = 60 * 1000,
    overrideEndpoint: Option[String] = None
) extends GenericSonatypeClient(overrideEndpoint) {

  private val authHeader = Map("Authorization" -> credentials.toAuthToken)

  private val defaultAwaitTimeout = readTimeout + 50

  private def paramsToString(params: Map[String, String]): String = {
    params.toVector
      .map { case (key, value) =>
        s"$key=$value"
      }
      .mkString("&")
  }

  private val session: Session = requests.Session(
    readTimeout = readTimeout,
    connectTimeout = connectTimeout,
    maxRedirects = 0,
    check = false,
    headers = BaseSession.defaultHeaders ++ authHeader
  )

  @tailrec
  private def withRetry(
      request: => requests.Response,
      awaitTimeout: Int = defaultAwaitTimeout,
      timeoutInterval: Int = 100,
      totalAwaitTime: Int = 0
  ): requests.Response = {
    val response =
      try {
        request
      } catch {
        case ex: Throwable => throw GenericError(ex)
      }
    if (response.is5xx && totalAwaitTime <= awaitTimeout) {
      Thread.sleep(timeoutInterval)
      withRetry(request, timeoutInterval * 2, totalAwaitTime + timeoutInterval)
    } else if (response.is5xx) {
      throw InternalServerError(
        s"Sonatype Central returned ${response.statusCode}\n${response.text()}"
      )
    } else if (response.statusCode == 404) {
      response
    } else if (response.statusCode == 401 || response.statusCode == 403) {
      throw AuthorizationError(
        s"Sonatype Central returned ${response.statusCode}. Error message: ${response.text()}"
      )
    } else if (response.statusCode == 400) {
      throw GenericUserError(
        s"Sonatype Central returned ${response.statusCode}. Error message: ${response.text()}"
      )
    } else if (!response.is2xx) {
      throw GenericError(
        new Exception(
          s"Sonatype Central returned ${response.statusCode}. Error message: ${response.text()}"
        )
      )
    } else {
      response
    }
  }

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
    * @param timeout
    *   The maximum amount of time (in ms) that the function has to retry the call if it receives an
    *   internal server error
    * @example
    *   {{{
    *   import com.lumidion.sonatype.central.client.core.{
    *       DeploymentName,
    *       PublishingType,
    *       SonatypeCredentials
    *     }
    *   import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
    *   import java.io.File
    *
    *   val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    *   val client              = new SyncSonatypeClient(sonatypeCredentials)
    *   val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    *   val id = client.uploadBundleFromFile(
    *     zippedBundle,
    *     DeploymentName("com.testing.project-1.0.0"),
    *     Some(PublishingType.USER_MANAGED)
    *   )
    *   }}}
    * @return
    *   The deployment id
    */
  def uploadBundleFromFile(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType],
      timeout: Int = defaultAwaitTimeout
  ): DeploymentId = {
    val item = MultiItem(
      uploadBundleMultipartFileName,
      localBundlePath,
      localBundlePath.getName
    )

    uploadBundleInternal(
      item,
      deploymentName,
      publishingType,
      timeout = timeout
    )
  }

  /** Uploads a bundle to Sonatype Central for validation and potential deployment
    *
    * @param bundleAsBytes
    *   The file bundle loaded into an array
    * @param deploymentName
    *   The deployment name that will be shown in the ui in the Sonatype Central portal
    * @param publishingType
    *   The publishing type. `AUTOMATIC` will publish the deployment immediately upon validation,
    *   whereas `USER_MANAGED` will require the user to manually publish it in the console or via
    *   another api call.
    * @param timeout
    *   The maximum amount of time (in ms) that the function has to retry the call if it receives an
    *   internal server error
    * @example
    *   {{{
    *   import com.lumidion.sonatype.central.client.core.{
    *       DeploymentName,
    *       PublishingType,
    *       SonatypeCredentials
    *     }
    *   import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
    *   import java.io.File
    *   import java.nio.file.Files
    *
    *   val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    *   val client              = new SyncSonatypeClient(sonatypeCredentials)
    *   val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *   val bundleAsBytes       = Files.readAllBytes(zippedBundle.toPath)
    *
    *   val id = client.uploadBundleFromBytes(
    *     bundleAsBytes,
    *     DeploymentName("com.testing.project-1.0.0"),
    *     Some(PublishingType.USER_MANAGED)
    *   )
    *   }}}
    * @return
    *   The deployment id
    */
  def uploadBundleFromBytes(
      bundleAsBytes: Array[Byte],
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType],
      timeout: Int = defaultAwaitTimeout
  ): DeploymentId = {
    val item = MultiItem(
      uploadBundleMultipartFileName,
      bundleAsBytes,
      s"${deploymentName.unapply}-bundle"
    )

    uploadBundleInternal(
      item,
      deploymentName,
      publishingType,
      timeout = timeout
    )
  }

  private def uploadBundleInternal(
      bundleItem: MultiItem,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType],
      timeout: Int
  ): DeploymentId = {
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

    val response = withRetry(
      session.post(
        finalEndpoint,
        data = MultiPart(
          bundleItem
        )
      ),
      awaitTimeout = timeout
    )

    DeploymentId((response.text()))
  }

  /** Checks the current status for a deployment
    * @param deploymentId
    *   The deployment id (generally received from one of the uploadBundle functions)
    * @param timeout
    *   The maximum amount of time (in ms) that the function has to retry the call if it receives an
    *   internal server error
    * @example
    *   {{{
    *   import com.lumidion.sonatype.central.client.core.{
    *       DeploymentName,
    *       PublishingType,
    *       SonatypeCredentials
    *     }
    *   import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
    *   import java.io.File
    *
    *   val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    *   val client              = new SyncSonatypeClient(sonatypeCredentials)
    *   val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    *   val id = client.uploadBundleFromFile(
    *     zippedBundle,
    *     DeploymentName("com.testing.project-1.0.0"),
    *     Some(PublishingType.USER_MANAGED)
    *   )
    *
    *   val res = client.checkStatus(id)
    *   }}}
    * @return
    *   `None` if Sonatype Central returns `404` for the request. Otherwise, assuming no error,
    *   `Some(CheckStatusResponse)`.
    */
  def checkStatus(
      deploymentId: DeploymentId,
      timeout: Int = 60000
  ): Option[CheckStatusResponse] = {
    val deploymentIdParams = Map(
      (CheckStatusRequestParams.DEPLOYMENT_ID.unapply -> deploymentId.unapply)
    )
    val finalParams   = paramsToString(deploymentIdParams)
    val finalEndpoint = s"$clientCheckStatusUrl?$finalParams"

    val response = withRetry(
      session.post(
        finalEndpoint,
        headers = Map("Content-Type" -> "text/plain")
      ),
      awaitTimeout = timeout
    )

    if (response.statusCode == 404) {
      None
    } else {
      Some(read[CheckStatusResponse](response.text()))
    }
  }

  /** Deletes a deployment that has not yet been published.
    * @param deploymentId
    *   The deployment id (generally received from one of the uploadBundle functions)
    * @param timeout
    *   The maximum amount of time (in ms) that the function has to retry the call if it receives an
    *   internal server error
    * @example
    *   {{{
    *   import com.lumidion.sonatype.central.client.core.{
    *       DeploymentName,
    *       PublishingType,
    *       SonatypeCredentials
    *     }
    *   import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
    *   import java.io.File
    *
    *   val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    *   val client              = new SyncSonatypeClient(sonatypeCredentials)
    *   val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    *   val id = client.uploadBundleFromFile(
    *     zippedBundle,
    *     DeploymentName("com.testing.project-1.0.0"),
    *     Some(PublishingType.USER_MANAGED)
    *   )
    *
    *   val res = client.deleteDeployment(id)
    *   }}}
    * @return
    *   `None` if Sonatype Central returns `404` for the request. Otherwise, assuming no error,
    *   `Some(())`.
    */
  def deleteDeployment(
      deploymentId: DeploymentId,
      timeout: Int = 60000
  ): Option[Unit] = {

    val finalEndpoint = clientDeleteDeploymentUrl(deploymentId)

    val response = withRetry(
      session.delete(
        finalEndpoint,
        headers = Map("Content-Type" -> "text/plain")
      ),
      awaitTimeout = timeout
    )

    if (response.statusCode == 404) {
      None
    } else {
      Some(())
    }
  }

  /** Publishes a deployment that is currently in the "validated" state.
    * @param deploymentId
    *   The deployment id (generally received from one of the uploadBundle functions)
    * @param timeout
    *   The maximum amount of time (in ms) that the function has to retry the call if it receives an
    *   internal server error
    * @example
    *   {{{
    *   import com.lumidion.sonatype.central.client.core.{
    *       DeploymentName,
    *       PublishingType,
    *       SonatypeCredentials
    *     }
    *   import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
    *   import java.io.File
    *
    *   val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    *   val client              = new SyncSonatypeClient(sonatypeCredentials)
    *   val zippedBundle        = new File("com-testing-project-1.0.0.zip")
    *
    *   val id = client.uploadBundleFromFile(
    *     zippedBundle,
    *     DeploymentName("com.testing.project-1.0.0"),
    *     Some(PublishingType.USER_MANAGED)
    *   )
    *
    *   val res = client.publishValidatedDeployment(id)
    *   }}}
    * @return
    *   `None` if Sonatype Central returns `404` for the request. Otherwise, assuming no error,
    *   `Some(())`.
    */
  def publishValidatedDeployment(
      deploymentId: DeploymentId,
      timeout: Int = 60000
  ): Option[Unit] = {

    val finalEndpoint = clientPublishValidatedDeploymentUrl(deploymentId)

    val response = withRetry(
      session.post(
        finalEndpoint,
        headers = Map("Content-Type" -> "text/plain")
      ),
      awaitTimeout = timeout
    )

    if (response.statusCode == 404) {
      None
    } else {
      Some(())
    }
  }

  /** Checks whether a component is published in Sonatype Central.
    *
    * @param component
    *   The SonatypeCentralComponent containing groupId, artifactId, and version.
    * @param timeout
    *   The maximum amount of time (in ms) to retry the call if it receives an internal server
    *   error.
    * @example
    *   {{{
    *   import com.lumidion.sonatype.central.client.core.{
    *       CheckPublishedStatusResponse,
    *       SonatypeCentralComponent,
    *       SonatypeCredentials
    *     }
    *   import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
    *
    *   val sonatypeCredentials = SonatypeCredentials("admin", "admin")
    *   val client              = new SyncSonatypeClient(sonatypeCredentials)
    *
    *   val component = SonatypeCentralComponent("com.example", "artifact", "1.0.0")
    *   val response = client.checkPublishedStatus(component)
    *
    *   response match {
    *     case Some(status) => println(s"Published: ${status.published}")
    *     case None         => println("Component not found")
    *   }
    *   }}}
    * @return
    *   `Some(CheckPublishedStatusResponse)` if the component exists, `None` if it doesn't exist.
    */
  def checkPublishedStatus(
      component: SonatypeCentralComponent,
      timeout: Int = 5000
  ): Option[CheckPublishedStatusResponse] = {

    val params = Map(
      CheckPublishedRequestParams.NAMESPACE.unapply -> component.groupId,
      CheckPublishedRequestParams.NAME.unapply      -> component.artifactId,
      CheckPublishedRequestParams.VERSION.unapply   -> component.version
    )

    val finalParams   = paramsToString(params)
    val finalEndpoint = s"$clientCheckPublishedUrl?$finalParams"
    val response = withRetry(
      session.get(finalEndpoint),
      awaitTimeout = timeout
    )

    if (response.statusCode == 404) {
      None
    } else {
      Some(read[CheckPublishedStatusResponse](response.text()))
    }
  }
}
