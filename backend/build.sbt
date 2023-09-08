import DockerConfig.dockerSettings
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.mimaBinaryIssueFilters
import sbtrelease._
import sbtwelcome._
import sbt.io.Path._

lazy val customLogo =
  s"""
     |      _____     _                 _
     |     |   | |___| |_ _ _ _ ___ ___| |_
     |     | | | | -_|  _| | | | . |  _| '_|
     |     |_|___|___|_| |_____|___|_| |_,_|
     |
     |
     |      _   _     _   _           _
     |     | |_|_|___| |_| |___ ___ _| |
     |     |   | | . |   | | . | .'| . |
     |     |_|_|_|_  |_|_|_|___|__,|___|
     |           |___|
     |
     |                    _         _
     |      ___ ___ ___  |_|___ ___| |_
     |     | . |  _| . | | | -_|  _|  _|
     |     |  _|_| |___|_| |___|___|_|
     |     |_|         |___|
     |
     |""".stripMargin

lazy val cliTasks = Seq(
  UsefulTask("prePush", "Runs all commands that need to be executed before push"),
  UsefulTask("allCompile", "Compile all scala files in project"),
  UsefulTask("allScalafix", "Run 'scalafix' on the entire project"),
  UsefulTask("allScalafmt", "Run 'scalafmt' on the entire project"),
  UsefulTask("allTest", "Run all tests"),
  UsefulTask("scalafmtSbt", "Run scalafmt on the sbt files in project"),
  UsefulTask("versionPolicyCheck", "Check current backward and source compatibility state"),
)

addCommandAlias("scalafixCheck", "scalafix --check")
addCommandAlias("scalafixCheckAll", "scalafixAll --check")

addCommandAlias("allCompile", "all Compile/compile Test/compile")
addCommandAlias("allScalafix", "all scalafixAll")
addCommandAlias("allScalafixCheck", "scalafixAll --check")
addCommandAlias("allScalafmt", "all scalafmtAll")
addCommandAlias("allScalafmtCheck", "all scalafmtCheckAll")
addCommandAlias("allTest", "all test")

addCommandAlias(
  "prePush",
  "allTest ; allScalafix ; allScalafmt ; scalafmtSbt ; versionPolicyCheck"
)

lazy val commonSettings = Seq(
  tpolecatScalacOptions ++= Set(
    ScalacOptions.release("11"),
    ScalacOptions.warnOption("nonunit-statement"),
  )
)

inThisBuild(
  Seq(
    organization  := "ru.kryptonite.libs",
    versionScheme := Some("semver-spec"),
    scalaVersion  := Dependencies.Versions.Scala213,
    javacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-source",
      "11"
    ),
    Compile / compile / javacOptions ++= Seq( // need to split this 'cause javadoc :(
      "-target",
      "11",
      "-Xlint:deprecation" // Print deprecation warning details.
    ),
    autoAPIMappings      := true, // will use external ScalaDoc links for managed dependencies
    mimaFailOnNoPrevious := false,
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    updateOptions := updateOptions.value.withCachedResolution(true),
    addCompilerPlugin(Dependencies.ModuleIds.sbtBetterMonadicFor),
    addCompilerPlugin(Dependencies.ModuleIds.sbtKindProjector.cross(CrossVersion.full)),
    addCompilerPlugin(Dependencies.ModuleIds.sbtSemanticDB.cross(CrossVersion.full)),
    scalafixDependencies ++= Dependencies.sbtScalafix.value,
    Global / excludeLintKeys ++= Set(
      managedSourceDirectories,
      sourceGenerators,
      unmanagedResourceDirectories,
      unmanagedSourceDirectories,
    ),
  )
)

lazy val api = Project(id = "network-highload-api", base = file("api"))
  .enablePlugins(Fs2Grpc)
  .settings(
    scalacOptions += "-Wconf:src=src_managed/.*:silent", // disable warnings in generated code
    Test / parallelExecution := false,
    libraryDependencies ++= Dependencies.api.value,
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
  )

lazy val core = Project(id = "core", base = file("core"))
  .dependsOn(api)
  .settings(
    commonSettings,
    libraryDependencies ++= (Dependencies.conf.value ++ Dependencies.common.value ++ Dependencies.connectorsSql.value ++ Dependencies.http.value ++ Dependencies.httpTapir.value ++ Dependencies.json.value ++ Dependencies.metrics.value),
    libraryDependencies ++= Dependencies.commonTest.value,
  )

lazy val conversation = Project(id = "conversation", base = file("conversation"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(api, core)
  .settings(
    commonSettings,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.map(git.gitCurrentBranch) { case (_, branch) =>
        "gitBranch" -> branch
      },
      BuildInfoKey.map(git.gitHeadCommit) { case (_, sha) =>
        "gitCommit" -> sha.orNull
      }
    ),
    buildInfoPackage := "ru.nh.conversation.cli",
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime,
      BuildInfoOption.ToJson
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Dconfig.file=../src/universal/conf/application.conf",
      "-Dlogback.configurationFile=../src/universal/conf/logback.xml"
    ),
    libraryDependencies ++= (Dependencies.cli.value ++ Dependencies.conf.value ++ Dependencies.common.value ++ Dependencies.connectorsSql.value ++ Dependencies.http.value ++ Dependencies.httpTapir.value ++ Dependencies.json.value ++ Dependencies.metrics.value),
    libraryDependencies ++= Dependencies.commonTest.value,
  )

