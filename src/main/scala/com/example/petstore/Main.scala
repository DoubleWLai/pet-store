package com.example.petstore

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    PetstoreServer.stream[IO].compile.drain.as(ExitCode.Success)
}
