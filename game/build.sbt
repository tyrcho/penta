ThisBuild / scalaVersion := "3.3.3"

// WartRemover: only the Option/Either/Try ".get"-style partial-access warts, as errors,
// scoped to Compile (not Test — see the Scala rules' own "When to Relax Rules": test code
// gets a lower bar, and munit assertions routinely call .get on a value the test just
// asserted is present). This repo has never run WartRemover before, so the full default
// wart set would flood every module with unrelated findings instead of the one specific
// pattern (a recent review flagged EntityNames.resourcePath/resourceLink and DocGenerator.
// resourcePage for exactly this) this is meant to catch.
//
// Applied inside each project's own .settings(...) below, not as a bare ThisBuild-level
// statement — sbt-wartremover's AutoPlugin injects its own per-project default
// (`wartremoverErrors := Nil`) at each project's own (more specific) scope, which wins
// over a same-key ThisBuild-scoped setting regardless of file order. Setting it inside
// each project's .settings(...) call instead makes it the last (winning) definition for
// that exact scope.
// `Test / wartremoverErrors := Seq.empty` alone isn't enough: sbt-wartremover bakes the
// wart list into `-P:wartremover:traverser:...` flags on `Compile / scalacOptions`, and
// this build's `Test / scalacOptions` (like sbt's own cross-config default) inherits
// Compile's already-computed value rather than recomputing from Test's own
// wartremoverErrors — so the ban leaks into Test regardless unless those specific flags
// are filtered back out of Test's scalacOptions directly.
import wartremover.Wart
lazy val banOptionGetWarts = Seq(
  Compile / wartremoverErrors := Seq(
    Wart.OptionPartial,
    Wart.EitherProjectionPartial,
    Wart.TryPartial
  ),
  Test / wartremoverErrors := Seq.empty,
  Test / scalacOptions := (Test / scalacOptions).value.filterNot(
    _.startsWith("-P:wartremover:traverser:")
  )
)

// Method-length checking uses scripts/check-method-length.py, not scalastyle — scalastyle
// was never published past Scala 2.12 (its parser predates Dotty entirely and cannot read
// this codebase's Scala 3 optional-braces syntax; every file fails with a raw parse error,
// not a real finding). See that script's own doc for how it approximates method
// boundaries instead. Wired into hooks/pre-commit, not this build.

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
    banOptionGetWarts
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
    banOptionGetWarts
  )

// ── Sim: headless JVM AI-vs-AI battle runner (no Node/browser, no rendering) ────

lazy val sim = project
  .in(file("sim"))
  .dependsOn(coreJVM)
  .settings(
    name := "towerdefense-sim",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
    testFrameworks += new TestFramework("munit.Framework"),
    banOptionGetWarts
  )

// ── Dev server task (attached to js project) ──────────────────────────────

lazy val devServer = taskKey[Unit]("Start live-reload dev server on :8082 (background)")
devServer := {
  val root = (ThisBuild / baseDirectory).value.toPath
  val jsFile =
    (js / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toPath.resolve("main.js")
  DevServer.start(root, jsFile, port = 8082)
  streams.value.log.info("Dev server: http://localhost:8082")
  DevServer.lanAddress().foreach(ip => streams.value.log.info(s"On your WiFi:  http://$ip:8082"))
}

addCommandAlias("dev", ";js/fastLinkJS; devServer; ~js/fastLinkJS")
