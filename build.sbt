val Http4sVersion = "0.23.16"
val CirceVersion = "0.14.6"
val LogbackVersion = "1.4.11"
val CatsParseVersion = "0.3.10"

lazy val root = (project in file("."))
  .settings(
    organization := "bekoder",
    name := "websocket-example",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.13",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.http4s" %% "http4s-blaze-server" % "0.23.16",
      "org.http4s" %% "http4s-server" % Http4sVersion,
    )
  )
