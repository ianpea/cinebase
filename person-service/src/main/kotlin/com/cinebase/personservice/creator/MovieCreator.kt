package com.cinebase.personservice.creator

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "movie_creator")
class MovieCreator(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "movie_id", nullable = false)
    var movieId: Long,

    @Column(name = "person_id", nullable = false)
    var personId: Long,

    @Column(nullable = false)
    var job: String,
)
