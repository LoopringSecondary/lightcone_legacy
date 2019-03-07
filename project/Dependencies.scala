import sbt._

object Dependencies {
  val slf4jVersion = "1.7.25"
  val logbackVersion = "1.2.3"
  val akkaVersion = "2.5.18"
  val json4sVersion = "3.6.2"
  val scalapbVersion = scalapb.compiler.Version.scalapbVersion
  val kamonVersion = "1.0.1"

  lazy val testDependency = Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    "org.scalamock" %% "scalamock" % "4.1.0" % Test,
    "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
    "com.dimafeng" %% "testcontainers-scala" % "0.22.0" % Test,
    "org.testcontainers" % "mysql" % "1.10.3" % Test,
    "org.testcontainers" % "postgresql" % "1.10.3" % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test)

  lazy val commonDependency = Seq(
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "tv.cntt" %% "slf4s-api" % "1.7.25",
    "org.typelevel" %% "spire" % "0.16.0",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
    "ch.qos.logback" % "logback-classic" % logbackVersion)

  lazy val guiceDependency = Seq(
    "com.google.inject.extensions" % "guice-assistedinject" % "4.2.2",
    "net.codingwell" %% "scala-guice" % "4.2.2")

  lazy val json4sDependency = Seq(
    "org.json4s" %% "json4s-native" % json4sVersion,
    "com.thesamet.scalapb" %% "scalapb-json4s" % "0.7.1")

  lazy val libDependency = Seq(
    "org.web3j" % "core" % "4.1.0")

  lazy val ethereumDependency = Seq(

    "org.ethereum" % "ethereumj-core" % "1.9.1-RELEASE")

  lazy val akkaDependency = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion)

  lazy val httpDependency = Seq(
    "com.typesafe.akka" %% "akka-http" % "10.1.5",
    "de.heikoseeberger" %% "akka-http-json4s" % "1.22.0")

  lazy val socketDependency = Seq(
    "com.corundumstudio.socketio" % "netty-socketio" % "1.7.7",
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.8")

  lazy val driverDependency = Seq(
    "com.github.etaty" %% "rediscala" % "1.8.0",
    "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "0.20",
    "mysql" % "mysql-connector-java" % "5.1.47",
    "org.postgresql" % "postgresql" % "42.2.5")

  lazy val scalapbDependency = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf")

  lazy val monitoringDependencies = Seq(
    "io.kamon" %% "kamon-core" % "1.1.0",
    "io.kamon" %% "kamon-jmx" % "0.6.7",
    "io.kamon" %% "kamon-akka-2.5" % "1.0.1",
    "io.kamon" %% "kamon-akka-remote-2.5" % "1.0.1",
    "io.kamon" %% "kamon-akka-http-2.5" % "1.0.1",
    "io.kamon" %% "kamon-zipkin" % "1.0.0",
    "io.kamon" %% "kamon-datadog" % "1.0.0",
    "io.kamon" %% "kamon-prometheus" % "1.1.1",
    "io.kamon" %% "kamon-scala-future" % "1.0.0",
    "io.kamon" %% "kamon-logback" % "1.0.5" // TODO(dongw): use https://github.com/openzipkin-contrib/brave-akka instead in the future
    )

  lazy val dependency4Lib = commonDependency ++
    libDependency ++
    json4sDependency ++
    testDependency

  lazy val dependency4Core = commonDependency ++
    ethereumDependency ++
    guiceDependency ++
    testDependency

  lazy val dependency4Ethereum = commonDependency ++
    ethereumDependency ++
    driverDependency ++
    guiceDependency ++
    json4sDependency ++
    testDependency

  lazy val dependency4Persistence = commonDependency ++
    driverDependency ++
    guiceDependency ++
    httpDependency ++
    json4sDependency ++
    testDependency

  lazy val dependency4Relayer = dependency4Persistence ++
    httpDependency ++
    socketDependency ++
    akkaDependency ++
    json4sDependency ++
    monitoringDependencies ++
    testDependency

  lazy val dependency4Indexer = dependency4Persistence ++
    httpDependency ++
    akkaDependency ++
    json4sDependency ++
    testDependency
}
