import Settings._
import Dependencies._

lazy val lib = (project in file("lib"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Lib)

lazy val proto = (project in file("proto"))
  .settings(
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value))

lazy val auxiliary = (project in file("auxiliary"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, lib)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Aux)

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
  .dependsOn(proto, core, auxiliary)
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
  .aggregate(lib, proto, auxiliary, core, actors, gateway)
  // .settings(headerLicense := None)
  .withId("lightcone")