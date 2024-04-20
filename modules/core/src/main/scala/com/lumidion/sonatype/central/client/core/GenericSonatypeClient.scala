package com.lumidion.sonatype.central.client.core

abstract class GenericSonatypeClient {
  protected val clientBaseUrl         = s"http://localhost:3000/api/v1/publisher"
  protected val clientUploadBundleUrl = s"$clientBaseUrl/upload"
  protected val clientCheckStatusUrl  = s"$clientBaseUrl/status"
  protected def clientUpdateDeploymentUrl(id: DeploymentId): String =
    s"$clientBaseUrl/deployment/${id.unapply}"
  protected def clientDeleteDeploymentUrl(id: DeploymentId): String = clientUpdateDeploymentUrl(id)

  protected val uploadBundleMultipartFileName: String = "bundle"
}
