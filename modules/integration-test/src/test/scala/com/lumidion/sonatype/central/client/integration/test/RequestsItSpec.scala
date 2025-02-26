package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{
  DeploymentId,
  DeploymentName,
  DeploymentState,
  PublishingType
}
import com.lumidion.sonatype.central.client.integration.test.Utils.{
  liveSonatypeCredentials,
  mockServerSonatypeCredentials,
  zippedBundle
}
import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient

import java.util.UUID
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RequestsItSpec extends AnyFreeSpec with Matchers {
  private val mockClient = new SyncSonatypeClient(
    mockServerSonatypeCredentials,
    overrideEndpoint = Some("http://localhost:8080")
  )

  private def testAgainstEndpoints(testName: String, isMock: Boolean = true)(
      func: SyncSonatypeClient => Unit
  ): Unit = {
    testName - {
      if (!isMock) {
        "should succeed against production endpoint" in {
          val liveClient = new SyncSonatypeClient(
            liveSonatypeCredentials
          )
          func(liveClient)
        }
      }
      if (isMock) {
        "should succeed" in {
          func(mockClient)
        }
      }
    }
  }

  testAgainstEndpoints("#uploadBundle") { client =>
    val id = client.uploadBundleFromFile(
      zippedBundle,
      DeploymentName("requests"),
      Some(PublishingType.USER_MANAGED)
    )
    val res = client.checkStatus(id).get

    val isResValid =
      res.deploymentState == DeploymentState.FAILED || res.deploymentState == DeploymentState.VALIDATING || res.deploymentState == DeploymentState.PENDING || res.deploymentState == DeploymentState.VALIDATED

    isResValid shouldBe true
  }

  testAgainstEndpoints("#deleteDeployment") { client =>
    val id = client.uploadBundleFromFile(
      zippedBundle,
      DeploymentName("requests-pending-deletion"),
      Some(PublishingType.USER_MANAGED)
    )
    val res = client.checkStatus(id)

    res.isDefined shouldBe true

    val deploymentDeletionRes = client.deleteDeployment(id)

    deploymentDeletionRes.isDefined shouldBe true

    val notFoundDeploymentDeletionRes =
      client.deleteDeployment(id)

    notFoundDeploymentDeletionRes.isEmpty shouldBe true
  }

  testAgainstEndpoints("#publishValidatedDeployment") { client =>
    val id = client.uploadBundleFromFile(
      zippedBundle,
      DeploymentName("requests-to-publish-"),
      Some(PublishingType.USER_MANAGED)
    )
    val res = client.checkStatus(id)

    res.isDefined shouldBe true

    val publishDeploymentRes = client.publishValidatedDeployment(id)

    publishDeploymentRes.isDefined shouldBe true

    val notFoundPublishDeploymentRes =
      client.publishValidatedDeployment(DeploymentId(UUID.randomUUID().toString))

    notFoundPublishDeploymentRes.isEmpty shouldBe true
  }
}
