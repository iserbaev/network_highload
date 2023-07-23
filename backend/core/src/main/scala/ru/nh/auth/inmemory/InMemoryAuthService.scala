//package ru.nh.auth.inmemory
//
//import cats.effect.{IO, Ref}
//import pdi.jwt._
//import ru.nh.auth.AuthService
//import ru.nh.auth.AuthService.Token
//
//import java.time.Instant
//
//class InMemoryAuthService (userPasswords: Ref[IO, Map[String, String]]) extends AuthService[IO] {
//  private val key: String        = "secretKey"
//  private val algo: JwtAlgorithm = JwtAlgorithm.HS256
//
//  private def buildToken(id: String, password: String) = {
//    val claim = JwtClaim(
//      content = s"""{"id":"$id", "password":"$password"}""",
//      expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
//      issuedAt = Some(Instant.now.getEpochSecond)
//    )
//
//    Token(JwtCirce.encode(claim, key, algo))
//  }
//
//  private def decodeToken(token: Token) =
////    JwtCirce.decode(token.token).map{ claim =>
////
////    }
//
//    ???
//
//  def login(id: String, password: String): IO[Option[AuthService.Token]] =
//    userPasswords.get.map(_.get(id).filter(_ == password).map { _ =>
//      buildToken(id, password)
//    })
//
//  def authorize(token: String): IO[AuthService.Auth] = ???
//}
