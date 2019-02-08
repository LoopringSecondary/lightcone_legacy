import sbt._
import Keys._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.MappingsHelper._
import xerial.sbt.Sonatype.SonatypeKeys._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import scoverage.ScoverageKeys._
import sbtdocker.mutable.Dockerfile
import sbtdocker.{ BuildOptions, DockerPlugin, ImageName }
import sbtdocker.DockerKeys._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import scalafix.sbt.ScalafixPlugin.autoImport.scalafixSemanticdb

object Settings {
  def shouldDisableSemanticdb: Boolean = false

  lazy val basicSettings: Seq[Setting[_]] = Seq(
    scalaVersion := Globals.scalaVersion,
    organization := "org.loopring",
    organizationName := "Loopring Foundation",
    homepage := Some(url("https://loopring.org")),
    developers := List(
      Developer(
        id = "foundation@loopring.org",
        name = "Loopring Developers",
        email = "foundation@loopring.org",
        url = url("https://loopring.org"))),
    scmInfo := Some(
      ScmInfo(url(Globals.projectGitHttpUrl), "scm:" + Globals.projectGitUrl)),
    autoScalaLibrary := false,
    resolvers += "mvnrepository" at "http://mvnrepository.com/artifact/",
    resolvers += "ethereumlibrepository" at "https://dl.bintray.com/ethereum/maven/",
    resolvers += "JFrog" at "https://oss.jfrog.org/libs-release/",
    resolvers += "bintray" at "https://dl.bintray.com/ethereum/maven/",
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    resolvers += Opts.resolver.sonatypeSnapshots,
    resolvers += Opts.resolver.sonatypeReleases,
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"),
    javacOptions := Seq( //"-source", Globals.jvmVersion,
    ),

    scalacOptions := Seq(
      "-Yrangepos", // required by SemanticDB compiler plugin
      "-Ywarn-unused-import", // required by `RemoveUnused` rule
      "-encoding", "utf8", // Option and arguments on same line
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:postfixOps",
      "-g:vars",
      "-unchecked",
      "-deprecation",
      "-Yresolve-term-conflict:package",
      "-feature",
      "-Xfatal-warnings"),
    libraryDependencies ++= {
      if (shouldDisableSemanticdb) List()
      else List(compilerPlugin(scalafixSemanticdb))
    },
    scalacOptions ++= {
      if (shouldDisableSemanticdb) List()
      else List("-Yrangepos")
    },
    fork in Test := false,
    // conflictManager := ConflictManager.strict,
    parallelExecution in Test := false,
    startYear := Some(2018),
    licenses += ("Apache-2.0", new URL(
      "https://www.apache.org/licenses/LICENSE-2.0.txt")),
    shellPrompt in ThisBuild := { state =>
      "sbt (%s)> ".format(Project.extract(state).currentProject.id)
    },
    publishArtifact in (Compile, packageSrc) := false,
    publishArtifact in (Compile, packageDoc) := true,
    publishTo := Some(
      if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
      else Opts.resolver.sonatypeStaging),
    releaseCrossBuild := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges),
    scalafmtOnCompile := true,
    coverageEnabled in Test := true)

  lazy val dockerSettings: Seq[Setting[_]] = Seq(
    dockerfile in docker := {
      val appDir = stage.value
      val targetDir = "/app"

      new Dockerfile {
        from("openjdk:8-jre")
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir, chown = "daemon:daemon")
      }
    },
    // dockerImageCreationTask := docker.value,
    imageNames in docker := Seq(
      ImageName(s"${organization.value}/lightcone_${name.value}:latest"),
      ImageName(
        s"${organization.value}/lightcone_${name.value}:v${version.value}")),
    buildOptions in docker := BuildOptions(
      cache = false,
      removeIntermediateContainers = BuildOptions.Remove.Always,
      pullBaseImage = BuildOptions.Pull.Always))
}
