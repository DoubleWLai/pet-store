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
//import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder

import java.time.Year
import java.util.UUID
import scala.collection.mutable
import scala.util.Try


object Http4sTutorial extends IOApp {

  // movie database
  type Actor = String

  case class Movie(id: String, title: String, year: Int, actors: List[Actor], director: String)

  case class Director(firstName: String, lastName: String) {
    override def toString = s"$firstName $lastName"
  }

  // internal "database"
  case class DirectorDetails(firstName: String, lastName: String, genre: String)

  val snjl: Movie = Movie(
    "6bcbca1e-efd3-411d-9f7c-14b872444fce",
    "Zack Snyder's Justice League",
    2021,
    List("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa"),
    "Zack Snyder"
  )

  val movies: Map[String, Movie] = Map(snjl.id -> snjl)

  // business logic
  private def findMovieById(movieId: UUID) =
    movies.get(movieId.toString)

  private def findMoviesByDirector(director: String): List[Movie] =
    movies.values.filter(_.director == director).toList

  // GET /movies?director=Zack%20Snyder&year=2021

  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
    QueryParamDecoder[Int].emap(yearInt =>
      Try(Year.of(yearInt))
        .toEither
        .leftMap(e => ParseFailure(e.getMessage, e.getMessage))
    )

  object DirectorQueryParamMatcher extends QueryParamDecoderMatcher[String]("director")

  object YearQueryParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Year]("year")

  def movieRoutes[F[_] : Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "movies" :? DirectorQueryParamMatcher(director) +& YearQueryParamMatcher(maybeYear) =>
        val moviesByByDirector = findMoviesByDirector(director)
        maybeYear match {
          case Some(validatedYear) => validatedYear.fold(
            _ => BadRequest("The year was badly formatted"),
            year => {
              val moviesByByDirectorAndYear = moviesByByDirector.filter(_.year == year.getValue)
              Ok(moviesByByDirectorAndYear.asJson)
            }
          )
          case None => Ok(moviesByByDirector.asJson)
        }

      case GET -> Root / "movies" / UUIDVar(movieId) / "actors" =>
        findMovieById(movieId).map(_.actors) match {
          case Some(actors) => Ok(actors.asJson)
          case _ => NotFound(s"No movie with id $movieId found in the database")
        }
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

  def directorRoutes[F[_] : Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "directors" / DirectorPath(director) =>
        directorDetailsDB.get(director) match {
          case Some(dirDetails) => Ok(dirDetails.asJson)
          case _ => NotFound(s"No director '$director' found")
        }
    }
  }

  def allRoutes[F[_] : Monad]: HttpRoutes[F] =
    movieRoutes[F] <+> directorRoutes[F]


  override def run(args: List[String]): IO[ExitCode] = {
    val apis = Router(
      "/api" -> movieRoutes[IO],
      "/api/admin" -> directorRoutes[IO]
    ).orNotFound

    BlazeServerBuilder[IO](runtime.compute)
      .bindHttp(8080, "localhost")
      .withHttpApp(apis)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)

  }

}
