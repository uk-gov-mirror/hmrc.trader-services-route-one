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

package uk.gov.hmrc.traderservices.controllers
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.traderservices.connectors.{PegaCreateCaseRequest, _}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AuditService
import uk.gov.hmrc.traderservices.wiring.AppConfig

import java.{util => ju}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime
import java.util.UUID

@Singleton
class CreateUpdateCaseController @Inject() (
  val authConnector: MicroserviceAuthConnector,
  val createCaseConnector: PegaCreateCaseConnector,
  val updateCaseConnector: PegaUpdateCaseConnector,
  val fileTransferConnector: FileTransferConnector,
  val env: Environment,
  val appConfig: AppConfig,
  val auditService: AuditService,
  cc: ControllerComponents
)(implicit val configuration: Configuration, ec: ExecutionContext)
    extends BackendController(cc) with AuthActions with ControllerHelper {
  // POST /create-case
  def createCase: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      val correlationId = request.headers
        .get("X-Correlation-Id")
        .orElse(
          request.headers
            .get("X-Request-Id")
            .map(_.takeRight(36))
        )
        .getOrElse(ju.UUID.randomUUID().toString())

      withAuthorised {
        withPayload[TraderServicesCreateCaseRequest] { createCaseRequest =>
          createCaseInPegaAndUploadFiles(
            createCaseRequest,
            correlationId,
            auditService.auditCreateCaseEvent(createCaseRequest)
          )

        } {
          // when incoming request's payload validation fails
          case (errorCode, errorMessage) =>
            val response = TraderServicesCaseResponse(
              correlationId = correlationId,
              error = Some(
                ApiError(errorCode, Some(errorMessage))
              )
            )
            auditService
              .auditCreateCaseErrorEvent(response)
              .map(_ => BadRequest(Json.toJson(response)))
        }
      }
        .recoverWith {
          // last resort fallback when request processing fails
          case e =>
            val response = TraderServicesCaseResponse(
              correlationId = correlationId,
              error = Some(
                ApiError("500", Some(e.getMessage()))
              )
            )
            auditService
              .auditCreateCaseErrorEvent(response)
              .map(_ => InternalServerError(Json.toJson(response)))
        }
    }

  // POST /update-case
  def updateCase: Action[String] =
    Action.async(parseTolerantTextUtf8) { implicit request =>
      val correlationId = request.headers
        .get("x-correlation-id")
        .orElse(
          request.headers
            .get("X-Request-Id")
            .map(_.takeRight(36))
        )
        .getOrElse(ju.UUID.randomUUID().toString())

      withAuthorised {
        withPayload[TraderServicesUpdateCaseRequest] { updateCaseRequest =>
          updateCaseInPega(
            updateCaseRequest,
            correlationId,
            auditService.auditUpdateCaseEvent(updateCaseRequest)
          )

        } {
          // when incoming request's payload validation fails
          case (errorCode, errorMessage) =>
            val response = TraderServicesCaseResponse(
              correlationId = correlationId,
              error = Some(
                ApiError(errorCode, Some(errorMessage))
              )
            )
            auditService
              .auditUpdateCaseErrorEvent(response)
              .map(_ => BadRequest(Json.toJson(response)))
        }
      }
        .recoverWith {
          // last resort fallback when request processing fails
          case e =>
            val response = TraderServicesCaseResponse(
              correlationId = correlationId,
              error = Some(
                ApiError("500", Some(e.getMessage()))
              )
            )
            auditService
              .auditUpdateCaseErrorEvent(response)
              .map(_ => InternalServerError(Json.toJson(response)))
        }
    }

  private def createCaseInPegaAndUploadFiles(
    createCaseRequest: TraderServicesCreateCaseRequest,
    correlationId: String,
    audit: TraderServicesCaseResponse => Future[Unit]
  )(implicit
    hc: HeaderCarrier
  ): Future[Result] = {
    val pegaCreateCaseRequest = PegaCreateCaseRequest(
      AcknowledgementReference = correlationId.replace("-", ""),
      ApplicationType = "Route1",
      OriginatingSystem = "Digital",
      Content = PegaCreateCaseRequest.Content.from(createCaseRequest)
    )

    createCaseConnector
      .createCase(pegaCreateCaseRequest, correlationId)
      .flatMap {
        case success: PegaCaseSuccess =>
          transferFilesToPega(success.CaseID, correlationId, createCaseRequest.uploadedFiles)
            .flatMap { fileTransferResults =>
              val response = TraderServicesCaseResponse(
                correlationId = correlationId,
                result = Option(
                  TraderServicesResult(success.CaseID, LocalDateTime.now(), fileTransferResults)
                )
              )
              audit(response)
                .map(_ => Created(Json.toJson(response)))
            }
        // when request to the upstream api returns an error
        case error: PegaCaseError =>
          if (error.isDuplicateCaseError) {
            val response = TraderServicesCaseResponse(
              correlationId = correlationId,
              error = Some(
                ApiError(
                  errorCode = "409",
                  errorMessage = error.duplicateCaseID
                )
              )
            )
            audit(response)
              .map(_ => Conflict(Json.toJson(response)))
          } else {
            val response = TraderServicesCaseResponse(
              correlationId = correlationId,
              error = Some(
                ApiError(
                  errorCode = error.errorCode.getOrElse("ERROR_UPSTREAM_UNDEFINED"),
                  errorMessage = error.errorMessage
                )
              )
            )
            audit(response)
              .map(_ => BadRequest(Json.toJson(response)))
          }
      }
  }

  private def updateCaseInPega(
    updateCaseRequest: TraderServicesUpdateCaseRequest,
    correlationId: String,
    audit: TraderServicesCaseResponse => Future[Unit]
  )(implicit
    hc: HeaderCarrier
  ): Future[Result] = {
    val pegaUpdateCaseRequest = PegaUpdateCaseRequest(
      AcknowledgementReference = correlationId.replace("-", ""),
      ApplicationType = "Route1",
      OriginatingSystem = "Digital",
      Content = PegaUpdateCaseRequest.Content.from(updateCaseRequest)
    )

    updateCaseConnector
      .updateCase(pegaUpdateCaseRequest, correlationId)
      .flatMap {
        case success: PegaCaseSuccess =>
          transferFilesToPega(updateCaseRequest.caseReferenceNumber, correlationId, updateCaseRequest.uploadedFiles)
            .flatMap { fileTransferResults =>
              val response = TraderServicesCaseResponse(
                correlationId = correlationId,
                result = Option(
                  TraderServicesResult(success.CaseID, LocalDateTime.now(), fileTransferResults)
                )
              )
              audit(response)
                .map(_ => Created(Json.toJson(response)))
            }
        // when request to the upstream api returns an error
        case error: PegaCaseError =>
          val response = TraderServicesCaseResponse(
            correlationId = correlationId,
            error = Some(
              ApiError(
                errorCode = error.errorCode.getOrElse("ERROR_UPSTREAM_UNDEFINED"),
                errorMessage = error.errorMessage
              )
            )
          )
          audit(response)
            .map(_ => BadRequest(Json.toJson(response)))
      }
  }

  private def transferFilesToPega(
    caseReferenceNumber: String,
    conversationId: String,
    uploadedFiles: Seq[UploadedFile]
  )(implicit hc: HeaderCarrier): Future[Seq[FileTransferResult]] =
    Future.sequence(
      uploadedFiles.zipWithIndex
        .map {
          case (file, index) =>
            TraderServicesFileTransferRequest
              .fromUploadedFile(
                caseReferenceNumber,
                conversationId,
                correlationId = UUID.randomUUID().toString(),
                applicationName = "Route1",
                batchSize = uploadedFiles.size,
                batchCount = index + 1,
                uploadedFile = file
              )
        }
        .map(fileTransferConnector.transferFile(_, conversationId))
    )
}
