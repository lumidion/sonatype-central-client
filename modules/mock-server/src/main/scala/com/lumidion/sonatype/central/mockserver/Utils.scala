package com.lumidion.sonatype.central.mockserver

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  DeploymentState
}

import java.util.UUID
import scala.language.higherKinds
import zio.json.{DeriveJsonEncoder, JsonEncoder}

object Utils {
  def parseUUID[B](str: String): Option[UUID] = {
    try {
      val id = UUID.fromString(str)
      Some(id)
    } catch {
      case _: Throwable => None
    }
  }

  object encoders {
    implicit val deploymentIdDecoder: JsonEncoder[DeploymentId] =
      JsonEncoder.string.contramap[DeploymentId](_.unapply)
    implicit val deploymentNameDecoder: JsonEncoder[DeploymentName] =
      JsonEncoder.string.contramap[DeploymentName](_.unapply)
    implicit val deploymentStateDecoder: JsonEncoder[DeploymentState] =
      JsonEncoder.string.contramap[DeploymentState](_.unapply)
    implicit val checkStatusResponseBodyDecoder: JsonEncoder[CheckStatusResponse] =
      DeriveJsonEncoder.gen[CheckStatusResponse]
  }
}
