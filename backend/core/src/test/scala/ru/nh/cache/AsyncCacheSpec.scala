package ru.nh.cache

import cats.effect.IO
import weaver.{ Expectations, SimpleIOSuite }

object AsyncCacheSpec extends SimpleIOSuite {
  def cacheTest(name: String)(f: AsyncCache[String, String] => IO[Expectations]): Unit =
    test(name)(Caffeine().buildAsync[String, String].use(f))

  cacheTest("get value if present") { cache =>
    for {
      _        <- cache.putF("foo", IO.pure("present"))
      fooValue <- cache.getIfPresent("foo")
      barValue <- cache.getIfPresent("bar")
    } yield expect.all(
      fooValue.contains("present"),
      barValue.isEmpty
    )
  }

  cacheTest("get or compute value") { cache =>
    for {
      _        <- cache.putF("foo", IO.pure("present"))
      fooValue <- cache.get("foo", _ => "computed")
      barValue <- cache.get("bar", _ => "computed")
    } yield expect.all(
      fooValue.contains("present"),
      barValue.contains("computed")
    )
  }

  cacheTest("get or compute async value") { cache =>
    for {
      _        <- cache.putF("foo", IO.pure("present"))
      fooValue <- cache.getF("foo", _ => IO.pure("computed"))
      barValue <- cache.getF("bar", _ => IO.pure("computed"))
    } yield expect.all(
      fooValue.contains("present"),
      barValue.contains("computed")
    )
  }

  cacheTest("get or compute all values") { cache =>
    for {
      _      <- cache.putF("foo", IO.pure("present"))
      values <- cache.getAll(List("foo", "bar"), _.map(key => (key, "computed")).toMap)
    } yield expect(values == Map("foo" -> "present", "bar" -> "computed"))
  }

  cacheTest("get or compute async all values") { cache =>
    for {
      _ <- cache.putF("foo", IO.pure("present"))
      values <- cache.getAllF(
        List("foo", "bar"),
        keys => IO.pure(keys.map(key => (key, "computed")).toMap)
      )
    } yield expect(values == Map("foo" -> "present", "bar" -> "computed"))
  }

}
