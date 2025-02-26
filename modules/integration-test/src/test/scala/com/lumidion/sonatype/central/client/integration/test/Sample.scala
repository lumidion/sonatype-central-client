package com.lumidion.sonatype.central.client.integration.test

import java.nio.file.Files

object Sample {
  import com.lumidion.sonatype.central.client.core.{
    DeploymentName,
    PublishingType,
    SonatypeCredentials
  }
  import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient

  import java.io.File

  val sonatypeCredentials = SonatypeCredentials("admin", "admin")
  val client              = new SyncSonatypeClient(sonatypeCredentials)
  val zippedBundle        = new File("com-testing-project-1.0.0.zip")
  val bundleAsBytes       = Files.readAllBytes(zippedBundle.toPath)

  val id = client.uploadBundleFromFile(
    zippedBundle,
    DeploymentName("com.testing.project-1.0.0"),
    Some(PublishingType.USER_MANAGED)
  )
  val res = client.checkStatus(id).get
}
