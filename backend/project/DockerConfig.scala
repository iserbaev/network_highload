import com.github.sbt.git.SbtGit.git.gitHeadCommit
import com.typesafe.sbt.SbtNativePackager.{ Docker, Universal }
import com.typesafe.sbt.packager.Keys._
import sbt.Keys.{ isSnapshot, mappings, target, version }
import sbt.{ Compile, Def, IO, ThisProject }

import java.io.File

object DockerConfig {
  lazy val DockerRegistry: String = sys.env.getOrElse("DOCKER_REGISTRY", "public.ecr.aws")
  lazy val DockerGroup: String    = sys.env.getOrElse("PROJECT_GROUP", "v3c4w4q9")
  lazy val DockerProject: String  = sys.env.getOrElse("PROJECT_NAME", "nh-user")

  def dockerSettings(service: String, httpPort: Int): Seq[Def.Setting[_]] = Seq(
    // docker settings
    dockerBaseImage    := "eclipse-temurin:17.0.5_8-jre",
    dockerExposedPorts := Seq(httpPort),
    dockerCmd          := Seq(s"$service-service-cli", "server"),

    // image label dockerRepository/dockerUsername/packageName:version
    dockerRepository     := Some(DockerRegistry),
    dockerUsername       := Some(DockerGroup),
    Docker / packageName := s"nh-$service",
    dockerAliases := {
      if ((ThisProject / isSnapshot).value) {
        Seq(
          dockerAlias.value,
          dockerAlias.value.withTag(gitHeadCommit.value.map(version.value + "-" + _.take(8))),
        )
      } else {
        Seq(
          dockerAlias.value,
          dockerAlias.value.withTag(Some("latest")),
        )
      }
    },
    dockerBuildCommand := {
      if (sys.props("os.arch") != "amd64") {
        // use buildx with platform to build supported amd64 images on other CPU architectures
        // this may require that you have first run 'docker buildx create' to set docker buildx up
        dockerExecCommand.value ++ Seq(
          "buildx",
          "build",
          "--platform=linux/amd64",
          "--load"
        ) ++ dockerBuildOptions.value :+ "."
      } else dockerBuildCommand.value
    },
  )
}
