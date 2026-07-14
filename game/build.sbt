ThisBuild / scalaVersion := "3.3.3"

// core cross-compiles to JS (for the browser app) and JVM (for fast local iteration
// on AI strategies — plain `sbt coreJVM/console` or a JVM main, no Node/browser needed).

// ── Core: domain logic (pure, no Pixi/DOM) ─────────────────────────────────

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "towerdefense-core",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
    testFrameworks += new TestFramework("munit.Framework"),
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

// ── JS: browser app (scalajs-dom + PixiJS v8 via CDN facade) ──────────────

lazy val js = project
  .in(file("js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .settings(
    name := "towerdefense-js",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
  )

// ── Dev server task (attached to js project) ──────────────────────────────

lazy val devServer = taskKey[Unit]("Start live-reload dev server on :8082 (background)")
devServer := {
  val root   = (ThisBuild / baseDirectory).value.toPath
  val jsFile = (js / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toPath.resolve("main.js")
  DevServer.start(root, jsFile, port = 8082)
  streams.value.log.info("Dev server: http://localhost:8082")
  DevServer.lanAddress().foreach(ip => streams.value.log.info(s"On your WiFi:  http://$ip:8082"))
}

addCommandAlias("dev", ";js/fastLinkJS; devServer; ~js/fastLinkJS")
