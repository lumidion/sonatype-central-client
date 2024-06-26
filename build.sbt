import sbt.url
import scala.collection.Seq

addCommandAlias("format", "scalafmtAll; scalafmtSbt")

val globals = new {
  val projectName      = "sonatype-central-client"
  val organizationName = "com.lumidion"
}

inThisBuild(
  List(
    name         := globals.projectName,
    organization := globals.organizationName,
    homepage     := Some(url("https://github.com/lumidion/sonatype-central-client")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        id = "andrapyre",
        name = "David Doyle",
        email = "david@lumidion.com",
        url = url("https://www.lumidion.com/about")
      )
    ),
    scalacOptions ++= Seq("-feature"),
    versionScheme := Some("semver-spec")
  )
)

val versions = new {
  val scala212  = "2.12.19"
  val scala213  = "2.13.13"
  val scala3    = "3.3.3"
  val sttp      = "4.0.0-M16"
  val scalatest = "3.2.18"
  val zioJson   = "0.7.0"
  val requests  = "0.8.2"
  val upickle   = "3.3.1"
}

val commonSettings = Seq(
  crossScalaVersions := Seq(versions.scala212, versions.scala213, versions.scala3),
  scalacOptions ++= {
    if (scalaVersion.value == versions.scala3) {
      Seq(
        "-Werror",
        "-Wunused:all",
        "-deprecation"
      )
    } else Seq.empty
  }
)

lazy val core = (project in file("modules/core"))
  .settings(
    name := s"${globals.projectName}-core"
  )
  .settings(commonSettings)

lazy val requests = (project in file("modules/requests"))
  .settings(
    name := s"${globals.projectName}-requests",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "requests" % versions.requests,
      "com.lihaoyi" %% "upickle"  % versions.upickle
    )
  )
  .settings(commonSettings)
  .dependsOn(core)

lazy val sttp_core = (project in file("modules/sttp-core"))
  .settings(
    name := s"${globals.projectName}-sttp-core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % versions.sttp
    )
  )
  .settings(commonSettings)
  .dependsOn(core)

lazy val zio_json = (project in file("modules/zio-json"))
  .settings(
    name := s"${globals.projectName}-zio-json",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % versions.zioJson
    )
  )
  .settings(commonSettings)
  .dependsOn(sttp_core)

lazy val integration_test = (project in file("modules/integration-test"))
  .settings(
    publish / skip := true,
    name           := "it",
    libraryDependencies ++= Seq(
      "org.scalatest"                 %% "scalatest" % versions.scalatest % Test,
      "com.softwaremill.sttp.client4" %% "zio-json"  % versions.sttp      % Test
    )
  )
  .dependsOn(requests, sttp_core, zio_json)

lazy val root = (project in file("."))
  .settings(
    publish / skip := true,
    name           := globals.projectName
  )
  .aggregate(core, requests, sttp_core, zio_json)
