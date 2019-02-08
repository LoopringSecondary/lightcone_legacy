import sbt._
import Keys._
import Settings._
import Dependencies._
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.tapad.docker.DockerComposeKeys

addCommandAlias("fix", "all compile:scalafix test:scalafix")

lazy val proto = (project in file("proto"))
  .settings(
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value))

lazy val lib = (project in file("lib"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(basicSettings, libraryDependencies ++= dependency4Lib)

lazy val core = (project in file("core"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, lib)
  .settings(basicSettings, libraryDependencies ++= dependency4Core)
  .dependsOn(proto)

lazy val ethereum = (project in file("ethereum"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(lib, core)
  .settings(basicSettings, libraryDependencies ++= dependency4Ethereum)

lazy val persistence = (project in file("persistence"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto, core, lib, ethereum)
  .settings(basicSettings, libraryDependencies ++= dependency4Persistence)

lazy val relayer = (project in file("relayer"))
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(DockerComposePlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(parallelExecution in Test := false)
  .dependsOn(proto, lib, core, persistence)
  .settings(
    basicSettings,
    dockerSettings,
    libraryDependencies ++= dependency4Relayer)

// lazy val indexer = (project in file("indexer"))
//   .enablePlugins(AutomateHeaderPlugin)
//   .enablePlugins(DockerComposePlugin)
//   .enablePlugins(sbtdocker.DockerPlugin)
//   .enablePlugins(JavaServerAppPackaging)
//   .enablePlugins(MultiJvmPlugin)
//   .configs(MultiJvm)
//   .settings(multiJvmSettings: _*)
//   .dependsOn(proto, lib, persistence)
//   .settings(
//     basicSettings,
//     dockerSettings,
//     libraryDependencies ++= dependency4Indexer)

lazy val all = (project in file("."))
  .enablePlugins(DockerComposePlugin)
  .settings(docker := {
    (docker in relayer).value
    // (docker in indexer).value
  }, DockerComposeKeys.dockerImageCreationTask := docker.value)
  .aggregate(proto, lib, ethereum, persistence, core, relayer)
  .withId("lightcone")
