package com.example.geotracker

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): com.google.gson.JsonObject
    @POST("api/locations") suspend fun addLocation(@Body body: NewLocation): SavedLocation
    @GET("api/locations") suspend fun getLocations(): List<SavedLocation>
}

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("geo_session", Context.MODE_PRIVATE)
    fun get(): String? = prefs.getString("jwt", null)
    fun save(token: String) = prefs.edit().putString("jwt", token).apply()
    fun clear() = prefs.edit().remove("jwt").apply()
}

class GeoRepository(context: Context) {
    val tokens = TokenStore(context)
    private val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val old = chain.request()
            val builder = old.newBuilder()
            tokens.get()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }).build()
    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(ApiService::class.java)

    suspend fun login(email: String, password: String) {
        val result = api.login(LoginRequest(email.trim(), password))

        fun findToken(json: com.google.gson.JsonObject): String? {
            for (key in listOf("token", "accessToken", "access_token", "jwt")) {
                val value = json.get(key)
                if (value != null && value.isJsonPrimitive &&
                    value.asString.isNotBlank()) {
                    return value.asString
                }
            }

            for (key in listOf("body", "data", "standardResponse")) {
                val nested = json.get(key)
                if (nested != null && nested.isJsonObject) {
                    val found = findToken(nested.asJsonObject)
                    if (found != null) return found
                }
            }

            return null
        }

        val token = findToken(result)

        require(!token.isNullOrBlank()) {
            "No se encontró el token JWT en la respuesta del servidor"
        }

        tokens.save(token)
    }
    suspend fun add(item: NewLocation) = api.addLocation(item)
    suspend fun list() = api.getLocations()
}
