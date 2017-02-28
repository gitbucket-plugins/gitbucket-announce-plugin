val Organization = "fr.brouillard.gitbucket"
val ProjectName = "gitbucket-announce-plugin"
val ProjectVersion = "1.6.0"

lazy val root = (project in file(".")).enablePlugins(SbtTwirl)

organization := Organization
name := ProjectName
version := ProjectVersion
scalaVersion := "2.12.1"

libraryDependencies ++= Seq(
  "io.github.gitbucket"   %% "gitbucket"            % "4.10.0"  % "provided",
  "io.github.gitbucket"   % "solidbase"             % "1.0.0"   % "provided",
  "com.typesafe.play"     %% "twirl-compiler"       % "1.3.0"   % "provided",
  "org.apache.commons"    % "commons-email"         % "1.4"     % "provided",
  "com.sun.mail"          % "javax.mail"            % "1.5.2"   % "provided",
  "javax.servlet"         % "javax.servlet-api"     % "3.1.0"   % "provided"
)

scalacOptions := Seq("-deprecation", "-feature", "-language:postfixOps", "-Ydelambdafy:method", "-target:jvm-1.8")
javacOptions in compile ++= Seq("-target", "7", "-source", "7")

useJCenter := true