lazy val user = Project(id = "user", base = file("user"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(api, core)
  .settings(
    commonSettings,
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.map(git.gitCurrentBranch) { case (_, branch) =>
        "gitBranch" -> branch
      },
      BuildInfoKey.map(git.gitHeadCommit) { case (_, sha) =>
        "gitCommit" -> sha.orNull
      }
    ),
    buildInfoPackage := "ru.nh.user.cli",
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime,
      BuildInfoOption.ToJson
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Dconfig.file=../src/universal/conf/application.conf",
      "-Dlogback.configurationFile=../src/universal/conf/logback.xml"
    ),
    libraryDependencies ++= (Dependencies.cli.value ++ Dependencies.conf.value ++ Dependencies.common.value ++ Dependencies.connectorsSql.value ++ Dependencies.http.value ++ Dependencies.httpTapir.value ++ Dependencies.json.value ++ Dependencies.metrics.value),
    libraryDependencies ++= Dependencies.commonTest.value,
  )

lazy val root = Project(id = "network-highload-all", base = file("."))
  .enablePlugins(GitBranchPrompt, JavaAppPackaging)
  .aggregate(api, core, user)
  .dependsOn(user)
  .settings(
    Compile / mainClass  := Some("ru.nh.user.cli.UserServiceCli"),
    executableScriptName := "user-service-cli",
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    ),
    publish      := {},
    publishLocal := {},
    dockerSettings,
    publish / skip := true,
    logo           := customLogo,
    usefulTasks    := cliTasks,
    // Configure releaseVersion to bump the patch, minor, or major version number according
    // to the compatibility intention set by versionPolicyIntention.
    releaseVersion := releaseSetVersionTo(versionPolicyIntention.value),
    // Custom release process: run `versionCheck` after we have set the release version, and
    // reset compatibility intention to `Compatibility.BinaryCompatible` after the release.
    // There are some other modifications for testing: the artifacts are locally published,
    // and we don’t push to the remote repository.
    releaseProcess := Seq[ReleaseStep](
      ReleaseTransformations.checkSnapshotDependencies,
      ReleaseTransformations.inquireVersions,
      ReleaseTransformations.runClean,
      ReleaseTransformations.runTest,
      ReleaseTransformations.setReleaseVersion,
      releaseStepCommand("versionCheck"), // Run task `versionCheck` after the release version is set
      ReleaseTransformations.commitReleaseVersion,
      ReleaseTransformations.tagRelease,
      releaseStepCommand("Docker/publish"),
      ReleaseTransformations.setNextVersion,
      ReleaseTransformations.commitNextVersion,
      releaseStepTask(
        releaseSetAndCommitNextCompatibilityIntention
      ), // Reset compatibility intention to `Compatibility.BinaryCompatible`
      ReleaseTransformations.pushChanges,
    ),
  )

/* Additional sbt-release functions and tasks */

def releaseSetVersionTo(compatibilityIntention: Compatibility): String => String = { currentVersion =>
  val versionWithoutQualifier = Version(currentVersion).getOrElse {
    versionFormatError(currentVersion)
  }.withoutQualifier
  val maybeBump = compatibilityIntention match {
    case Compatibility.None =>
      Some(Version.Bump.Major).filterNot { _ =>
        // No need to bump the major version,
        // because minor, patch, etc are already set to 0
        versionWithoutQualifier.subversions.forall(_ == 0)
      }
    case Compatibility.BinaryCompatible =>
      Some(Version.Bump.Minor).filterNot { _ =>
        // No need to bump the major version,
        // because patch, nano, etc are already set to 0
        versionWithoutQualifier.subversions.tail.forall(_ == 0)
      }
    case Compatibility.BinaryAndSourceCompatible =>
      // No need to bump the patch version,
      // because it has already been bumped when sbt-release set the next release version
      None
  }

  val bumped = maybeBump match {
    case Some(bump) => versionWithoutQualifier.bump(bump)
    case None       => versionWithoutQualifier
  }

  bumped.string
}

lazy val releaseSetAndCommitNextCompatibilityIntention = taskKey[Unit](
  "Set versionPolicyIntention to Compatibility.BinaryAndSourceCompatible, and commit the change"
)
ThisBuild / releaseSetAndCommitNextCompatibilityIntention := {
  val log       = streams.value.log
  val intention = (ThisBuild / versionPolicyIntention).value
  if (intention == Compatibility.BinaryAndSourceCompatible) {
    log.info(s"Not changing compatibility intention because it is already set to $intention")
  } else {
    log.info("Reset compatibility intention to BinaryAndSourceCompatible")
    IO.write(
      new File("compatibility.sbt"),
      "ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible\n"
    )
    val gitAddExitValue =
      sys.process.Process("git add compatibility.sbt").run(log).exitValue()
    assert(gitAddExitValue == 0, s"Command failed with exit status $gitAddExitValue")
    val gitCommitExitValue =
      sys.process
        .Process(Seq("git", "commit", "-m", "Reset compatibility intention"))
        .run(log)
        .exitValue()
    assert(gitCommitExitValue == 0, s"Command failed with exist status $gitCommitExitValue")
  }
}
