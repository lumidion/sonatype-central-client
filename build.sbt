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
  val sttp      = "4.0.0-M10"
  val scalatest = "3.2.18"
  val mockito   = "3.2.11.0"
  val zioJson   = "0.6.2"
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
    } else if (scalaVersion.value == versions.scala212) {
      Seq(
        "-language:higherKinds",
      )
    } else Seq.empty
  }
)

lazy val core = (project in file("modules/core"))
  .settings(
    name := s"${globals.projectName}-core"
  )
  .settings(commonSettings)

lazy val sttp_core = (project in file("modules/sttp-core"))
  .settings(
    name := s"${globals.projectName}-sttp-core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core"        % versions.sttp,
      "org.scalatest"                 %% "scalatest"   % versions.scalatest % Test,
      "org.scalatestplus"             %% "mockito-4-2" % versions.mockito   % Test
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

lazy val root = (project in file("."))
  .settings(
    publish / skip := true,
    name           := globals.projectName
  )
  .aggregate(core, sttp_core, zio_json)
