resolvers += "mvnrepository" at "http://mvnrepository.com/artifact/"
resolvers += "ethereumlibrepository" at "https://dl.bintray.com/ethereum/maven/"
resolvers += "JFrog" at "https://oss.jfrog.org/libs-release/"
resolvers += "bintray" at "https://dl.bintray.com/ethereum/maven/"
resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.7.4"

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.4.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.0.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.18")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2-1")