package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentName,
  DeploymentState,
  PublishingType
}
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

class SyncSttpItSpec extends AnyFreeSpec with Matchers {
  private val backend = HttpURLConnectionBackend()
  private val client = new SyncSonatypeClient(
    mockServerSonatypeCredentials,
    backend,
    overrideEndpoint = Some("http://localhost:8080")
  )

  "#uploadBundle" - {
    "should succeed against production endpoint" ignore {
      val client = new SyncSonatypeClient(
        liveSonatypeCredentials,
        backend
      )
      val res = for {
        id <- client
          .uploadBundle(zippedBundle, DeploymentName("sttp"), Some(PublishingType.USER_MANAGED))
          .body
        checkStatusResponse <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
        deploymentState = checkStatusResponse.deploymentState
        isStateValid =
          deploymentState == DeploymentState.FAILED || deploymentState == DeploymentState.VALIDATING || deploymentState == DeploymentState.PENDING
      } yield isStateValid shouldBe true

      res.isLeft shouldBe false
    }
    "should succeed" in {
      val res = for {
        id <- client
          .uploadBundle(zippedBundle, DeploymentName("sttp"), Some(PublishingType.USER_MANAGED))
          .body
        checkStatusResponse <- client.checkStatus(id)(asJson[CheckStatusResponse]).body
        deploymentState = checkStatusResponse.deploymentState
        isStateValid =
          deploymentState == DeploymentState.FAILED || deploymentState == DeploymentState.VALIDATING || deploymentState == DeploymentState.PENDING
      } yield isStateValid shouldBe true

      res.isLeft shouldBe false
    }
  }
}
