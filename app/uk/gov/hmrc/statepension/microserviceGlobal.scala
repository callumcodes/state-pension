/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.statepension

import com.typesafe.config.Config
import play.api._
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.api.config.{ServiceLocatorConfig, ServiceLocatorRegistration}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.api.controllers.ErrorGenericBadRequest
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.statepension.controllers.ErrorResponses
import uk.gov.hmrc.statepension.controllers.ErrorResponses.ErrorNinoInvalid

import scala.concurrent.Future

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs: Config = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs: Config = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector
  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with ServiceLocatorRegistration with ServiceLocatorConfig {

  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = Some(MicroserviceAuthFilter)

  override val slConnector: ServiceLocatorConnector = ServiceLocatorConnector(WSHttp)

  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override def onBadRequest(request: RequestHeader, error: String): Future[Result] = {
    val errorScenario = error match {
      case ErrorResponses.CODE_INVALID_NINO => ErrorNinoInvalid
      case _ => ErrorGenericBadRequest(error)
    }
    Future.successful(Status(errorScenario.httpStatusCode)(Json.toJson(errorScenario)))
  }

}
