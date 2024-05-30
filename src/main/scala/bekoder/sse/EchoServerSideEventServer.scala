package bekoder.sse

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.implicits.toSemigroupKOps
import fs2._
import fs2.concurrent.Topic
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server.middleware.Logger

object EchoServerSideEventServer extends IOApp {

  // Topic to publish events (now using String)
  private val eventTopic: Topic[IO, String] = Topic[IO, String].unsafeRunSync()

  private def indexHtml: String = {
    """
      |<!DOCTYPE html>
      |<html lang="en">
      |<head>
      |    <meta charset="UTF-8">
      |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
      |    <title>SSE Echo</title>
      |</head>
      |<body>
      |    <div id="event-container"></div>
      |    <script>
      |        const eventSource = new EventSource("http://localhost:8080/events");
      |        const eventContainer = document.getElementById("event-container");
      |
      |        eventSource.onmessage = function(event) {
      |            const p = document.createElement("p");
      |            p.textContent = event.data; // Use the raw string data directly
      |            eventContainer.appendChild(p);
      |        };
      |    </script>
      |</body>
      |</html>
      |""".stripMargin
  }

  private val sseRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "events" =>
      for {
        eventString <- req.as[String] // Get the string from the request body
        _ <- eventTopic.publish1(eventString)
        response <- Ok(s"Event published: {{ $eventString }}")
      } yield response
  }

  private val eventsRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "events" =>
      val eventsStream: Stream[IO, String] = eventTopic.subscribe(100)
      val sseStream: Stream[IO, String] =
        eventsStream.map(event => s"data: $event\n\n")
      Ok(sseStream).map(_.withContentType(`Content-Type`(MediaType.`text/event-stream`)))
  }

  private val indexRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok(indexHtml).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }

  def run(args: List[String]): IO[ExitCode] = {
    val routes = sseRoute <+> eventsRoute <+> indexRoute
    val loggedRoutes =
      Logger.httpApp(logHeaders = true, logBody = true)(routes.orNotFound)
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(loggedRoutes)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
