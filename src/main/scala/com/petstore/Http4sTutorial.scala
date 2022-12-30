package com.petstore

object Http4sTutorial {

  // movie database
  type Actor = String
  case class Movie(id: String, title: String, year: Int, actors: List[Actor], director: String)
  case class Director(firstName: String, lastName: String) {
    override def toString = s"$firstName $lastName"
  }

}
