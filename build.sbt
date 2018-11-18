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
      scalapb.gen() -> (sourceManaged in Compile).value))

lazy val auxiliary = (project in file("auxiliary"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, lib)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Biz,
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value))

lazy val actors = (project in file("actors"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, auxiliary)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Actors,
    libraryDependencies += "org.loopring" %% "lightcone-core" % "0.1.1-SNAPSHOT")

lazy val gateway = (project in file("gateway"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Gateway,
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value))

lazy val all = (project in file("."))
  .aggregate(proto, lib, auxiliary, actors, gateway)
  .withId("lightcone")