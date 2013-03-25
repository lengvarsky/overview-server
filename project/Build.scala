import sbt._
import Keys._
import play.Project._
import templemore.sbt.cucumber.CucumberPlugin
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys

object ApplicationBuild extends Build {

  override def settings = super.settings ++
    Seq(EclipseKeys.skipParents in ThisBuild := false)

  val appName     = "overview-server"
  val appVersion    = "1.0-SNAPSHOT"

  val appDatabaseUrl = "postgres://overview:overview@localhost/overview-dev"
  val testDatabaseUrl	= "postgres://overview:overview@localhost/overview-test"

  // shared dependencies
  val openCsvDep =  "net.sf.opencsv" % "opencsv" % "2.3"
  val postgresqlDep = "postgresql" % "postgresql" % "9.1-901.jdbc4"
  val specs2Dep = "org.specs2" %% "specs2" % "1.14"
  val squerylDep = "org.squeryl" %% "squeryl" % "0.9.6-SNAPSHOT"
  val mockitoDep = "org.mockito" % "mockito-all" % "1.9.5"
  val junitInterfaceDep = "com.novocode" % "junit-interface" % "0.9"
  val junitDep = "junit" % "junit-dep" % "4.11"
  
  // Project dependencies
  val serverProjectDependencies = Seq(
    jdbc,
    anorm,
    filters,
    openCsvDep,
    postgresqlDep,
    squerylDep,
    mockitoDep,
    "com.typesafe" %% "play-plugins-mailer" % "2.1.0",
    "org.jodd" % "jodd-wot" % "3.3.1" % "test",
    "ua.t3hnar.bcrypt" %% "scala-bcrypt" % "2.0"
  )

  // Dependencies for the project named 'common'. Not dependencies common to all projects...
  val commonProjectDependencies = Seq(
    jdbc,
    anorm,
    postgresqlDep,
    squerylDep,
    specs2Dep, // FIXME add % "test"
    junitInterfaceDep, // FIXME add % "test"
    junitDep // FIXME add % "test"
  )

  val workerProjectDependencies = Seq(
    jdbc,
    openCsvDep,
    squerylDep,
    mockitoDep % "test",
    specs2Dep % "test",
    junitInterfaceDep, // FIXME add % "test"
    junitDep % "test"
  )

  val ourTestOptions = Seq(
    Tests.Argument("xonly"),
    Tests.Setup(() => System.setProperty("datasource.default.url", testDatabaseUrl))
  )

  val ourResolvers = Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )

  val ourScalaVersion = "2.10.0"
  val ourScalacOptions = Seq("-deprecation", "-unchecked", "-feature")

  // Project definitions
  val common = Project("common", file("common"), settings =
    Defaults.defaultSettings ++ Seq(
      scalaVersion := ourScalaVersion,
      resolvers ++= ourResolvers,
      libraryDependencies ++= commonProjectDependencies
    )
  ).settings(
    testOptions in Test ++= ourTestOptions,
    scalacOptions ++= ourScalacOptions,
    parallelExecution in Test := false,
    sources in doc in Compile := List()
  )

  val worker = Project("worker", file("worker"), settings =
    Defaults.defaultSettings ++ Seq(
      scalaVersion := ourScalaVersion,
      resolvers ++= ourResolvers,
      libraryDependencies ++= workerProjectDependencies
    )
  ).settings(
    testOptions in Test ++= ourTestOptions,
    scalacOptions ++= ourScalacOptions,
    parallelExecution in Test := false,
    sources in doc in Compile := List()
  ).settings(
    initialize ~= {_ => System.setProperty("datasource.default.url", appDatabaseUrl) }    
  ).dependsOn(common)

  val main = play.Project(appName, appVersion, serverProjectDependencies).settings(
    resolvers ++= ourResolvers,
    resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/",
    resolvers += "scala-bcrypt repo" at "http://nexus.thenewmotion.com/content/repositories/releases-public/"
  ).configs(
    IntegrationTest
  ).settings(
    CucumberPlugin.cucumberSettingsWithIntegrationTestPhaseIntegration : _*
  ).settings(
    templatesImport += "views.Magic._",
    lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" * "*.less"), // only compile .less files that aren't in subdirs
    requireJs ++= Seq(
      "bundle/DocumentSet/index.js",
      "bundle/DocumentSet/show.js",
      "bundle/Document/show.js",
      "bundle/Welcome/show.js"
    ),
    requireJsShim += "main.js",
    testOptions in Test ++= ourTestOptions,
    scalacOptions ++= ourScalacOptions,
    javaOptions in Test ++= Seq(
      "-Dconfig.file=conf/application-test.conf",
      "-Dlogger.resource=logback-test.xml",
      "-Ddb.default.url=" + testDatabaseUrl
    ),
    sources in doc in Compile := List(),
    aggregate in Compile := true,
    Keys.fork in Test := true,
    aggregate in Test := false,
    CucumberPlugin.cucumberFeaturesLocation := "test/features",
    CucumberPlugin.cucumberStepsBasePackage := "steps"
  ).dependsOn(common).aggregate(worker)

  val all = Project("all", file("all")).aggregate(main, worker, common)
}
