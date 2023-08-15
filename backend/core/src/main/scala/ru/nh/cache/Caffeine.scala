package ru.nh.cache

import cats.effect.std.Dispatcher
import cats.effect.{ IO, Resource }
import cats.syntax.all._
import com.github.benmanes.caffeine.cache.{ AsyncCacheLoader, Caffeine => JavaCaffeine, Expiry }

import java.util.concurrent.{ CompletableFuture, Executor }
import scala.compat.java8.DurationConverters._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object Caffeine {

  /** Constructs a new `Caffeine` instance with default settings, including strong keys,
    * strong values, and no automatic eviction of any kind.
    *
    * @return
    *   a new instance with default settings
    */
  def apply(): Caffeine[AnyRef, AnyRef] = new Caffeine(JavaCaffeine.newBuilder())
}

final class Caffeine[K, V] private (underlying: JavaCaffeine[K, V]) {

  /** Sets the minimum total size for the internal hash tables.
    *
    * @param initialCapacity
    *   minimum total size for the internal hash tables
    * @return
    *   this builder instance
    * @throws java.lang.IllegalArgumentException
    *   if initialCapacity
    * @throws java.lang.IllegalStateException
    *   if an initial capacity was already set
    */
  def initialCapacity(initialCapacity: Int): Caffeine[K, V] =
    new Caffeine(underlying.initialCapacity(initialCapacity))

  /** Specifies the executor to use when running asynchronous tasks.
    *
    * @param executor
    *   the executor to use for asynchronous execution
    * @return
    *   this builder instance
    */
  def executor(executor: Executor): Caffeine[K, V] =
    new Caffeine(underlying.executor(executor))

  /** Specifies the maximum number of entries the cache may contain.
    *
    * @param maximumSize
    *   the maximum size of the cache
    * @return
    *   this builder instance
    * @throws java.lang.IllegalArgumentException
    *   `size` is negative
    * @throws java.lang.IllegalStateException
    *   if a maximum size or weight was already set
    */
  def maximumSize(maximumSize: Long): Caffeine[K, V] =
    new Caffeine(underlying.maximumSize(maximumSize))

  /** Specifies that each entry should be automatically removed from the cache once a
    * fixed duration has elapsed after the entry's creation, or the most recent
    * replacement of its value.
    *
    * @param duration
    *   the length of time after an entry is created that it should be automatically
    *   removed
    * @return
    *   this builder instance
    * @throws java.lang.IllegalArgumentException
    *   if `duration` is negative
    * @throws java.lang.IllegalStateException
    *   if the time to live or time to idle was already set
    */
  def expireAfterWrite(duration: FiniteDuration): Caffeine[K, V] =
    new Caffeine(underlying.expireAfterWrite(duration.toJava))

  /** Specifies that each entry should be automatically removed from the cache once a
    * fixed duration has elapsed after the entry's creation, the most recent replacement
    * of its value, or its last read.
    *
    * @param duration
    *   the length of time after an entry is last accessed that it should be automatically
    *   removed
    * @return
    *   this builder instance
    * @throws java.lang.IllegalArgumentException
    *   if `duration` is negative
    * @throws java.lang.IllegalStateException
    *   if the time to idle or time to live was already set
    */
  def expireAfterAccess(duration: FiniteDuration): Caffeine[K, V] =
    new Caffeine(underlying.expireAfterAccess(duration.toJava))

