package com.lumidion.sonatype.central.client.core

sealed abstract class PublishingType(private val id: String) {
  def unapply: String = id
}

object PublishingType {
  case object AUTOMATIC extends PublishingType("AUTOMATIC")

  case object USER_MANAGED extends PublishingType("USER_MANAGED")
}
