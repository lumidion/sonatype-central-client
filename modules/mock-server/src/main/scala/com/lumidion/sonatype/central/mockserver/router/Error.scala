package com.lumidion.sonatype.central.mockserver.router

object Error {
  sealed abstract class PublishDeploymentError(val id: String)
  case object InvalidPublishableDeployment extends PublishDeploymentError(id = "invalid_deployment")
  case object DeploymentNotFound           extends PublishDeploymentError(id = "not_found")
}
