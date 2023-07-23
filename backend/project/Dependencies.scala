import sbt._

object Dependencies {

  object Versions {
    val Scala213 = "2.13.11"

    // Compiler Plugins
    val BetterMonadicFor  = "0.3.1"
    val KindProjector     = "0.13.2"
    val SemanticDB        = "4.7.8"
    val ScalafixTypelevel = "0.1.5"

    val Magnolia = "1.1.3"

    val CaseInsensitive    = "1.4.0"
    val Cats               = "2.9.0"
    val CatsEffect         = "3.5.1"
    val CatsRetry          = "3.1.0"
    val Caffeine           = "3.1.6"
    val Circe              = "0.14.5"
    val CirceGenericExtras = "0.14.3"
    val Config             = "1.4.2"
    val Decline            = "2.4.1"
    val Doobie             = "1.0.0-RC4"
    val Discipline         = "1.5.1"
    val Enumeratum         = "1.7.2"
    val Flyway             = "9.20.0"
    val Fs2                = "3.7.0"
    val JwtHttp4sVersion   = "1.2.0"
    val JwtScalaVersion    = "9.4.3"
    val HikariCP           = "5.0.1"
    val Http4s             = "0.23.22"
    val Http4sBlaze        = "0.23.15"
    val Http4sNetty        = "0.5.7"
    val JMH                = "1.36"
    val JwksRsa            = "0.22.0"
    val JwtCore            = "5.0.0"
    val Log4Cats           = "2.6.0"
    val Logback            = "1.4.8"
    val Netty              = "4.1.93.Final"
    val Newtypes           = "0.2.3"
    val Prometheus         = "0.16.0"
    val ProtobufJava       = "3.23.3"
    val Pureconfig         = "0.17.4"
    val SampledSpPcm       = "0.9.5"
    val ScalaJava8Compat   = "1.0.2"
    val ScalapbLenses      = "0.11.13"
    val ScodecCore         = "1.11.10"
    val ScodecBits         = "1.1.37"
    val ScodecCats         = "1.2.0"
    val Shapeless          = "2.3.10"
    val SingletonOps       = "0.5.2"
    val Squants            = "1.8.3"
    val SttpApispec        = "0.4.0"
    val SttpClient3        = "3.8.15"
    val SttpModel          = "1.6.0"
    val SttpShared         = "1.3.15"
    val SttpTapir          = "1.6.0"
    val Vault              = "3.5.0"

    val ScalaPbCommonProtos = "2.9.6-0"

    val Expecty             = "0.16.0"
    val Testcontainers      = "1.18.3"
    val TestContainersScala = "0.40.17"
    val Scalacheck          = "1.17.0"
    val Weaver              = "0.8.3"

  }

  // noinspection TypeAnnotation
  object ModuleIds {

    val caseInsensitive = Seq("org.typelevel" %% "case-insensitive" % Versions.CaseInsensitive)

    val catsKernel = Seq("org.typelevel" %% "cats-kernel" % Versions.Cats)
    val catsCore   = Seq("org.typelevel" %% "cats-core" % Versions.Cats)
    val catsLaws = Seq(
      "org.typelevel" %% "cats-kernel-laws" % Versions.Cats,
      "org.typelevel" %% "cats-laws"        % Versions.Cats,
    )
    val catsRetry   = Seq("com.github.cb372" %% "cats-retry" % Versions.CatsRetry)
    val catsTestkit = Seq("org.typelevel" %% "cats-testkit" % Versions.Cats)

    val catsEffectKernel = Seq("org.typelevel" %% "cats-effect-kernel" % Versions.CatsEffect)
    val catsEffectStd    = Seq("org.typelevel" %% "cats-effect-std" % Versions.CatsEffect)
    val catsEffect       = Seq("org.typelevel" %% "cats-effect" % Versions.CatsEffect)

    val caffeine = Seq("com.github.ben-manes.caffeine" % "caffeine" % Versions.Caffeine)

    val circeCore          = Seq("io.circe" %% "circe-core" % Versions.Circe)
    val circeGeneric       = Seq("io.circe" %% "circe-generic" % Versions.Circe)
    val circeGenericExtras = Seq("io.circe" %% "circe-generic-extras" % Versions.CirceGenericExtras)
    val circeJawn          = Seq("io.circe" %% "circe-jawn" % Versions.Circe)
    val circeLiteral       = Seq("io.circe" %% "circe-literal" % Versions.Circe)
    val circeParser        = Seq("io.circe" %% "circe-parser" % Versions.Circe)
    val circeTesting       = Seq("io.circe" %% "circe-testing" % Versions.Circe)

