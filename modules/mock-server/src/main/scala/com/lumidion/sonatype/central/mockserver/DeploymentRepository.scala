package com.lumidion.sonatype.central.mockserver

import com.lumidion.sonatype.central.client.core.{DeploymentName, DeploymentState}

import java.util.UUID
import scala.collection.mutable.{HashMap => MutableHashMap}

class DeploymentRepository {
  private val mutableMap: MutableHashMap[UUID, (DeploymentName, DeploymentState)] =
    MutableHashMap.empty

  def getDeployment(id: UUID): Option[(DeploymentName, DeploymentState)] = mutableMap.get(id)

  def createDeployment(
      deploymentName: DeploymentName
  ): UUID = {
    val id = UUID.randomUUID()
    mutableMap += ((id, (deploymentName, DeploymentState.VALIDATING)))
    id
  }

  def updateDeploymentState(id: UUID, state: DeploymentState): Option[Unit] = {
    for {
      deployment <- getDeployment(id)
      res = mutableMap.update(id, (deployment._1, state))
    } yield res
  }
}
