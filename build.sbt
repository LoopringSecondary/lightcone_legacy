import sbt._
import Keys._
import Settings._
import Dependencies._
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.tapad.docker.DockerComposeKeys

lazy val proto = (project in file("proto"))
  .settings(
    libraryDependencies ++= scalapbDependency,
    PB.targets in Compile := Seq(
      scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value))

lazy val ethereum = (project in file("ethereum"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(proto)
  .settings(
    basicSettings,
    libraryDependencies ++= dependency4Ethereum)

lazy val persistence = (project in file("persistence"))
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
  .enablePlugins(DockerComposePlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .dependsOn(proto, core, persistence)
  .settings(
    basicSettings,
    dockerSettings,
    libraryDependencies ++= dependency4Actors)

lazy val indexer = (project in file("indexer"))
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(DockerComposePlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .dependsOn(ethereum, persistence)
  .settings(
    basicSettings,
    dockerSettings,
    libraryDependencies ++= dependency4Indexer)

lazy val gateway = (project in file("gateway"))
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(DockerComposePlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .dependsOn(proto)
  .settings(
    basicSettings,
    dockerSettings,
    libraryDependencies ++= dependency4Gateway)

lazy val all = (project in file("."))
  .enablePlugins(DockerComposePlugin)
  .settings(
    docker <<= (
      docker in actors,
      docker in indexer,
      docker in gateway) map { (image, _, _) =>
        image
      },
    DockerComposeKeys.dockerImageCreationTask := docker.value)
  .aggregate(proto, ethereum, persistence, core, actors, gateway, indexer)
  .withId("lightcone")