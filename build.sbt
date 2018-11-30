import Settings._
import Dependencies._
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

lazy val proto = (project in file("proto"))
  .settings(
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value))

lazy val ethereum = (project in file("ethereum"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Ethereum)

lazy val persistence = (project in file("persistence"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, ethereum)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Persistence)

lazy val core = (project in file("core"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Core)
  .dependsOn(proto)

lazy val actors = (project in file("actors"))
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .dependsOn(proto, core, persistence)
  .settings(
    parallelExecution in Test := false,
    basicSettings,
    libraryDependencies ++= dependency4Actors)

lazy val gateway = (project in file("gateway"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Gateway)

lazy val all = (project in file("."))
  .aggregate(proto, ethereum, persistence, core, actors, gateway)
  .withId("lightcone")