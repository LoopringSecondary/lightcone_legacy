import sbt._
import Keys._
import Settings._
import Dependencies._
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.tapad.docker.DockerComposeKeys

addCommandAlias("fix", ";all compile:scalafix RemoveUnused ProcedureSyntax; all test:scalafix RemoveUnused ProcedureSyntax")
addCommandAlias("check", ";all compile:scalafix RemoveUnused ProcedureSyntax; all test:scalafix RemoveUnused ProcedureSyntax; all clean; all test:compile")

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
  .settings(basicSettings, libraryDependencies ++= dependency4Core)
  .dependsOn(proto)
  .dependsOn(lib % "compile->compile;test->test")

lazy val ethereum = (project in file("ethereum"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(basicSettings, libraryDependencies ++= dependency4Ethereum)
  .dependsOn(lib % "compile->compile;test->test")
  .dependsOn(core % "compile->compile;test->test")

lazy val persistence = (project in file("persistence"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(basicSettings, libraryDependencies ++= dependency4Persistence)
  .dependsOn(proto)
  .dependsOn(lib % "compile->compile;test->test")
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(ethereum)

lazy val relayer = (project in file("relayer"))
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(DockerComposePlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(parallelExecution in Test := false)
  .dependsOn(proto)
  .dependsOn(lib % "compile->compile;test->test")
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(persistence)
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
  .settings(myScalafixSettings)
  .enablePlugins(GitBranchPrompt)
  .enablePlugins(GitVersioning)
  .enablePlugins(DockerComposePlugin)
  .settings(docker := {
    (docker in relayer).value
    // (docker in indexer).value
  }, DockerComposeKeys.dockerImageCreationTask := docker.value)
  .aggregate(proto, lib, ethereum, persistence, core, relayer)
  .withId("lightcone")
