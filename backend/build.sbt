import DockerConfig.dockerSettings
import sbtrelease._
import com.typesafe.tools.mima.core._
import org.typelevel.scalacoptions.ScalacOptions

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

Global / lintUnusedKeysOnLoad := false

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
    libraryDependencies ++= Dependencies.coreDeps.value,
    libraryDependencies ++= Dependencies.commonTest.value,
  )

lazy val auth = Project(id = "auth", base = file("auth"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
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
    buildInfoPackage := "ru.nh.auth.cli",
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime,
      BuildInfoOption.ToJson
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Dconfig.file=../src/universal/conf/application.conf",
      "-Dlogback.configurationFile=../src/universal/conf/logback.xml"
    ),
    libraryDependencies ++= Dependencies.authDeps.value,
    libraryDependencies ++= Dependencies.commonTest.value,
    Compile / mainClass  := Some("ru.nh.auth.cli.AuthServiceCli"),
    executableScriptName := "auth-service-cli",
    dockerSettings("auth", 8088),
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    ),
  )

lazy val conversation = Project(id = "conversation", base = file("conversation"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
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
    libraryDependencies ++= Dependencies.conversationDeps.value,
    libraryDependencies ++= Dependencies.commonTest.value,
    Compile / mainClass  := Some("ru.nh.conversation.cli.ConversationServiceCli"),
    executableScriptName := "conversation-service-cli",
    dockerSettings("conversation", 8082),
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    ),
  )

lazy val post = Project(id = "post", base = file("post"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
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
    buildInfoPackage := "ru.nh.post.cli",
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime,
      BuildInfoOption.ToJson
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Dconfig.file=../src/universal/conf/application.conf",
      "-Dlogback.configurationFile=../src/universal/conf/logback.xml"
    ),
    libraryDependencies ++= Dependencies.potsDeps.value,
    libraryDependencies ++= Dependencies.commonTest.value,
    Compile / mainClass  := Some("ru.nh.post.cli.PostServiceCli"),
    executableScriptName := "post-service-cli",
    dockerSettings("post", 8083),
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    ),
  )

lazy val user = Project(id = "user", base = file("user"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
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
    libraryDependencies ++= Dependencies.userDeps.value,
    libraryDependencies ++= Dependencies.commonTest.value,
    Compile / mainClass  := Some("ru.nh.user.cli.UserServiceCli"),
    executableScriptName := "user-service-cli",
    dockerSettings("user", 8081),
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    ),
  )

lazy val digitalWallet = Project(id = "digital_wallet", base = file("digital_wallet"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(core)
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
    buildInfoPackage := "ru.nh.digital_wallet.cli",
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime,
      BuildInfoOption.ToJson
    ),
    Compile / run / fork := true,
    Compile / run / javaOptions ++= Seq(
      "-Dconfig.file=../src/universal/conf/application.conf",
      "-Dlogback.configurationFile=../src/universal/conf/logback.xml"
    ),
    libraryDependencies ++= Dependencies.userDeps.value,
    libraryDependencies ++= Dependencies.commonTest.value,
    Compile / mainClass  := Some("ru.nh.digital_wallet.cli.DWServiceCli"),
    executableScriptName := "digital-wallet-service-cli",
    dockerSettings("digital-wallet", 8033),
    bashScriptExtraDefines ++= Seq(
      """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml""""
    ),
  )

lazy val root = Project(id = "network-highload-all", base = file("."))
  .enablePlugins(GitBranchPrompt, JavaAppPackaging)
  .aggregate(api, core, auth, user, conversation, post, digitalWallet)
  .dependsOn(auth, user, conversation, post, digitalWallet)
  .settings(
    Compile / discoveredMainClasses := Seq("ru.nh.user.cli.UserServiceCli"),
    Compile / discoveredMainClasses ++= Seq("ru.nh.conversation.cli.ConversationServiceCli"),
    Compile / discoveredMainClasses ++= Seq("ru.nh.post.cli.PostServiceCli"),
    Compile / discoveredMainClasses ++= Seq("ru.nh.auth.cli.AuthServiceCli"),
    publish        := {},
    publishLocal   := {},
    publish / skip := true,
    logo           := customLogo,
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
