package com.lumidion.sonatype.central.client.core

final case class DeploymentName(private val name: String) {
  def unapply: String = name
}

object DeploymentName {

  def fromArtifact(
      organizationName: String,
      artifactName: String,
      version: String
  ): DeploymentName = DeploymentName(
    s"$organizationName.$artifactName-$version"
  )
}
