package ru.nh.config

import ru.nh.db.PostgresModule
import ru.nh.http.HttpModule
import ru.nh.metrics.MetricsModule

final case class ServerConfig(http: HttpModule.Config, db: PostgresModule.Config, metrics: MetricsModule.Config)