package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.SonatypeCredentials

import java.io.File

object Utils {
  private def getFromEnvOrThrow(key: String): String = {
    sys.env.getOrElse(key, throw new Exception(s"Expected $key to be present in environment"))
  }

  lazy val liveSonatypeCredentials: SonatypeCredentials = {
    val username = getFromEnvOrThrow("SONATYPE_USERNAME")
    val password = getFromEnvOrThrow("SONATYPE_PASSWORD")

    SonatypeCredentials(username, password)
  }

  lazy val mockServerSonatypeCredentials: SonatypeCredentials =
    SonatypeCredentials("admin", "admin")

  lazy val invalidMockServerCredentials: SonatypeCredentials =
    SonatypeCredentials("invalid", "invalid")

  lazy val zippedBundle: File = {
    val path = getClass.getResource("/readme.zip").getPath
    new File(path)
  }
}
