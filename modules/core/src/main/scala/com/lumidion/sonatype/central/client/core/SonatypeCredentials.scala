package com.lumidion.sonatype.central.client.core

import java.nio.charset.StandardCharsets
import java.util.Base64

final case class SonatypeCredentials(
    username: String,
    password: String
) {
  override def toString: String = "SonatypeCredentials(username: <redacted>, password: <redacted>)"

  def toAuthToken: String = {
    val base64Credentials =
      Base64.getEncoder.encodeToString(s"${username}:${password}".getBytes(StandardCharsets.UTF_8))
    s"UserToken $base64Credentials"
  }
}
