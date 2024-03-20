package com.lumidion.sonatype.central.client.core

object RequestParams {

  sealed abstract class UploadBundleRequestParams(id: String) {
    def unapply: String = id
  }
  object UploadBundleRequestParams {
    case object BUNDLE_PUBLISHING_TYPE extends UploadBundleRequestParams("publishingType")
    case object BUNDLE_NAME            extends UploadBundleRequestParams("name")
  }

  sealed abstract class CheckStatusRequestParams(id: String) {
    def unapply: String = id
  }
  object CheckStatusRequestParams {
    case object DEPLOYMENT_ID extends CheckStatusRequestParams("id")
  }
}
