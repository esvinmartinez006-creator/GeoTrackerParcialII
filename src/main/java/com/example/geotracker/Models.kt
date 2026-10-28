package com.example.geotracker

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String? = null, val accessToken: String? = null, val message: String? = null)
data class NewLocation(val name: String, val description: String, val latitude: Double, val longitude: Double)
data class SavedLocation(
    val id: Long,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("created_at") val createdAt: String? = null
)
