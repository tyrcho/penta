ThisBuild / scalaVersion := "3.3.3"

// Browser-only: both modules compile to JS. No JVM target — domain tests
// run under Node.js via sbt-scalajs' default JS test env.

// ── Core: domain logic (pure, no Pixi/DOM) ─────────────────────────────────

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "towerdefense-core",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
    testFrameworks += new TestFramework("munit.Framework"),
  )

// ── JS: browser app (scalajs-dom + PixiJS v8 via CDN facade) ──────────────

lazy val js = project
  .in(file("js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core)
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
}

addCommandAlias("dev", ";js/fastLinkJS; devServer; ~js/fastLinkJS")
