libraryDependencies += "ch.qos.logback"     % "logback-classic"  % "1.4.8"
libraryDependencies += "org.apache.commons" % "commons-compress" % "1.23.0"

addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"              % "0.11.0")
addSbtPlugin("ch.epfl.scala"                     % "sbt-version-policy"        % "2.1.1")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"             % "0.11.0")
addSbtPlugin("com.github.cb372"                  % "sbt-explicit-dependencies" % "0.3.1")
addSbtPlugin("com.github.reibitto"               % "sbt-welcome"               % "0.3.1")
addSbtPlugin("com.github.sbt"                    % "sbt-git"                   % "2.0.1")
addSbtPlugin("com.github.sbt"                    % "sbt-release"               % "1.1.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings"          % "3.0.2")
addSbtPlugin("com.typesafe"                      % "sbt-mima-plugin"           % "1.1.2")
addSbtPlugin("io.github.davidgregory084"         % "sbt-tpolecat"              % "0.4.2")
addSbtPlugin("org.typelevel"                     % "sbt-fs2-grpc"              % "2.7.4")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"                  % "2.3.7")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"              % "2.5.0")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"                   % "0.4.5")
addSbtPlugin("com.github.sbt"                    % "sbt-native-packager"       % "1.9.9")

// sbt dependencyBrowseTreeHTML - will generate dependency tree in html/json formats
addDependencyTreePlugin
