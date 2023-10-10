package ru.nh.db.tarantool

trait TarantoolModule {
  def client: TarantoolHttpClient
}

object TarantoolModule {}
