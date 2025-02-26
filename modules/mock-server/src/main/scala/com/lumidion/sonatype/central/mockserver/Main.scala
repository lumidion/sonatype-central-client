package com.lumidion.sonatype.central.mockserver

import com.lumidion.sonatype.central.mockserver.router.Auth.authMiddleware
import com.lumidion.sonatype.central.mockserver.router.Routes.{
  deleteDeployment,
  getDeploymentStatusRoute,
  publishValidatedDeployment,
  uploadBundleRoute
}

import zio._
import zio.http._

object Main extends ZIOAppDefault {
  private val repository = new DeploymentRepository()

  private val routes: Routes[Any, Nothing] =
    Routes(
      Method.POST / "api" / "v1" / "publisher" / "upload" -> uploadBundleRoute(repository),
      Method.POST / "api" / "v1" / "publisher" / "status" -> getDeploymentStatusRoute(repository),
      Method.POST / "api" / "v1" / "publisher" / "deployment" / uuid(
        "deploymentId"
      ) -> publishValidatedDeployment(repository),
      Method.DELETE / "api" / "v1" / "publisher" / "deployment" / uuid(
        "deploymentId"
      ) -> deleteDeployment(repository)
    ) @@ authMiddleware

  def run: ZIO[Any, Throwable, Nothing] = Server.serve(routes).provide(Server.default)
}
