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

  sealed abstract class CheckPublishedRequestParams(id: String) {
    def unapply: String = id
  }
  object CheckPublishedRequestParams {
    case object NAMESPACE extends CheckPublishedRequestParams("namespace")
    case object NAME      extends CheckPublishedRequestParams("name")
    case object VERSION   extends CheckPublishedRequestParams("version")
  }
}
