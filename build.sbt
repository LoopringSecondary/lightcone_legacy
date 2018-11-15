import Settings._
import Dependencies._

lazy val lib = (project in file("lib"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Lib
  )

lazy val proto = (project in file("proto"))
  .settings(
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

lazy val biz = (project in file("biz"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, lib)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Biz
  )

lazy val actors = (project in file("actors"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, lib)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Actors,
    libraryDependencies += "org.loopring" %% "lightcone-core" % "0.1.1-SNAPSHOT"
  )


lazy val gateway = (project in file("gateway"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Gateway
  )

lazy val all = (project in file("."))
  .aggregate(proto, lib, biz, actors, gateway)
  .withId("lightcone")