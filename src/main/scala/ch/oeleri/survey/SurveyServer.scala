package ch.oeleri.survey

import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext.global

object SurveyServer {

  def stream(implicit T: Timer[IO], C: ContextShift[IO]): Stream[IO, Nothing] = {
    for {
      client <- BlazeClientBuilder[IO](global).stream
      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      httpApp = SurveyRoutes.surveyRoutes().orNotFound

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[IO]
        .bindHttp(port = 8443, host = "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .withSslContext(Ssl.fromPath("/etc/letsencrypt/live/survey.oeleri.ch/", "wonkdonk123!"))
        .serve
    } yield exitCode
  }.drain
}
