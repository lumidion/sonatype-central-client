package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentName,
  DeploymentState,
  PublishingType
}
import com.lumidion.sonatype.central.client.integration.test.Utils.{
  sonatypeCredentials,
  zippedBundle
}
import com.lumidion.sonatype.central.client.sttp.core.SyncSonatypeClient
import com.lumidion.sonatype.central.client.zio.json.decoders._

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.httpurlconnection.HttpURLConnectionBackend
import sttp.client4.ziojson.asJson

class SyncSttpItSpec extends AnyFreeSpec with Matchers {
  "#uploadBundle" ignore {
    "should succeed" in {
      val backend = HttpURLConnectionBackend()
      val client = new SyncSonatypeClient(
        sonatypeCredentials,
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
  }
}
