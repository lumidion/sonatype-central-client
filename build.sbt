import sbt.url
import sbtghactions.GenerativePlugin.autoImport.{
  githubWorkflowPublish,
  githubWorkflowPublishTargetBranches,
  JavaSpec
}
import scala.collection.Seq
import xerial.sbt.Sonatype.sonatypeCentralHost
import Dependencies.versions

addCommandAlias("fmt", "scalafmtAll; scalafmtSbt; mock_server/scalafmtAll")
addCommandAlias("it", "integration_test/test")
addCommandAlias("gigahorseTest", "integration_test/testOnly *.GigahorseItSpec")
addCommandAlias("compileAll", "+compile; integration_test/test:compile; mock_server/compile")
addCommandAlias(
  "mimaChecks",
  "core/mimaReportBinaryIssues; requests/mimaReportBinaryIssues; sttp_core/mimaReportBinaryIssues; upickle/mimaReportBinaryIssues; zio_json/mimaReportBinaryIssues"
)

val globals = new {
  val projectName      = "sonatype-central-client"
  val organizationName = "com.lumidion"
}

lazy val crossScalaVersionsGlobal = Seq(versions.scala212, versions.scala213, versions.scala3)

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
    versionScheme          := Some("semver-spec"),
    sonatypeCredentialHost := sonatypeCentralHost,
    githubWorkflowJavaVersions := Seq(
      JavaSpec.temurin("11"),
      JavaSpec.zulu("8"),
    ),
    githubWorkflowScalaVersions := crossScalaVersionsGlobal,
    githubWorkflowAddedJobs ++= Seq(
      WorkflowJob(
        id = "mima_check",
        name = "Mima Check",
        steps = List(
          WorkflowStep.Use(UseRef.Public("actions", "checkout", "v4"), Map("fetch-depth" -> "0")),
          WorkflowStep.Use(UseRef.Public("coursier", "setup-action", "v1"))
        ) ++ WorkflowStep.SetupJava(List(JavaSpec.temurin("21"))) ++ List(
          WorkflowStep.Use(UseRef.Public("sbt", "setup-sbt", "v1")),
          WorkflowStep.Sbt(List("mimaChecks"))
        ),
        cond = Option("${{ github.event_name == 'pull_request' }}"),
        javas = List(JavaSpec.temurin("21"))
      ),
      WorkflowJob(
        "check",
        "Check Formatting",
        List(
          WorkflowStep.Use(UseRef.Public("actions", "checkout", "v4"), Map("fetch-depth" -> "0")),
          WorkflowStep.Use(UseRef.Public("coursier", "setup-action", "v1")),
          WorkflowStep.Use(UseRef.Public("sbt", "setup-sbt", "v1")),
          WorkflowStep.Sbt(
            name = Some("Check Formatting"),
            commands = List(s"scalafmtCheckAll")
          )
        ),
        javas = List(
          JavaSpec.temurin("11")
        ),
        cond = Option("${{ github.event_name == 'pull_request' }}")
      )
    ),
    githubWorkflowIncludeClean   := false,
    githubWorkflowArtifactUpload := false,
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches :=
      Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
    githubWorkflowBuild := Seq(
      WorkflowStep
        .Sbt(
          name = Some("Build"),
          commands = List("clean", "compile", "integration_test/test:compile")
        ),
      WorkflowStep.Run(name = Some("Start Mock Server"), commands = List("./start-mock-server.sh")),
      WorkflowStep.Sbt(name = Some("Run Integration Tests"), commands = List("it")).copy(
        cond = Some("matrix.java == 'temurin@11'")
      ),
      WorkflowStep.Sbt(name = Some("Run Integration Tests (JDK 8)"), commands = List("gigahorseTest")).copy(
        cond = Some("matrix.java == 'zulu@8'")
      ),
    ),
    githubWorkflowTargetBranches := Seq("main"),
    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        commands = List("ci-release"),
        name = Some("Publish project"),
        env = Map(
          "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
        )
      )
    )
  )
)

val mimaSettings = Seq(
  mimaPreviousArtifacts ++= previousStableVersion.value
    .map(organization.value %% name.value % _)
    .toSet
)

val commonSettings = Seq(
  crossScalaVersions := crossScalaVersionsGlobal,
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
  .settings(mimaSettings)
  .settings(commonSettings)
  .settings(
    name := s"${globals.projectName}-core",
    Compile / scalacOptions += "-release:8",
  )

lazy val gigahorse = (project in file("modules/gigahorse"))
  .dependsOn(core, upickle)
  .settings(mimaSettings)
  .settings(commonSettings)
  .settings(
    name := s"${globals.projectName}-gigahorse",
    libraryDependencies ++= Seq(
      "com.eed3si9n" %% "gigahorse-apache-http" % versions.gigahorse
    ),
    Compile / scalacOptions += "-release:8",
  )

lazy val requests = (project in file("modules/requests"))
  .settings(
    name := s"${globals.projectName}-requests",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "requests" % versions.requests
    )
  )
  .settings(mimaSettings)
  .settings(commonSettings)
  .dependsOn(core, upickle)

lazy val sttp_core = (project in file("modules/sttp-core"))
  .settings(
    name := s"${globals.projectName}-sttp-core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % versions.sttp
    )
  )
  .settings(mimaSettings)
  .settings(commonSettings)
  .dependsOn(core)

lazy val upickle = (project in file("modules/upickle"))
  .settings(
    name := s"${globals.projectName}-upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % versions.upickle
    )
  )
  .settings(mimaSettings)
  .settings(commonSettings)
  .dependsOn(core)

lazy val zio_json = (project in file("modules/zio-json"))
  .settings(
    name := s"${globals.projectName}-zio-json",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % versions.zioJson
    )
  )
  .settings(mimaSettings)
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
  .settings(commonSettings)
  .dependsOn(gigahorse, requests, sttp_core, zio_json)

lazy val mock_server = (project in file("modules/mock-server"))
  .settings(
    assembly / assemblyJarName := "mock-server.jar",
    assembly / target          := file("./output"),
    ThisBuild / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    crossScalaVersions := Seq(versions.scala3),
    publish / skip     := true,
    name               := "mock-server",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % versions.zioHttp
    )
  )
  .settings(commonSettings)
  .dependsOn(core, zio_json)

lazy val root = (project in file("."))
  .settings(
    publish / skip := true,
    name           := globals.projectName
  )
  .aggregate(core, gigahorse, requests, upickle, sttp_core, zio_json, integration_test)
