package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{
  DeploymentId,
  DeploymentName,
  DeploymentState,
  PublishingType
}
import com.lumidion.sonatype.central.client.gigahorse.SyncSonatypeClient
import com.lumidion.sonatype.central.client.integration.test.Utils.{
  liveSonatypeCredentials,
  mockServerSonatypeCredentials,
  zippedBundle
}

import gigahorse.support.okhttp.Gigahorse
import java.util.UUID
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext.Implicits._

class GigahorseItSpec extends AnyFreeSpec with Matchers {

  private val httpClient = Gigahorse.http(Gigahorse.config)
  private val mockClient = new SyncSonatypeClient(
    mockServerSonatypeCredentials,
    httpClient,
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
            httpClient
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
      id <- client.uploadBundle(
        zippedBundle,
        DeploymentName("gigahorse"),
        Some(PublishingType.USER_MANAGED)
      )
      resOption <- client.checkStatus(id)
    } yield {
      val res = resOption.get
      val isResValid =
        res.deploymentState == DeploymentState.FAILED || res.deploymentState == DeploymentState.VALIDATING || res.deploymentState == DeploymentState.PENDING || res.deploymentState == DeploymentState.VALIDATED

      isResValid shouldBe true
    }

    if (res.isLeft) {
      println(res)
    }
    res.isRight shouldBe true
  }

  testAgainstEndpoints("#deleteDeployment") { client =>
    val res = for {
      id <- client.uploadBundle(
        zippedBundle,
        DeploymentName("gigahorse-pending-deletion"),
        Some(PublishingType.USER_MANAGED)
      )
      res <- client.checkStatus(id)
      _ = res.isDefined shouldBe true
      deploymentDeletionRes <- client.deleteDeployment(id, 10000)
      _ = deploymentDeletionRes.isDefined shouldBe true
      notFoundDeploymentDeletionRes <- client.deleteDeployment(id, 10000)
      _ = notFoundDeploymentDeletionRes.isEmpty shouldBe true
    } yield ()

    if (res.isLeft) {
      println(res)
    }
    res.isRight shouldBe true
  }

  testAgainstEndpoints("#publishValidatedDeployment") { client =>
    val res = for {
      id <- client.uploadBundle(
        zippedBundle,
        DeploymentName("gigahorse-pending-publish"),
        Some(PublishingType.USER_MANAGED)
      )
      res <- client.checkStatus(id)
      _ = res.isDefined shouldBe true
      publishDeploymentRes <- client.publishValidatedDeployment(id)
      _ = publishDeploymentRes.isDefined shouldBe true
      notFoundPublishDeploymentRes <- client.publishValidatedDeployment(
        DeploymentId(UUID.randomUUID().toString)
      )
      _ = notFoundPublishDeploymentRes.isEmpty shouldBe true
    } yield ()

    if (res.isLeft) {
      println(res)
    }
    res.isRight shouldBe true
  }
}
