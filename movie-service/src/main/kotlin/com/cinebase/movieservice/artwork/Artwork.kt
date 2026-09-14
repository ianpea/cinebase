package com.cinebase.movieservice.artwork

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "artworks")
class Artwork(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "movie_id", nullable = false)
    var movieId: Long,

    @Column(nullable = false)
    var url: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ArtworkType,
)
