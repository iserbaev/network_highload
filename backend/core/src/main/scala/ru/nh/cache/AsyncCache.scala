package ru.nh.cache

import cats.effect.std.Dispatcher
import cats.effect.{ IO, Resource }
import cats.syntax.traverse._
import com.github.benmanes.caffeine.cache

import java.util.concurrent.Executor
import scala.jdk.CollectionConverters._
import scala.jdk.FunctionConverters._

object AsyncCache {
  def apply[K, V](asyncCache: cache.AsyncCache[K, V]): Resource[IO, AsyncCache[K, V]] =
    Dispatcher.parallel[IO].map(new AsyncCache(asyncCache, _))
}

class AsyncCache[K, V] private[cache] (underlying: cache.AsyncCache[K, V], dispatcher: Dispatcher[IO]) {

  /** Returns the value associated with `key` in this cache, or `None` if there is no
    * cached value for `key`.
    *
    * @param key
    *   key whose associated value is to be returned
    * @return
    *   the current value to which the specified key is mapped, or `None` if this map
    *   contains no mapping for the key
    */
  def getIfPresent(key: K): IO[Option[V]] = IO.defer {
    Option(underlying.getIfPresent(key)).traverse(fromCF(_))
  }

  /** Returns the value associated with `key` in this cache, obtaining that value from
    * `mappingFunction` if necessary. This method provides a simple substitute for the
    * conventional "if cached, return; otherwise create, cache and return" pattern.
    *
    * @param key
    *   key with which the specified value is to be associated
    * @param mappingFunction
    *   the function to compute a value
    * @return
    *   the current (existing or computed) value associated with the specified key
    */
  def get(key: K, mappingFunction: K => V): IO[V] = fromCF {
    underlying.get(key, mappingFunction.asJava)
  }

  /** Returns the value associated with `key` in this cache, obtaining that value from
    * `mappingFunction` if necessary. This method provides a simple substitute for the
    * conventional "if cached, return; otherwise create, cache and return" pattern.
    *
    * @param key
    *   key with which the specified value is to be associated
    * @param mappingFunction
    *   the function to asynchronously compute a value
    * @return
    *   the current (existing or computed) value associated with the specified key
    * @throws java.lang.RuntimeException
    *   or Error if the mappingFunction does when constructing the value, in which case
    *   the mapping is left unestablished
    */
  def getF(key: K, mappingFunction: K => IO[V]): IO[V] = fromCF {
    underlying
      .get(
        key,
        (k: K, _: Executor) => toCF(mappingFunction(k), dispatcher)
      )
  }

  /** Returns the map of the values associated with `keys`, creating or retrieving those
    * values if necessary. The returned map contains entries that were already cached,
    * combined with newly loaded entries. If the any of the asynchronous computations
    * fail, those entries will be automatically removed from this cache.
    *
    * A single request to the `mappingFunction` is performed for all keys which are not
    * already present in the cache.
    *
    * @param keys
    *   the keys whose associated values are to be returned
    * @param mappingFunction
    *   the function to asynchronously compute the values
    * @return
    *   the map of keys to values for the specified keys in this cache
    * @throws java.lang.RuntimeException
    *   or Error if the mappingFunction does when constructing the values, in which case
    *   the mapping is left unestablished
    */
  def getAll(
      keys: Iterable[K],
      mappingFunction: Iterable[K] => Map[K, V]
  ): IO[Map[K, V]] = fromCF {
    underlying
      .getAll(
        keys.asJava,
        (ks: java.lang.Iterable[_ <: K]) => mappingFunction(ks.asScala).asJava
      )
  }.map(_.asScala.toMap)

  /** Returns the map of the values associated with `keys`, creating or retrieving those
    * values if necessary. The returned map contains entries that were already cached,
    * combined with newly loaded entries. If the any of the asynchronous computations
    * fail, those entries will be automatically removed from this cache.
    *
    * A single request to the `mappingFunction` is performed for all keys which are not
    * already present in the cache.
    *
    * @param keys
    *   the keys whose associated values are to be returned
    * @param mappingFunction
    *   the function to asynchronously compute the values
    * @return
    *   the map of keys to values for the specified keys in this cache
    * @throws java.lang.RuntimeException
    *   or Error if the mappingFunction does when constructing the values, in which case
    *   the mapping is left unestablished
    */
  def getAllF(
      keys: Iterable[K],
      mappingFunction: Iterable[K] => IO[Map[K, V]]
  ): IO[Map[K, V]] = fromCF {
    underlying
      .getAll(
        keys.asJava,
        (ks: java.lang.Iterable[_ <: K], _: Executor) => {
          toCF(mappingFunction(ks.asScala).map(_.asJava), dispatcher)
        }
      )
  }.map(_.asScala.toMap)

  /** Associates `value` with `key` in this cache. If the cache previously contained a
    * value associated with `key`, the old value is replaced by `value`. If the
    * asynchronous computation fails, the entry will be automatically removed.
    *
    * @param key
    *   key with which the specified value is to be associated
    * @param value
    *   value to be associated with the specified key
    */
  def putF(key: K, value: IO[V]): IO[Unit] =
    IO.delay(underlying.put(key, toCF(value, dispatcher)))

}
