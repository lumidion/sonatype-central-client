package com.lumidion.sonatype.central.client.requests

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  DeploymentState,
  GenericSonatypeClient,
  PublishingType,
  SonatypeCredentials
}
import com.lumidion.sonatype.central.client.core.RequestParams.{
  CheckStatusRequestParams,
  UploadBundleRequestParams
}

import java.io.File
import requests.{MultiItem, MultiPart, Session}
import scala.annotation.tailrec
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import upickle.default._

class SyncSonatypeClient(
    credentials: SonatypeCredentials,
    userAgent: Option[String] = None
) extends GenericSonatypeClient {

  implicit protected val deploymentIdDecoder: Reader[DeploymentId] =
    upickle.default.reader[ujson.Str].map(str => DeploymentId(str.value))
  implicit protected val deploymentNameDecoder: Reader[DeploymentName] = {
    upickle.default.reader[ujson.Str].map(str => DeploymentName(str.value))
  }
  implicit protected val deploymentStateDecoder: Reader[DeploymentState] =
    upickle.default.reader[ujson.Str].map { str =>
      DeploymentState
        .decoder(str.value)
        .fold(errorMessage => throw new Exception(errorMessage), identity)
    }
  implicit protected val checkStatusResponseBodyDecoder: Reader[CheckStatusResponse] =
    macroR[CheckStatusResponse]

  private val authHeader = Map("Authorization" -> credentials.toAuthToken)

  private val userAgentHeader =
    userAgent.map(agent => Map("User-Agent" -> agent)).getOrElse(Map.empty)

  private val jsonHeaders = Map(
    "Accept"       -> "application/json",
    "Content-Type" -> "application/json"
  )

  private def paramsToString(params: Map[String, String]): String = {
    params.toVector
      .map { case (key, value) =>
        s"$key=$value"
      }
      .mkString("&")
  }

  private val http: Session = requests.Session(
    readTimeout = 30000,
    connectTimeout = 5000,
    maxRedirects = 0,
    check = false,
    headers = authHeader ++ userAgentHeader
  )

  @tailrec
  private def withRetry(
      request: => requests.Response,
      retries: Int = 10,
      initialTimeout: FiniteDuration = 100.millis
  ): requests.Response = {
    val response = request
    if (response.is5xx && retries > 0) {
      Thread.sleep(initialTimeout.toMillis)
      withRetry(request, retries - 1, initialTimeout * 2)
    } else if (!response.is2xx) {
      throw new Exception(
        s"${response.url} returned ${response.statusCode}\n${response.text()}"
      )
    } else {
      response
    }
  }

  def uploadBundle(
      localBundlePath: File,
      deploymentName: DeploymentName,
      publishingType: Option[PublishingType],
      retries: Int = 3,
      timeout: FiniteDuration = 10.minutes
  ): DeploymentId = {
    val deploymentIdParams = Map(
      (UploadBundleRequestParams.BUNDLE_NAME.unapply, deploymentName.unapply)
    )
    val publishingTypeParams = publishingType
      .map(publishingType =>
        Map(UploadBundleRequestParams.BUNDLE_PUBLISHING_TYPE.unapply -> publishingType.unapply)
      )
      .getOrElse(Seq.empty)
    val finalParamsAsString = paramsToString(deploymentIdParams ++ publishingTypeParams)
    val finalEndpoint       = s"$clientUploadBundleUrl?$finalParamsAsString"

    val response = withRetry(
      http.post(
        finalEndpoint,
        data = MultiPart(
          MultiItem(
            uploadBundleMultipartFileName,
            localBundlePath,
            localBundlePath.getName
          )
        ),
        readTimeout = timeout.toMillis.toInt
      ),
      retries
    )

    DeploymentId((response.text()))
  }

  def checkStatus(
      deploymentId: DeploymentId,
      retries: Int = 3,
      timeout: FiniteDuration = 3.seconds
  ): CheckStatusResponse = {
    val deploymentIdParams = Map(
      (CheckStatusRequestParams.DEPLOYMENT_ID.unapply -> deploymentId.unapply)
    )
    val finalParams   = paramsToString(deploymentIdParams)
    val finalEndpoint = s"$clientCheckStatusUrl?$finalParams"

    val response = withRetry(
      http.post(
        finalEndpoint,
        headers = jsonHeaders,
        readTimeout = timeout.toMillis.toInt
      ),
      retries
    )

    read[CheckStatusResponse](response.text())
  }
}
