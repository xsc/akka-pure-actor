name := "akka-pure-actor"
organization := "org.xsc"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.2"

lazy val akkaVersion = "2.5.3"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ywarn-unused-import"
)

resolvers += Resolver.jcenterRepo
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)