    val config = Seq("com.typesafe" % "config" % Versions.Config)

    val discipline = Seq("org.typelevel" %% "discipline-core" % Versions.Discipline)

    val decline = Seq("com.monovore" %% "decline" % Versions.Decline)
    val declineEffect = decline ++ Seq(
      "com.monovore" %% "decline-effect" % Versions.Decline
    )

    val doobie = Seq(
      "org.tpolecat" %% "doobie-core"   % Versions.Doobie,
      "org.tpolecat" %% "doobie-hikari" % Versions.Doobie,
    )

    val doobiePostgres = Seq(
      "org.tpolecat" %% "doobie-postgres"       % Versions.Doobie,
      "org.tpolecat" %% "doobie-postgres-circe" % Versions.Doobie,
    )

    val enumeratum = Seq(
      "com.beachape" %% "enumeratum" % Versions.Enumeratum
    )

    val enumeratumCirce = Seq(
      "com.beachape" %% "enumeratum-circe" % Versions.Enumeratum,
    )

    val flyway = Seq(
      "org.flywaydb" % "flyway-core" % Versions.Flyway
    )

    val fs2Core   = Seq("co.fs2" %% "fs2-core" % Versions.Fs2)
    val fs2Io     = Seq("co.fs2" %% "fs2-io" % Versions.Fs2)
    val fs2Scodec = Seq("co.fs2" %% "fs2-scodec" % Versions.Fs2)

    val jwt = Seq(
//      "dev.profunktor"       %% "http4s-jwt-auth" % Versions.JwtHttp4sVersion,
//      "com.github.jwt-scala" %% "jwt-core"        % Versions.JwtScalaVersion,
//      "com.github.jwt-scala" %% "jwt-circe"       % Versions.JwtScalaVersion
    )

    val grpcApi      = Seq("io.grpc" % "grpc-api" % scalapb.compiler.Version.grpcJavaVersion)
    val grpcNetty    = Seq("io.grpc" % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion)
    val grpcServices = Seq("io.grpc" % "grpc-services" % scalapb.compiler.Version.grpcJavaVersion)

    val grpcServer = Seq(
      "io.grpc"               % "grpc-netty-shaded"    % scalapb.compiler.Version.grpcJavaVersion,
      "io.grpc"               % "grpc-services"        % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime"      % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % Versions.ScalaPbCommonProtos,
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % Versions.ScalaPbCommonProtos % "protobuf",
    )

    val hikariCP = Seq("com.zaxxer" % "HikariCP" % Versions.HikariCP)

    val http4sCore   = Seq("org.http4s" %% "http4s-core" % Versions.Http4s)
    val http4sCirce  = Seq("org.http4s" %% "http4s-circe" % Versions.Http4s)
    val http4sClient = Seq("org.http4s" %% "http4s-client" % Versions.Http4s)
    val http4sDsl    = Seq("org.http4s" %% "http4s-dsl" % Versions.Http4s)
    val http4sServer = Seq("org.http4s" %% "http4s-server" % Versions.Http4s)

    val http4sBlazeCore   = Seq("org.http4s" %% "http4s-blaze-core" % Versions.Http4sBlaze)
    val http4sBlazeClient = Seq("org.http4s" %% "http4s-blaze-client" % Versions.Http4sBlaze)
    val http4sBlazeServer = Seq("org.http4s" %% "http4s-blaze-server" % Versions.Http4sBlaze)

    val http4sNettyCore   = Seq("org.http4s" %% "http4s-netty-core" % Versions.Http4sNetty)
    val http4sNettyServer = Seq("org.http4s" %% "http4s-netty-server" % Versions.Http4sNetty)

    val jmh = Seq(
      "org.openjdk.jmh" % "jmh-core"                 % Versions.JMH,
      "org.openjdk.jmh" % "jmh-generator-annprocess" % Versions.JMH
    )

    val jwksRsa = Seq("com.auth0" % "jwks-rsa" % Versions.JwksRsa)
    val jwtCore = Seq("com.pauldijou" %% "jwt-core" % Versions.JwtCore)

