package pl.stasgroch.footballhub.data.remote

import pl.stasgroch.footballhub.data.model.FootballData
import retrofit2.http.GET

interface FootballApi {
    @GET("football")
    suspend fun football(): FootballData
}

