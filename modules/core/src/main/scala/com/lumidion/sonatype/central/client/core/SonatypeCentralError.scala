package com.lumidion.sonatype.central.client.core

trait SonatypeCentralError extends Exception {
  val name: String
}

object SonatypeCentralError {
  final case class AuthorizationError(msg: String) extends SonatypeCentralError {
    override val name: String = "Sonatype Central Authorization Error"

    override def getMessage: String = s"$name: $msg"
  }

  final case class GenericError(ex: Throwable) extends SonatypeCentralError {
    override val name: String = "Sonatype Central Generic Error"

    override def getMessage: String = s"$name: ${ex.getMessage}"
  }

  final case class GenericUserError(msg: String) extends SonatypeCentralError {
    override val name: String = "Sonatype Central Generic User Error"

    override def getMessage: String = s"$name: $msg"
  }

  final case class InternalServerError(msg: String) extends SonatypeCentralError {
    override val name: String = "Sonatype Central Internal Server Error"

    override def getMessage: String = s"$name: $msg"
  }
}
