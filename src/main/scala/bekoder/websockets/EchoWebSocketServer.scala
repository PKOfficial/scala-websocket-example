package bekoder.websockets

import cats.effect._
import cats.effect.std.Queue
import cats.syntax.all._
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame

object EchoWebSocketServer extends IOApp {

  private def echoRoute(
      wsb: WebSocketBuilder2[IO],
      t: Topic[IO, WebSocketFrame],
      q: Queue[IO, WebSocketFrame]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root / "echo" =>
      wsb
        .build(
          send = t.subscribe(maxQueued = 1000),
          receive = _.foreach(q.offer)
        )
    }

  private def indexHtml: String = {
    """
      |<!DOCTYPE html>
      |<html lang="en">
      |<head>
      |    <meta charset="UTF-8">
      |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
      |    <title>WebSocket Echo</title>
      |</head>
      |<body>
      |<div>
      |    <input type="text" id="message-input" placeholder="Type your message...">
      |    <button id="send-button">Send</button>
      |    <div id="echo-container"></div>
      |</div>
      |
      |<script>
      |    const socket = new WebSocket("ws://localhost:8080/echo");
      |    const echoContainer = document.getElementById("echo-container");
      |    const messageInput = document.getElementById("message-input");
      |    const sendButton = document.getElementById("send-button");
      |
      |    function appendMessage(message) {
      |      const p = document.createElement("p");
      |      p.textContent = message;
      |      echoContainer.appendChild(p);
      |    }
      |
      |    socket.addEventListener("message", function (event) {
      |      appendMessage(event.data);
      |    });
      |
      |    sendButton.addEventListener("click", function () {
      |      const message = messageInput.value.trim();
      |      if (message !== "") {
      |        socket.send(message);
      |        messageInput.value = "";
      |      }
      |    });
      |
      |    messageInput.addEventListener("keypress", function (event) {
      |      if (event.key === "Enter") {
      |        sendButton.click();
      |      }
      |    });
      |</script>
      |</body>
      |</html>
      |""".stripMargin
  }

  private val indexRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok(indexHtml).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }

  private object Server {
    def server(t: Topic[IO, WebSocketFrame], q: Queue[IO, WebSocketFrame]): IO[ExitCode] = {
      BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpWebSocketApp(wsb =>
          (Logger.httpRoutes(logHeaders = true, logBody = true)(
            echoRoute(wsb, t, q)
          ) <+> indexRoute).orNotFound
        )
        .withSocketKeepAlive(true)
        .resource
        .use(_ => IO.never)
        .as(ExitCode.Success)
    }
  }

  private def program: IO[Unit] = {
    for {
      q <- Queue.unbounded[IO, WebSocketFrame]
      t <- Topic[IO, WebSocketFrame]
      s <- Stream(
        Stream.fromQueueUnterminated(q).through(t.publish),
        Stream.eval(Server.server(t, q))
      ).parJoinUnbounded.compile.drain
    } yield s
  }

  def run(args: List[String]): IO[ExitCode] = program.map(_ => ExitCode.Success)
}
