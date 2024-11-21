package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{DeploymentName, DeploymentState, PublishingType}
import com.lumidion.sonatype.central.client.integration.test.Utils.{
  liveSonatypeCredentials,
  mockServerSonatypeCredentials,
  zippedBundle
}
import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RequestsItSpec extends AnyFreeSpec with Matchers {
  private val client = new SyncSonatypeClient(
    mockServerSonatypeCredentials,
    overrideEndpoint = Some("http://localhost:8080")
  )

  "#uploadBundle" - {
    "should succeed against production endpoint" ignore {
      val liveClient = new SyncSonatypeClient(
        liveSonatypeCredentials
      )
      val id = liveClient.uploadBundleFromFile(
        zippedBundle,
        DeploymentName("requests"),
        Some(PublishingType.USER_MANAGED)
      )
      val res = liveClient.checkStatus(id).get

      val isResValid =
        res.deploymentState == DeploymentState.FAILED || res.deploymentState == DeploymentState.VALIDATING || res.deploymentState == DeploymentState.PENDING

      isResValid shouldBe true
    }

    "should succeed" in {
      val id = client.uploadBundleFromFile(
        zippedBundle,
        DeploymentName("requests"),
        Some(PublishingType.USER_MANAGED)
      )
      val res = client.checkStatus(id).get

      val isResValid =
        res.deploymentState == DeploymentState.FAILED || res.deploymentState == DeploymentState.VALIDATING || res.deploymentState == DeploymentState.PENDING

      isResValid shouldBe true
      res.deploymentName.unapply == "requests"
    }
  }
}
