lazy val root = project
  .in(file("."))
  .settings(
    name := "sqs2kinesis",
    version := "0.1.0-rc1",
    organization := "com.snowplowanalytics",
    scalaVersion := "2.13.1",
    initialCommands := "import com.snowplowanalytics.sqs2kinesis._"
  )
  .settings(BuildSettings.assemblySettings)
  .settings(BuildSettings.compilerSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](organization, name, version),
    buildInfoPackage := "com.snowplowanalytics.sqs2kinesis.generated"
  )
  .settings(
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    ),
    libraryDependencies ++= Seq(
      Dependencies.specs2,
      Dependencies.scalaCheck
    )
  )
  .settings(BuildSettings.helpersSettings)
