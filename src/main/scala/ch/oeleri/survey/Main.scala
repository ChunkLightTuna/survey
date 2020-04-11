package ch.oeleri.survey

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Main extends IOApp {
  def run(args: List[String]) =
    SurveyServer.stream[IO].compile.drain.as(ExitCode.Success)
}