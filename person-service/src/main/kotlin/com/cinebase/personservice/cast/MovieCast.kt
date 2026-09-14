package com.cinebase.personservice.cast

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "movie_cast")
class MovieCast(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "movie_id", nullable = false)
    var movieId: Long,

    @Column(name = "person_id", nullable = false)
    var personId: Long,

    @Column(name = "character_name", nullable = false)
    var characterName: String,
)
