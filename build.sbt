name := "dtwrks-csv-processor"

organization := "net.d53dev.dtwrks"

version := "0.1.0"

scalaVersion := "2.11.8"

//unmanagedClasspath in Runtime += baseDirectory.value / "app"
unmanagedClasspath in Runtime += baseDirectory.value / "conf"

//unmanagedSourceDirectories in Compile ++= {
//  Seq(baseDirectory.value / "app", baseDirectory.value / "test", baseDirectory.value / "conf")
//}

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-language:postfixOps",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

enablePlugins(sbtdocker.DockerPlugin)

resolvers += 
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"      


val akkaVersion = "2.4.17"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream"     	 % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13",
  "org.scalikejdbc"   %% "scalikejdbc"       % "2.5.0",
  "com.h2database"    %  "h2"                % "1.4.193",
  "ch.qos.logback"    %  "logback-classic"   % "1.1.7",
  "org.apache.kafka"  %% "kafka" 	   % "0.10.1.1",
  "com.typesafe"      % "config" % "1.3.0",
  "com.typesafe.play" %% "play-json" % "2.5.12",
  "com.github.alexandrnikitin" %% "bloom-filter" % "0.8.0",
  "com.typesafe.akka" %% "akka-testkit" 	 % akkaVersion % "test",
  "org.specs2" %% "specs2-core" % "3.8.8"    % "test",
  "org.scalikejdbc" %% "scalikejdbc-test" % "2.5.0"
  )
  
scalacOptions in Test ++= Seq("-Yrangepos")


dockerfile in docker := {
  val appDir: File = stage.value
  val targetDir = "/app"
  
  //log.debug(s"appDir: $appDir")
  //log.debug(s"targetDir: $targetDir")

  new Dockerfile {
    from("java")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir)
  	expose(9999)    // for JMX  
  }
}
