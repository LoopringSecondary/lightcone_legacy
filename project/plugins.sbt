resolvers += "mvnrepository" at "http://mvnrepository.com/artifact/"
resolvers += "ethereumlibrepository" at "https://dl.bintray.com/ethereum/maven/"
resolvers += "JFrog" at "https://oss.jfrog.org/libs-release/"
resolvers += "bintray" at "https://dl.bintray.com/ethereum/maven/"
resolvers += Resolver.bintrayRepo("hseeberger", "maven")
resolvers += Resolver.bintrayIvyRepo("kamon-io", "sbt-plugins")
resolvers += Resolver.url(
  "scoverage-bintray",
  url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns)

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.8.2"
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.21"

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC4")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.4.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2-1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("com.tapad" % "sbt-docker-compose" % "1.0.34")
addSbtPlugin("io.kamon" % "sbt-aspectj-runner" % "1.1.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")