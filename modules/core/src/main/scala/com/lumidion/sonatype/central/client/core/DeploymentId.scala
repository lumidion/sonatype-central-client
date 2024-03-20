package com.lumidion.sonatype.central.client.core

final case class DeploymentId(private val id: String) {
  def unapply: String = id
}
