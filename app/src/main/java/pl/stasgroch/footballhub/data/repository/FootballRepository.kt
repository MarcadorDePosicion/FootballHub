package pl.stasgroch.footballhub.data.repository

import okhttp3.OkHttpClient
import pl.stasgroch.footballhub.data.remote.FootballApi
import pl.stasgroch.footballhub.data.remote.MockFootballInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FootballRepository(private val api: FootballApi) {
    suspend fun load() = api.football()

    companion object {
        // To connect a real backend, replace the URL and remove the mock interceptor.
        fun demo(): FootballRepository {
            val client = OkHttpClient.Builder().addInterceptor(MockFootballInterceptor()).build()
            val api = Retrofit.Builder().baseUrl("https://football-hub.example/api/")
                .client(client).addConverterFactory(GsonConverterFactory.create())
                .build().create(FootballApi::class.java)
            return FootballRepository(api)
        }
    }
}

