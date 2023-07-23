package ru.nh.http

final case class ErrorResponse(message: String, request_id: String, code: Int)