    lazy val log4CatsCore  = Seq("org.typelevel" %% "log4cats-core" % Versions.Log4Cats)
    lazy val log4CatsSlf4J = Seq("org.typelevel" %% "log4cats-slf4j" % Versions.Log4Cats)
    lazy val log4CatsNoop  = Seq("org.typelevel" %% "log4cats-noop" % Versions.Log4Cats)

    val logback = Seq("ch.qos.logback" % "logback-classic" % Versions.Logback)

    val magnolia = Seq("com.softwaremill.magnolia1_2" %% "magnolia" % Versions.Magnolia)

    val nettyTransport = Seq("io.netty" % "netty-transport" % Versions.Netty)

    val newtypes = Seq("io.monix" %% "newtypes-core" % Versions.Newtypes)

    val prometheus = Seq("io.prometheus" % "simpleclient" % Versions.Prometheus)

    val protobuf = Seq("com.google.protobuf" % "protobuf-java" % Versions.ProtobufJava)

    val pureconfigCore = Seq(
      "com.github.pureconfig" %% "pureconfig-core" % Versions.Pureconfig,
    )
    val pureconfigGeneric = Seq(
      "com.github.pureconfig" %% "pureconfig-generic"      % Versions.Pureconfig,
      "com.github.pureconfig" %% "pureconfig-generic-base" % Versions.Pureconfig,
    )
    val pureconfigSquants = Seq(
      "com.github.pureconfig" %% "pureconfig-squants" % Versions.Pureconfig,
    )

    val sampledSpPcm = Seq(
      "com.tagtraum" % "pcmsampledsp" % Versions.SampledSpPcm % Provided
    )

    val scalaJava8Compat = Seq(
      "org.scala-lang.modules" %% "scala-java8-compat" % Versions.ScalaJava8Compat
    )

    val scalaReflect = Seq("org.scala-lang" % "scala-reflect" % Versions.Scala213)

    val scalapbRuntime = Seq(
      "com.thesamet.scalapb" %% "lenses"               % Versions.ScalapbLenses,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime"      % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime"      % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % Versions.ScalaPbCommonProtos % "protobuf",
    )

    val scalacheck = Seq("org.scalacheck" %% "scalacheck" % Versions.Scalacheck)

    val scodecCats = Seq("org.scodec" %% "scodec-cats" % Versions.ScodecCats)
    val scodecBits = Seq("org.scodec" %% "scodec-bits" % Versions.ScodecBits)
    val scodecCore = Seq("org.scodec" %% "scodec-core" % Versions.ScodecCore)

    val shapeless = Seq("com.chuusai" %% "shapeless" % Versions.Shapeless)

    val singletonOps = Seq("eu.timepit" %% "singleton-ops" % Versions.SingletonOps)

    val squants = Seq("org.typelevel" %% "squants" % Versions.Squants)

    val sttpClient3 = Seq(
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % Versions.SttpClient3,
      "com.softwaremill.sttp.client3" %% "circe"                          % Versions.SttpClient3
    )

    val tapirCore = Seq(
      "com.softwaremill.sttp.model" %% "core"             % Versions.SttpModel,
      "com.softwaremill.sttp.tapir" %% "tapir-core"       % Versions.SttpTapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % Versions.SttpTapir,
    )

    val tapirMetrics = Seq("com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % Versions.SttpTapir)

    val tapirServer = Seq(
      "com.softwaremill.sttp.apispec" %% "openapi-model"           % Versions.SttpApispec,
      "com.softwaremill.sttp.shared"  %% "fs2"                     % Versions.SttpShared,
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server"     % Versions.SttpTapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-openapi-docs"      % Versions.SttpTapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-server"            % Versions.SttpTapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui"        % Versions.SttpTapir,
      "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-bundle" % Versions.SttpTapir,
    )

    val tapirStub = Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % Versions.SttpTapir
    )

    val testcontainersCore = Seq(
      "org.testcontainers" % "testcontainers"            % Versions.Testcontainers,
      "com.dimafeng"      %% "testcontainers-scala-core" % Versions.TestContainersScala,
    )
    val testcontainersPostgres = Seq(
      "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.TestContainersScala,
    )

    val testcontainersElastic = Seq(
      "com.dimafeng" %% "testcontainers-scala-elasticsearch" % Versions.TestContainersScala,
    )

