package ch.oeleri.survey

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets

import cats.effect.{Blocker, ContextShift, IO}
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.Http4sDsl

trait Question extends Product {
  val question: String
}

case class Text(question: String, response: String) extends Question {
  override def toString: String = s"$question:\n$response"
}

case class Audio(question: String, response: Array[Byte]) extends Question

object SurveyRoutes {

  def surveyRoutes(): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}

    import java.util.concurrent._

    val blockingPool = Executors.newFixedThreadPool(4)
    val blocker = Blocker.liftExecutorService(blockingPool)

    import dsl._
    import scala.concurrent.ExecutionContext

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
          val parts: Seq[Question] = response.parts.withFilter(_.name.isDefined).map {
            case mp3 if mp3.filename.isDefined =>
              Audio(mp3.name.get, mp3.body.compile.to(Array).unsafeRunSync())
            case text =>
              Text(text.name.get, text.body.compile.to(Array).map(bytes => new String(bytes, StandardCharsets.UTF_8)).unsafeRunSync())
          }

          val text = parts.withFilter(_.isInstanceOf[Text]).map(_.asInstanceOf[Text])
          val audio = parts.withFilter(_.isInstanceOf[Audio]).map(_.asInstanceOf[Audio])


          new File(path).mkdir()

          new FileOutputStream(s"$path/questions.txt")
            .write(text.mkString("", "\n\n", "\n").getBytes)

          audio.foreach { mp3 =>
            new FileOutputStream(s"$path/${mp3.question}.mp3")
              .write(mp3.response)
          }


          Ok("success")
        }

    }
  }
}