  /** Specifies that each entry should be automatically removed from the cache once a
    * duration has elapsed after the entry's creation, the most recent replacement of its
    * value, or its last read.
    *
    * @param create
    *   the length of time an entry should be automatically removed from the cache after
    *   the entry's creation.
    * @param update
    *   the length of time an entry should be automatically removed from the cache after
    *   the replacement of it's value.
    * @param read
    *   the length of time an entry should be automatically removed from the cache after
    *   the entry's last read.
    * @tparam K1
    *   the key type of the expiry.
    * @tparam V1
    *   the value type of the expiry.
    * @return
    *   this builder instance
    * @throws java.lang.IllegalStateException
    *   if expiration was already set or used with expiresAfterAccess or
    *   expiresAfterWrite.
    */
  def expireAfter[K1 <: K, V1 <: V](
      create: (K1, V1) => FiniteDuration,
      update: (K1, V1, FiniteDuration) => FiniteDuration,
      read: (K1, V1, FiniteDuration) => FiniteDuration
  ): Caffeine[K1, V1] = {
    val expiry: Expiry[K1, V1] = new Expiry[K1, V1] {
      def expireAfterCreate(key: K1, value: V1, currentTime: Long): Long =
        create(key, value).toNanos

      def expireAfterUpdate(
          key: K1,
          value: V1,
          currentTime: Long,
          currentDuration: Long
      ): Long = update(key, value, currentDuration.nanos).toNanos

      def expireAfterRead(
          key: K1,
          value: V1,
          currentTime: Long,
          currentDuration: Long
      ): Long = read(key, value, currentDuration.nanos).toNanos
    }

    new Caffeine(underlying.expireAfter(expiry))
  }

  /** Specifies that active entries are eligible for automatic refresh once a fixed
    * duration has elapsed after the entry's creation, or the most recent replacement of
    * its value.
    *
    * @param duration
    *   the length of time after an entry is created that it should be considered stale,
    *   and thus eligible for refresh
    * @return
    *   this builder instance
    * @throws java.lang.IllegalArgumentException
    *   if `duration` is negative
    * @throws java.lang.IllegalStateException
    *   if the refresh interval was already set
    */
  def refreshAfterWrite(duration: FiniteDuration): Caffeine[K, V] =
    new Caffeine(underlying.refreshAfterWrite(duration.toJava))

  /** Builds a cache which does not automatically load values when keys are requested
    * unless a mapping function is provided. If the asynchronous computation fails value
    * then the entry will be automatically removed. Note that multiple threads can
    * concurrently load values for distinct keys.
    *
    * @tparam K1
    *   the key type of the cache
    * @tparam V1
    *   the value type of the cache
    * @return
    *   a cache having the requested features
    */
  def buildAsync[K1 <: K, V1 <: V]: Resource[IO, AsyncCache[K1, V1]] =
    AsyncCache(underlying.buildAsync[K1, V1]())

  /** Builds a cache, which either returns a CompletableFuture already loaded or currently
    * computing the value for a given key, or atomically computes the value asynchronously
    * through a supplied mapping function or the supplied AsyncCacheLoader. If the
    * asynchronous computation fails or computes a null value then the entry will be
    * automatically removed. Note that multiple threads can concurrently load values for
    * distinct keys. This method does not alter the state of this Caffeine instance, so it
    * can be invoked again to create multiple independent caches.
    *
    * @tparam K1
    *   the key type of the cache
    * @tparam V1
    *   the value type of the cache
    * @return
    *   a cache having the requested features
    */
  def buildAsyncLoading[K1 <: K, V1 <: V](
      load: K1 => IO[V1],
      bulkLoad: List[K1] => IO[Map[K1, V1]]
  ): Resource[IO, AsyncLoadingCache[K1, V1]] =
    Dispatcher
      .parallel[IO]
      .map { dispatcher =>
        new AsyncCacheLoader[K1, V1] {
          def asyncLoad(key: K1, executor: Executor): CompletableFuture[V1] =
            toCF(load(key), dispatcher)

          override def asyncLoadAll(
              keys: java.util.Set[_ <: K1],
              executor: Executor
          ): CompletableFuture[_ <: java.util.Map[_ <: K1, _ <: V1]] =
            toCF(bulkLoad(keys.asScala.toList).map(_.asJava), dispatcher)
        }
      }
      .flatMap { loader =>
        AsyncLoadingCache(underlying.buildAsync(loader))
      }

  def buildAsyncLoading[K1 <: K, V1 <: V](load: K1 => IO[V1]): Resource[IO, AsyncLoadingCache[K1, V1]] =
    buildAsyncLoading(load, _.parTraverse(k => load(k).tupleLeft(k)).map(_.toMap))

}
