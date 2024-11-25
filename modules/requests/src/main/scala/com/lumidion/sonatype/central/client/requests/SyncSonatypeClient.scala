package com.lumidion.sonatype.central.client.requests

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  GenericSonatypeClient,
  PublishingType,
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

import java.io.File
import requests.{BaseSession, MultiItem, MultiPart, Session}
import scala.annotation.tailrec
import upickle.default._

class SyncSonatypeClient(
    credentials: SonatypeCredentials,
    readTimeout: Int = 300 * 1000,
    connectTimeout: Int = 5000,
    overrideEndpoint: Option[String] = None
) extends GenericSonatypeClient(overrideEndpoint) {

  private val authHeader = Map("Authorization" -> credentials.toAuthToken)

  private val defaultAwaitTimeout = 120 * 1000

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

  def checkStatus(
      deploymentId: DeploymentId,
      timeout: Int = 5000
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
}
