package ru.nh.user.cli

import ru.nh.user.db.PostgresModule
import ru.nh.user.http.HttpModule
import ru.nh.user.metrics.MetricsModule

final case class Config(http: HttpModule.Config, db: PostgresModule.Config, metrics: MetricsModule.Config)
