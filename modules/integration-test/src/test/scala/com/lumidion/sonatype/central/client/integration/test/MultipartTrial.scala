package com.lumidion.sonatype.central.client.integration.test

import com.lumidion.sonatype.central.client.integration.test.Utils.zippedBundle
import requests.{MultiItem, MultiPart, Session}
import ujson._

object MultipartTrial {
  def uploadFile(): Unit = {
    val session: Session = requests.Session(
      readTimeout = 5000,
      connectTimeout = 5000,
      maxRedirects = 0,
    )

    val fileKey = "zipped"

    val response = session.post(
      "https://httpbin.org/post",
      data = MultiPart(
        MultiItem(
          fileKey,
          zippedBundle,
          zippedBundle.getName
        )
      )
    )

    val binaryDataWithHeader = read(new String(response.bytes)).obj("files").obj(fileKey).toString().contains("data:application/octet-stream;base64")
    println(s"Final data: $binaryDataWithHeader")
  }
}
