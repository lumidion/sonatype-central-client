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
    httpSession: Session,
    userAgent: Option[String] = None
) extends GenericSonatypeClient {
  implicit val deploymentIdDecoder: Reader[DeploymentId] =
    upickle.default.reader[ujson.Str].map(str => DeploymentId(str.value))
  implicit val deploymentNameDecoder: Reader[DeploymentName] = {
    upickle.default.reader[ujson.Str].map(str => DeploymentName(str.value))
  }
  implicit val deploymentStateDecoder: Reader[DeploymentState] =
    upickle.default.reader[ujson.Str].map { str =>
      DeploymentState
        .decoder(str.value)
        .fold(errorMessage => throw new Exception(errorMessage), identity)
    }
  implicit val checkStatusResponseBodyDecoder: Reader[CheckStatusResponse] =
    macroR[CheckStatusResponse]

  private val commonHeaders = Seq(
    "Authorization" -> credentials.toAuthToken,
    "Accept"        -> "application/json",
    "Content-Type"  -> "application/json"
  ) ++ userAgent.map(agent => Seq("User-Agent" -> agent)).getOrElse(Seq.empty)

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
    } else if (response.is4xx) {
      response
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
    val deploymentIdParams = Seq(
      (UploadBundleRequestParams.BUNDLE_NAME.unapply, deploymentName.unapply)
    )
    val publishingTypeParams = publishingType
      .map(publishingType =>
        Seq(UploadBundleRequestParams.BUNDLE_PUBLISHING_TYPE.unapply -> publishingType.unapply)
      )
      .getOrElse(Seq.empty)
    val finalParams = deploymentIdParams ++ publishingTypeParams

    val response = withRetry(
      httpSession.post(
        clientUploadBundleUrl,
        headers = commonHeaders,
        params = finalParams,
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

    read[DeploymentId](response.text())
  }

  def checkStatus(
      deploymentId: DeploymentId,
      retries: Int = 3,
      timeout: FiniteDuration = 3.seconds
  ): CheckStatusResponse = {
    val params = Seq((CheckStatusRequestParams.DEPLOYMENT_ID.unapply -> deploymentId.unapply))
    val response = withRetry(
      httpSession.post(
        clientCheckStatusUrl,
        headers = commonHeaders,
        params = params,
        readTimeout = timeout.toMillis.toInt
      ),
      retries
    )

    read[CheckStatusResponse](response.text())
  }
}
