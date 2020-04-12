package ch.oeleri.survey

import cats.effect.{ExitCode, IOApp}
import cats.implicits._

object Main extends IOApp {
  def run(args: List[String]) =
    SurveyServer.stream.compile.drain.as(ExitCode.Success)
}