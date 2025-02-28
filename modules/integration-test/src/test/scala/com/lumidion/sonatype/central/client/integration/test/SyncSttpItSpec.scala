package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentName,
  DeploymentState,
  IsArtifactPublishedResponse,
  PublishingType
}
import com.lumidion.sonatype.central.client.core.DeploymentState.VALIDATED
import com.lumidion.sonatype.central.client.integration.test.Utils.{
  liveSonatypeCredentials,
  mockServerSonatypeCredentials,
  zippedBundle
}
import com.lumidion.sonatype.central.client.sttp.core.SyncSonatypeClient
import com.lumidion.sonatype.central.client.zio.json.decoders._

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.httpurlconnection.HttpURLConnectionBackend
import sttp.client4.ziojson.asJson
import sttp.model.StatusCode

class SyncSttpItSpec extends AnyFreeSpec with Matchers {
  private val backend = HttpURLConnectionBackend()
  private val mockClient = new SyncSonatypeClient(
    mockServerSonatypeCredentials,
    backend,
    overrideEndpoint = Some("http://localhost:8080")
  )

  private def testAgainstEndpoints(testName: String, isMock: Boolean = true)(
      func: SyncSonatypeClient => Unit
  ): Unit = {
    testName - {
      if (!isMock) {
        "should succeed against production endpoint" in {
          val liveClient = new SyncSonatypeClient(
            liveSonatypeCredentials,
            backend
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
    val res = for {
      id <- client
        .uploadBundle(zippedBundle, DeploymentName("sttp"), Some(PublishingType.USER_MANAGED))
        .body
      checkStatusResponse <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
      deploymentState = checkStatusResponse.deploymentState
      isStateValid =
        deploymentState == DeploymentState.FAILED || deploymentState == DeploymentState.VALIDATING || deploymentState == DeploymentState.PENDING || deploymentState == VALIDATED
    } yield isStateValid shouldBe true

    res.isLeft shouldBe false
  }

  testAgainstEndpoints("#deleteDeployment") { client =>
    val op = for {
      id <- client
        .uploadBundle(
          zippedBundle,
          DeploymentName("sttp-pending-deletion"),
          Some(PublishingType.USER_MANAGED)
        )
        .body
      _ <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
    } yield {
      val res = client.deleteDeployment(id)
      res.isSuccess shouldBe true

      val finalRes = client.checkStatus(id)(asJson[CheckStatusResponse])
      finalRes.code shouldBe StatusCode.NotFound
    }

    op.isRight shouldBe true
  }

  testAgainstEndpoints("#publishValidatedDeployment") { client =>
    val op = for {
      id <- client
        .uploadBundle(
          zippedBundle,
          DeploymentName("sttp-pending-publishing"),
          Some(PublishingType.USER_MANAGED)
        )
        .body
      status <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
      _                       = status.deploymentState shouldBe DeploymentState.VALIDATED
      deploymentPublishingRes = client.publishValidatedDeployment(id)
      _                       = deploymentPublishingRes.isSuccess shouldBe true
      finalStatus <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
      _ = finalStatus.deploymentState shouldBe DeploymentState.PUBLISHED
    } yield ()

    op.isRight shouldBe true
  }

  testAgainstEndpoints("#isArtifactPublished", false) { client =>
    client.isArtifactPublished("org.mockito", "mockito-scala-cats_2.13", "1.17.37")(
      asJson[IsArtifactPublishedResponse]
    )
//    client.isArtifactPublished("com.lumidion", "sonatype-central-client-core_3", "0.3.0")
  }
}
