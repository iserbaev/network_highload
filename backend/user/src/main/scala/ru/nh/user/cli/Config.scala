package ru.nh.user.cli

import ru.nh.user.UserModule
import ru.nh.user.http.HttpModule
import ru.nh.user.metrics.MetricsModule

final case class Config(http: HttpModule.Config, user: UserModule.Config, metrics: MetricsModule.Config)
