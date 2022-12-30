package com.petstore

import cats._
import cats.effect._
import cats.implicits._
import org.http4s.circe._
import org.http4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._

import java.time.Year
import scala.collection.mutable
import scala.util.Try


object Http4sTutorial {

  // movie database
  type Actor = String
  case class Movie(id: String, title: String, year: Int, actors: List[Actor], director: String)
  case class Director(firstName: String, lastName: String) {
    override def toString = s"$firstName $lastName"
  }
  case class DirectorDetails(firstName: String, lastName: String, genre: String)

  // GET /movies?director=Zack%20Snyder&year=2021

  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
    QueryParamDecoder[Int].map(yearInt => Year.of(yearInt))

  object DirectorQueryParamMatcher extends QueryParamDecoderMatcher[String]("director")
  object YearQueryParamMatcher extends QueryParamDecoderMatcher[Year]("year")

  def movieRoutes[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "movies" :? DirectorQueryParamMatcher(director) +& YearQueryParamMatcher(year) => ???

      case GET -> Root / "movies" / UUIDVar(movieId) / "actors" => ???
    }
  }

  object DirectorPath {
    def unapply(name: String): Option[Director] = {
      Try {
        val tokens = name.split(" ")
        Director(tokens(0), tokens(1))
      }.toOption
    }
  }

  val directorDetailsDB: mutable.Map[Director, DirectorDetails] =
    mutable.Map(Director("Zack", "Snyder") -> DirectorDetails("Zack", "Snyder", "superhero"))

  def directorRoutes[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "directors" / DirectorPath(director) => ???
    }
  }

  def allRoutes[F[_]: Monad]: HttpRoutes[F] =
    movieRoutes[F] <+> directorRoutes[F]

  def allRoutesComplete[F[_]: Monad]: HttpApp[F] =
    allRoutes[F].orNotFound

}
