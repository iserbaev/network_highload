package ru.nh.cache

import cats.effect.std.Dispatcher
import cats.effect.{ IO, Resource }
import cats.syntax.traverse._
import com.github.benmanes.caffeine.cache

import scala.jdk.CollectionConverters._

object AsyncLoadingCache {
  def apply[K, V](asyncCache: cache.AsyncLoadingCache[K, V]): Resource[IO, AsyncLoadingCache[K, V]] =
    Dispatcher.parallel[IO].map(new AsyncLoadingCache(asyncCache, _))
}
class AsyncLoadingCache[K, V] private[cache] (underlying: cache.AsyncLoadingCache[K, V], dispatcher: Dispatcher[IO])
    extends AsyncCache(underlying, dispatcher) {

  /** Returns the future associated with key in this cache, obtaining that value from
    * AsyncCacheLoader.asyncLoad if necessary. If the asynchronous computation fails, the
    * entry will be automatically removed from this cache. If the specified key is not
    * already associated with a value, attempts to compute its value asynchronously and
    * enters it into this cache unless null. The entire method invocation is performed
    * atomically, so the function is applied at most once per key.
    *
    * @param key
    *   key with which the specified value is to be associated
    * @return
    *   the current (existing or computed) future value associated with the specified key
    * @throws java.lang.RuntimeException
    *   or Error if the AsyncCacheLoader does when constructing the future, in which case
    *   the mapping is left unestablished
    */
  def get(key: K): IO[Option[V]] =
    Option(underlying.get(key)).traverse(fromCF(_))

  /** Returns the future of a map of the values associated with keys, creating or
    * retrieving those values if necessary. The returned map contains entries that were
    * already cached, combined with newly loaded entries; it will never contain null keys
    * or values. If the any of the asynchronous computations fail, those entries will be
    * automatically removed from this cache. Caches loaded by a AsyncCacheLoader
    * supporting bulk loading will issue a single request to AsyncCacheLoader.asyncLoadAll
    * for all keys which are not already present in the cache. If another call to get
    * tries to load the value for a key in keys, that thread retrieves a future that is
    * completed by this bulk computation. Caches that do not use a AsyncCacheLoader with
    * an optimized bulk load implementation will sequentially load each key by making
    * individual AsyncCacheLoader.asyncLoad calls. Note that multiple threads can
    * concurrently load values for distinct keys. Note that duplicate elements in keys, as
    * determined by Object.equals, will be ignored.
    *
    * @param keys
    *   the keys whose associated values are to be returned
    * @return
    *   a future containing an unmodifiable mapping of keys to values for the specified
    *   keys in this cache
    * @throws java.lang.NullPointerException
    *   if the specified collection is null or contains a null element, or if the future
    *   returned by the AsyncCacheLoader is null
    * @throws java.lang.RuntimeException
    *   or Error if the AsyncCacheLoader does so, if AsyncCacheLoader.asyncLoadAll returns
    *   null, or fails when constructing the future, in which case the mapping is left
    *   unestablished
    */
  def getAll(keys: Iterable[K]): IO[Map[K, V]] = fromCF(underlying.getAll(keys.asJava)).map(_.asScala.toMap)
}
