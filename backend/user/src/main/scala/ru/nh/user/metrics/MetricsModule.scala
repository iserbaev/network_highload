package ru.nh.user.metrics

import cats.effect.IO
import cats.effect.kernel.Resource
import com.zaxxer.hikari.metrics.MetricsTrackerFactory
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import io.prometheus.client.CollectorRegistry
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

trait MetricsModule {
  def pullEndpoint: ServerEndpoint[Any, IO]

  def httpInterceptor: MetricsRequestInterceptor[IO]

  def metricsFactory: MetricsTrackerFactory
}

object MetricsModule {
  final case class Config(namespace: String) {
    val httpNamespace = s"${namespace}_http"
    val grpcNamespace = s"${namespace}_grpc"
  }

  def prometheus(registry: CollectorRegistry, config: Config): Resource[IO, MetricsModule] =
    Resource.eval(IO(PrometheusMetrics.default[IO](config.httpNamespace, registry, MetricLabels.Default))).map {
      prometheusMetrics =>
        new MetricsModule {
          val pullEndpoint: ServerEndpoint[Any, IO]          = prometheusMetrics.metricsEndpoint
          val httpInterceptor: MetricsRequestInterceptor[IO] = prometheusMetrics.metricsInterceptor()
          val metricsFactory: MetricsTrackerFactory          = new PrometheusMetricsTrackerFactory(registry)
        }
    }

}