    val vault = Seq("org.typelevel" %% "vault" % Versions.Vault)

    val weaverCore = Seq(
      "com.eed3si9n.expecty" %% "expecty"     % Versions.Expecty,
      "com.disneystreaming"  %% "weaver-core" % Versions.Weaver
    )
    val weaverCatsCore = Seq("com.disneystreaming" %% "weaver-cats-core" % Versions.Weaver)
    val weaverCats     = Seq("com.disneystreaming" %% "weaver-cats" % Versions.Weaver)
    val weaverScalacheck = Seq(
      "com.disneystreaming" %% "weaver-discipline" % Versions.Weaver,
      "com.disneystreaming" %% "weaver-scalacheck" % Versions.Weaver,
    )

    val sbtBetterMonadicFor = "com.olegpy"   %% "better-monadic-for" % Versions.BetterMonadicFor
    val sbtKindProjector    = "org.typelevel" % "kind-projector"     % Versions.KindProjector
    val sbtSemanticDB       = "org.scalameta" % "semanticdb-scalac"  % Versions.SemanticDB

    val sbtScalafixTypelevel = Seq(
      "org.typelevel" %% "typelevel-scalafix-cats"        % Versions.ScalafixTypelevel,
      "org.typelevel" %% "typelevel-scalafix-cats-effect" % Versions.ScalafixTypelevel,
      "org.typelevel" %% "typelevel-scalafix-fs2"         % Versions.ScalafixTypelevel
    )

  }

  import ModuleIds._

  val api = Def.setting(
    catsCore ++ catsEffect ++ enumeratum ++ enumeratumCirce ++ fs2Core ++ grpcServer ++ newtypes
  )

  val common = Def.setting(
    catsCore ++ catsKernel ++
      catsEffect ++ catsEffectKernel ++ catsEffectStd ++
      circeCore ++ circeJawn ++ circeParser ++ circeGeneric ++ circeGenericExtras ++
      config ++
      decline ++
      fs2Core ++ fs2Io ++
      log4CatsCore ++ log4CatsNoop ++
      pureconfigCore ++
      scodecBits
  )

  val commonTest = Def.setting(
    (catsTestkit ++
      pureconfigGeneric ++
      weaverCore ++ weaverCats ++ weaverScalacheck).map(_ % Test)
  )

  val connectorsSql = Def.setting(
    catsCore ++
      catsEffect ++ catsEffectKernel ++ catsEffectStd ++ catsRetry ++
      log4CatsCore ++
      doobie ++ doobiePostgres ++
      flyway ++
      hikariCP ++
      pureconfigCore ++ pureconfigGeneric ++
      shapeless
  )

  val grpc = Def.setting(
    catsKernel ++ catsCore ++
      catsEffect ++ catsEffectKernel ++ catsEffectStd ++
      fs2Core ++
      grpcApi ++ grpcNetty ++ grpcServices ++ grpcServer ++
      log4CatsCore ++
      protobuf ++
      pureconfigCore ++ pureconfigGeneric ++ pureconfigSquants ++
      scalapbRuntime ++
      scodecBits ++
      shapeless ++
      squants
  )

  val grpcMetrics = Def.setting(
    catsKernel ++ catsCore ++
      catsEffectKernel ++ catsEffectStd ++ catsEffect ++
      grpcApi ++
      prometheus
  )

  val http = Def.setting(
    caseInsensitive ++
      catsCore ++
      catsEffect ++ catsEffectKernel ++
      circeCore ++
      config ++
      fs2Core ++
      jwt ++
      http4sCore ++ http4sCirce ++ http4sDsl ++ http4sServer ++
      http4sBlazeCore ++ http4sBlazeServer ++ http4sNettyCore ++ http4sNettyServer ++
      log4CatsCore ++
      nettyTransport ++
      pureconfigCore ++ pureconfigGeneric ++
      shapeless
  )

  val httpTest = Def.setting(
    (http4sClient ++
      weaverCats).map(_ % Test)
  )

  val httpTapir = Def.setting(
    catsCore ++
      catsEffectKernel ++ catsEffect ++
      circeCore ++
      config ++
      fs2Core ++
      http4sCore ++
      log4CatsCore ++
      tapirCore ++ tapirServer ++ tapirMetrics
  )

  val sbtScalafix = Def.setting(
    sbtScalafixTypelevel
  )
}
