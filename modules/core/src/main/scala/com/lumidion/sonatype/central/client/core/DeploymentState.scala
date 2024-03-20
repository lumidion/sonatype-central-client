package com.lumidion.sonatype.central.client.core

import DeploymentState._

sealed abstract class DeploymentState(private val id: String) {
  def unapply: String = id

  def isNonFinal: Boolean = this match {
    case PENDING    => true
    case PUBLISHING => true
    case VALIDATING => true
    case _          => false
  }
}

object DeploymentState {
  case object FAILED extends DeploymentState("FAILED")

  case object PENDING extends DeploymentState("PENDING")

  case object PUBLISHED extends DeploymentState("PUBLISHED")

  case object PUBLISHING extends DeploymentState("PUBLISHING")

  case object VALIDATED extends DeploymentState("VALIDATED")

  case object VALIDATING extends DeploymentState("VALIDATING")

  val values: Vector[DeploymentState] =
    Vector(FAILED, PENDING, PUBLISHED, PUBLISHING, VALIDATED, VALIDATING)

  def parse(str: String): Option[DeploymentState] = values.collectFirst {
    case state if state.unapply == str => state
  }

  def decoder(str: String): Either[String, DeploymentState] = DeploymentState
    .parse(str)
    .toRight(
      s"Could not parse received value to a valid deployment state. Received value: $str. Expected value to be one of the following: ${DeploymentState.values
          .map(_.unapply)
          .mkString(",")}"
    )
}
