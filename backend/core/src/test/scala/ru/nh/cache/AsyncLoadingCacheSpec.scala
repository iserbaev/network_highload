package ru.nh.cache

import cats.effect.IO
import weaver.{ Expectations, SimpleIOSuite }

object AsyncLoadingCacheSpec extends SimpleIOSuite {
  def asyncLoadingCacheTest(name: String)(f: AsyncLoadingCache[String, String] => IO[Expectations]): Unit =
    test(name)(
      Caffeine()
        .buildAsyncLoading[String, String]((key: String) => IO.realTimeInstant.map(i => key ++ i.toEpochMilli.toString))
        .use(f)
    )

  asyncLoadingCacheTest("get value if present") { cache =>
    for {
      notPresent  <- cache.getIfPresent("foo")
      asyncLoaded <- cache.get("foo")
      cachedValue <- cache.getIfPresent("foo")
    } yield expect.all(
      notPresent.isEmpty,
      asyncLoaded.nonEmpty,
      cachedValue == asyncLoaded
    )
  }

  asyncLoadingCacheTest("async load or compute value") { cache =>
    for {
      computedValue          <- cache.get("foo", _ => "computed")
      computedAndCachedValue <- cache.get("foo")
      asyncLoadedBarValue    <- cache.get("bar")
      cachedBarValue         <- cache.get("bar", _ => "computed")
    } yield expect.all(
      computedValue.contains("computed"),
      computedAndCachedValue.contains("computed"),
      asyncLoadedBarValue.nonEmpty,
      cachedBarValue != "computed"
    )
  }

  asyncLoadingCacheTest("async load or compute async value") { cache =>
    for {
      computedValueF      <- cache.getF("foo", _ => IO("computed"))
      cachedValueF        <- cache.get("foo")
      asyncLoadedBarValue <- cache.get("bar")
      cachedBarValue      <- cache.getF("bar", _ => IO("computed"))
    } yield expect.all(
      computedValueF == "computed",
      cachedValueF.contains("computed"),
      asyncLoadedBarValue.nonEmpty,
      cachedBarValue != "computed"
    )
  }

  asyncLoadingCacheTest("async load all or compute all values") { cache =>
    for {
      asyncLoadedAll <- cache.getAll(List("foo", "bar"))
      cachedAll      <- cache.getAll(List("foo", "bar"))
      computedAll    <- cache.getAll(List("fooC", "barC"), _.map(key => (key, "computed")).toMap)
      computedAllF <- cache.getAllF(
        List("fooF", "barF"),
        keys => IO.pure(keys.map(key => (key, "computedF")).toMap)
      )
    } yield expect.all(
      asyncLoadedAll.nonEmpty,
      cachedAll == asyncLoadedAll,
      computedAll == Map("fooC" -> "computed", "barC" -> "computed"),
      computedAllF == Map("fooF" -> "computedF", "barF" -> "computedF")
    )
  }

}
