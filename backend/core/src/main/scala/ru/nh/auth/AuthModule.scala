package ru.nh.auth

import cats.effect.IO
import cats.effect.kernel.Resource
import ru.nh.auth.inmemory.InMemoryLoginAccessor

trait AuthModule {
  def service: AuthService
}

object AuthModule {
  def inMemory: Resource[IO, AuthModule] =
    InMemoryLoginAccessor
      .resource()
      .map(la =>
        new AuthModule {
          val service: AuthService = new AuthService(la)
        }
      )
}
