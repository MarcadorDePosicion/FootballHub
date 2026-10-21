package pl.stasgroch.footballhub.data.remote

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

/** Local demo transport. Never calls chain.proceed(), so no server is contacted. */
class MockFootballInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val found = request.url.encodedPath == "/api/football"
        val allowed = request.method == "GET"
        val code = if (!found) 404 else if (!allowed) 405 else 200
        val json = if (code == 200) Gson().toJson(DemoData.create())
            else Gson().toJson(mapOf("error" to if (code == 404) "Unknown endpoint" else "Method not allowed"))
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message(if (code == 200) "OK" else "Mock API error")
            .header("X-FootballHub-Source", "local-mock")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
    }
}

