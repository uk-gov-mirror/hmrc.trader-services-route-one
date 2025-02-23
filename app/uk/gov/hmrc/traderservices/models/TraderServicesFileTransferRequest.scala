/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.models

import play.api.libs.json.Json
import play.api.libs.json.Format

case class TraderServicesFileTransferRequest(
  conversationId: String,
  caseReferenceNumber: String,
  applicationName: String,
  upscanReference: String,
  downloadUrl: String,
  checksum: String,
  fileName: String,
  fileMimeType: String,
  batchSize: Int,
  batchCount: Int,
  correlationId: Option[String] = None,
  // private field, this value will be overwritten
  // with x-request-id header value in the controller
  requestId: Option[String] = None,
  fileSize: Option[Int] = None
)

object TraderServicesFileTransferRequest {

  def fromUploadedFile(
    caseReferenceNumber: String,
    conversationId: String,
    correlationId: String,
    applicationName: String,
    batchSize: Int,
    batchCount: Int,
    uploadedFile: UploadedFile
  ): TraderServicesFileTransferRequest =
    TraderServicesFileTransferRequest(
      conversationId = conversationId,
      caseReferenceNumber = caseReferenceNumber,
      applicationName = applicationName,
      upscanReference = uploadedFile.upscanReference,
      downloadUrl = uploadedFile.downloadUrl,
      checksum = uploadedFile.checksum,
      fileName = uploadedFile.fileName,
      fileMimeType = uploadedFile.fileMimeType,
      fileSize = uploadedFile.fileSize,
      batchSize = batchSize,
      batchCount = batchCount,
      correlationId = Some(correlationId)
    )

  implicit val formats: Format[TraderServicesFileTransferRequest] =
    Json.format[TraderServicesFileTransferRequest]

  implicit val validate: Validator.Validate[TraderServicesFileTransferRequest] =
    Validator.always
}
