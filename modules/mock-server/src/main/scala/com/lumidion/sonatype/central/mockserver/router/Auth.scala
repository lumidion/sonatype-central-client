package com.lumidion.sonatype.central.mockserver.router

import java.util.Base64
import zio.http.{HandlerAspect, Header, Headers, Middleware, Response, Status}
import zio.http.Header.Authorization
import zio.http.Header.Authorization.Basic
import zio.Config.Secret
import zio.ZIO

object Auth {
  private def parseBasic(value: String): Either[String, Authorization] = {
    try {
      val partsOfBasic = new String(Base64.getDecoder.decode(value)).split(":")
      if (partsOfBasic.length == 2) {
        Right(Basic(partsOfBasic(0), Secret(partsOfBasic(1))))
      } else {
        Left("Basic Authorization header value is not in the format username:password")
      }
    } catch {
      case _: IllegalArgumentException =>
        Left("Basic Authorization header value is not a valid base64 encoded string")
    }
  }

  val authMiddleware: HandlerAspect[Any, Unit] = {
    Middleware.customAuthZIO(
      req => {
        for {
          rawHeaderValue <- ZIO
            .fromOption(req.headers.get("authorization"))
            .mapError(_ => Response.status(Status.Unauthorized))
          _ <- ZIO.logInfo(rawHeaderValue)
          res <- {
            val parts = rawHeaderValue.split(" ")
            if (parts.length == 2 && parts(0) == "UserToken") {
              val parsedAuth = parseBasic(parts(1))
              parsedAuth match {
                case Right(Basic(username, password))
                    if (username == "admin" && password.value.asString == "admin") =>
                  ZIO.succeed(true)
                case _ => ZIO.fail(Response.status(Status.Unauthorized))
              }
            } else {
              ZIO.fail(Response.status(Status.Unauthorized))
            }
          }
        } yield res
      },
      Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))
    )
  }
}
