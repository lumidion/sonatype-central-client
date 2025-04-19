# Sonatype Central Client

## Getting Started

### Sample Application (Sttp Sync Client)

#### Dependencies

For a quick start with a sttp client featuring zio-json support, add the following to your library dependencies in `build.sbt`:

```sbt
  "com.lumidion"  %%  "sonatype-central-client-sttp-core"  %  "0.5.0"
  "com.lumidion"  %%  "sonatype-central-client-zio-json"   %  "0.5.0"
  "com.softwaremill.sttp.client4" %% "zio-json"            %  "4.0.0-M16"
```

#### Simple App

```scala
import com.lumidion.sonatype.central.client.core.{
  CheckStatusResponse,
  DeploymentName,
  PublishingType,
  SonatypeCredentials
}
import com.lumidion.sonatype.central.client.sttp.core.SyncSonatypeClient
import com.lumidion.sonatype.central.client.zio.json.decoders.*
import java.io.File
import sttp.client4.httpurlconnection.HttpURLConnectionBackend
import sttp.client4.ziojson.asJson

object Main {
  def main(args: Array[String]): Unit = {
    val backend     = HttpURLConnectionBackend()
    val credentials = SonatypeCredentials("<user>", "<pass>")
    val client      = new SyncSonatypeClient(credentials, backend)
    val file        = new File("mybundle.zip")

    for {
      deploymentId <- client
        .uploadBundle(
          file,
          DeploymentName("com.testing.project-1.0.0"),
          Some(PublishingType.AUTOMATIC)
        ).body
      statusResponse <- client.checkStatus(deploymentId)(asJson[CheckStatusResponse]).body
      _ = println(
        s"Current deployment status is: ${statusResponse.deploymentState.unapply}. Deployment id: ${deploymentId.unapply}"
      )
    } yield ()
  }
}
```

### Sample Application (Requests Client)

#### Dependencies

For a quick start with the requests client, add the following to your library dependencies in `build.sbt`:

```sbt
  "com.lumidion"  %%  "sonatype-central-client-requests"  %  "0.5.0"
```

#### Simple App

```scala
import com.lumidion.sonatype.central.client.core.{
  DeploymentName,
  PublishingType,
  SonatypeCredentials
}
import com.lumidion.sonatype.central.client.requests.SyncSonatypeClient

import java.io.File

object Main {
  def main(args: Array[String]): Unit = {
    val credentials = SonatypeCredentials("<user>", "<pass>")
    val client      = new SyncSonatypeClient(credentials)
    val file        = new File("mybundle.zip")

    val deploymentId = client
      .uploadBundleFromFile(
        file,
        DeploymentName("com.testing.project-1.0.0"),
        Some(PublishingType.AUTOMATIC)
      )
    val statusResponse = client.checkStatus(deploymentId)

    println(
      s"Current deployment status is: ${statusResponse.deploymentState.unapply}. Deployment id: ${deploymentId.unapply}"
    )
  }
}
```

### Sample Application (Gigahorse Sync Client)

#### Dependencies

For a quick start with the gigahorse client, add the following to your library dependencies in `build.sbt`:

```sbt
  "com.lumidion"  %%  "sonatype-central-client-gigahorse"  %  "0.5.0"
```

#### Simple App

```scala
import com.lumidion.sonatype.central.client.core.{
  DeploymentName,
  PublishingType,
  SonatypeCredentials
}

import gigahorse.support.okhttp.Gigahorse
import java.io.File
import scala.concurrent.ExecutionContext.Implicits._

object Main {
  def main(args: Array[String]): Unit = {
    val httpClient = Gigahorse.http(Gigahorse.config)
    val credentials = SonatypeCredentials("admin", "admin")
    val sonatypeClient = new SyncSonatypeClient(credentials, httpClient)
    for {
      id <- sonatypeClient.uploadBundle(
        new File("mybundle.zip"),
        DeploymentName("com.testing.project-1.0.0"),
        Some(PublishingType.AUTOMATIC)
      )
      status <- sonatypeClient.checkStatus(id)
      _ <- sonatypeClient.deleteDeployment(id)
      secondId <- sonatypeClient.uploadBundle(
        new File("mysecondbundle.zip"),
        DeploymentName("com.testing2.project-1.0.0"),
        Some(PublishingType.AUTOMATIC)
      )
      _ <- sonatypeClient.publishValidatedDeployment(secondId)
    } yield ()
  }
}
```


