package com.lumidion.sonatype.central.client.zio

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  DeploymentState,
  IsArtifactPublishedResponse
}

import scala.language.higherKinds
import zio.json.{DeriveJsonDecoder, JsonDecoder}

package object json {
  object decoders {
    implicit val deploymentIdDecoder: JsonDecoder[DeploymentId] =
      JsonDecoder.string.map(DeploymentId.apply)
    implicit val deploymentNameDecoder: JsonDecoder[DeploymentName] =
      JsonDecoder.string.map(DeploymentName.apply)
    implicit val deploymentStateDecoder: JsonDecoder[DeploymentState] =
      JsonDecoder.string.mapOrFail(DeploymentState.decoder)
    implicit val checkStatusResponseBodyDecoder: JsonDecoder[CheckStatusResponse] =
      DeriveJsonDecoder.gen[CheckStatusResponse]
    implicit val isArtifactPublishedResponse: JsonDecoder[IsArtifactPublishedResponse] =
      DeriveJsonDecoder.gen[IsArtifactPublishedResponse]
  }
}
