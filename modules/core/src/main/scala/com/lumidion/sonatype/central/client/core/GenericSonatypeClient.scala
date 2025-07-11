package com.lumidion.sonatype.central.client.core

abstract class GenericSonatypeClient(overrideEndpoint: Option[String] = None) {
  private val host                      = overrideEndpoint.getOrElse("https://central.sonatype.com")
  protected val clientBaseUrl           = s"$host/api/v1/publisher"
  protected val clientUploadBundleUrl   = s"$clientBaseUrl/upload"
  protected val clientCheckStatusUrl    = s"$clientBaseUrl/status"
  protected val clientCheckPublishedUrl = s"$clientBaseUrl/published"
  protected def clientPublishValidatedDeploymentUrl(id: DeploymentId): String =
    s"$clientBaseUrl/deployment/${id.unapply}"
  protected def clientDeleteDeploymentUrl(id: DeploymentId): String =
    clientPublishValidatedDeploymentUrl(id)

  protected val uploadBundleMultipartFileName: String = "bundle"
}
