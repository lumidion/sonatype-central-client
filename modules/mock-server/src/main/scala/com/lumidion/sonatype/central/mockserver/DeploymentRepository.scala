package com.lumidion.sonatype.central.mockserver

import com.lumidion.sonatype.central.client.core.{DeploymentName, DeploymentState}
import com.lumidion.sonatype.central.mockserver.router.Error.{
  DeploymentNotFound,
  InvalidPublishableDeployment,
  PublishDeploymentError
}

import java.util.UUID
import scala.collection.mutable.{HashMap => MutableHashMap}

class DeploymentRepository {
  private val mutableMap: MutableHashMap[UUID, (DeploymentName, DeploymentState)] =
    MutableHashMap.empty

  // Store pre-published component
  private val publishedComponents: scala.collection.mutable.Set[DeploymentName] =
    scala.collection.mutable.HashSet(DeploymentName("com.testing.project-1.0.0"))

  def getDeployment(id: UUID): Option[(DeploymentName, DeploymentState)] = mutableMap.get(id)

  def createDeployment(
      deploymentName: DeploymentName
  ): UUID = {
    val id = UUID.randomUUID()
    mutableMap += ((id, (deploymentName, DeploymentState.VALIDATED)))
    id
  }

  def updateDeploymentState(id: UUID, state: DeploymentState): Option[Unit] = {
    for {
      deployment <- getDeployment(id)
      res = mutableMap.update(id, (deployment._1, state))
    } yield res
  }

  def deleteDeployment(id: UUID): Option[Unit] = {
    mutableMap.get(id).map { _ =>
      mutableMap -= id
    }
  }

  def publishValidatedDeployment(id: UUID): Either[PublishDeploymentError, Unit] = {
    for {
      deployment <- mutableMap.get(id).toRight(DeploymentNotFound)
      res <-
        if (deployment._2 == DeploymentState.VALIDATED) {
          mutableMap.update(id, (deployment._1, DeploymentState.PUBLISHED))
          Right(())
        } else {
          Left(InvalidPublishableDeployment)
        }
    } yield res
  }

  def isPublished(deploymentName: DeploymentName): Boolean = {
    publishedComponents.contains(deploymentName)
  }
}
