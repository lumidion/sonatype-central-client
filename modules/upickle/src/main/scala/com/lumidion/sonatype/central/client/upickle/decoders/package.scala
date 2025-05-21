package com.lumidion.sonatype.central.client.upickle

import com.lumidion.sonatype.central.client.core.{
  CheckPublishedStatusResponse,
  CheckStatusResponse,
  DeploymentId,
  DeploymentName,
  DeploymentState
}

import upickle.default._

package object decoders {
  implicit val deploymentIdDecoder: Reader[DeploymentId] =
    upickle.default.reader[ujson.Str].map(str => DeploymentId(str.value))
  implicit val deploymentNameDecoder: Reader[DeploymentName] = {
    upickle.default.reader[ujson.Str].map(str => DeploymentName(str.value))
  }
  implicit val deploymentStateDecoder: Reader[DeploymentState] =
    upickle.default.reader[ujson.Str].map { str =>
      DeploymentState
        .decoder(str.value)
        .fold(errorMessage => throw new Exception(errorMessage), identity)
    }
  implicit val checkStatusResponseBodyDecoder: Reader[CheckStatusResponse] =
    macroR[CheckStatusResponse]
  implicit val checkPublishedResponseBodyDecoder: Reader[CheckPublishedStatusResponse] =
    macroR[CheckPublishedStatusResponse]
}
