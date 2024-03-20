package com.lumidion.sonatype.central.client.core

final case class CheckStatusResponse(
    deploymentId: DeploymentId,
    deploymentName: DeploymentName,
    deploymentState: DeploymentState,
    purls: Vector[String]
)
