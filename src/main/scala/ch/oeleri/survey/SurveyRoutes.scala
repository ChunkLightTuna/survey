package ch.oeleri.survey

import java.io.{File, FileOutputStream}
import java.util.concurrent._

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, StaticFile}

import scala.concurrent.ExecutionContext

object SurveyRoutes {

  def surveyRoutes(): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._

    val blockingPool = Executors.newFixedThreadPool(4)
    val blocker = Blocker.liftExecutorService(blockingPool)
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

    HttpRoutes.of[IO] {
      case request@GET -> "static" /: x =>
        StaticFile.fromFile(
          new File(s"${System.getProperty("user.home")}/static/${x.toList.mkString("/")}")
          , blocker, Some(request)).getOrElseF(NotFound()) // In case the file doesn't exist

      case request@GET -> Root =>
        StaticFile.fromFile(new File(s"${System.getProperty("user.home")}/static/index.html"), blocker, Some(request))
          .getOrElseF(NotFound()) // In case the file doesn't exist

      case req@POST -> Root / "submit" =>
        val ip = req.from.map(_.getAddress.mkString).getOrElse("no_ip")
        val millis = System.currentTimeMillis()
        val path = s"${System.getProperty("user.home")}/responses/${millis}_$ip"

        req.decodeWith(org.http4s.EntityDecoder.multipart[IO], strict = true) { response =>
          val (text, audio) = response.parts.partition(_.filename.isEmpty)

          val writeText = text.map { part =>
            part.body.through(fs2.text.utf8Decode).compile.string
              .map(answer => s"${part.name.get}:\t$answer")
          }.sequence.map { questions =>
            new FileOutputStream(s"$path/questions.txt")
              .write(questions.mkString("\n\n").getBytes)
          }

          val writeAudio = audio.map { part =>
            part.body.compile.to(Array).map { bytes =>
              new FileOutputStream(s"$path/${part.filename}").write(bytes)
            }
          }.sequence

          new File(path).mkdir()
          writeText.unsafeRunSync()
          writeAudio.unsafeRunSync()

          Ok("success")
        }

    }
  }
}