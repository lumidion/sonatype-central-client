package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{DeploymentId, DeploymentName, DeploymentState, PublishingType, SonatypeCredentials}
import com.lumidion.sonatype.central.client.integration.test.Utils.{sonatypeCredentials, zippedBundle}
import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RequestsItSpec extends AnyFreeSpec with Matchers {
  "#uploadBundle" - {
    "should succeed" in {
      val client = new SyncSonatypeClient(sonatypeCredentials)
      val id = client.uploadBundle(
        zippedBundle,
        DeploymentName("requests"),
        Some(PublishingType.USER_MANAGED),
        retries = 0
      )
      val res = client.checkStatus(id)

      val isResValid =
        res.deploymentState == DeploymentState.FAILED || res.deploymentState == DeploymentState.VALIDATING || res.deploymentState == DeploymentState.PENDING

      isResValid shouldBe true
    }
  }
}
