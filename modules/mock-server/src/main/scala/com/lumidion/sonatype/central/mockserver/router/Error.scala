package com.lumidion.sonatype.central.mockserver.router

object Error {
  sealed abstract class PublishDeploymentError(val id: String)
  final case object InvalidPublishableDeployment
      extends PublishDeploymentError(id = "invalid_deployment")
  final case object DeploymentNotFound extends PublishDeploymentError(id = "not_found")
}
