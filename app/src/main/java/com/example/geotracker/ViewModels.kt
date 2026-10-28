package com.example.geotracker

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

fun readableError(error: Throwable): String = when (error) {
    is HttpException -> when (error.code()) {
        400 -> "Datos incorrectos. Verifica la información."
        401 -> "No autorizado: verifica tus credenciales o vuelve a iniciar sesión."
        404 -> "Endpoint no encontrado: comprueba la dirección de la API."
        else -> "El servidor respondió con error ${error.code()}."
    }
    is java.net.UnknownHostException, is java.net.ConnectException -> "No se pudo conectar con el servidor."
    else -> error.message ?: "Ocurrió un error inesperado."
}

data class LoginState(val loading: Boolean = false, val error: String? = null, val success: Boolean = false)
class LoginViewModel(app: Application): AndroidViewModel(app) {
    private val repository = GeoRepository(app)
    private val mutable = MutableStateFlow(LoginState())
    val state = mutable.asStateFlow()
    fun isLoggedIn() = repository.tokens.get() != null
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) { mutable.value = LoginState(error = "Escribe tu correo y contraseña"); return }
        viewModelScope.launch {
            mutable.value = LoginState(loading = true)
            try { repository.login(email, password); mutable.value = LoginState(success = true) }
            catch (e: Exception) { mutable.value = LoginState(error = readableError(e)) }
        }
    }
    fun logout() { repository.tokens.clear(); mutable.value = LoginState() }
}

data class CaptureState(val name: String = "", val description: String = "", val loading: Boolean = false, val error: String? = null, val success: String? = null)
class CaptureViewModel(app: Application): AndroidViewModel(app) {
    private val repository = GeoRepository(app)
    private val stateMutable = MutableStateFlow(CaptureState())
    val state = stateMutable.asStateFlow()
    fun name(value: String) { stateMutable.value = stateMutable.value.copy(name = value, error = null, success = null) }
    fun description(value: String) { stateMutable.value = stateMutable.value.copy(description = value, error = null, success = null) }
    fun capture() {
        val ctx = getApplication<Application>()
        val s = stateMutable.value
        if (s.name.isBlank() || s.description.isBlank()) { stateMutable.value = s.copy(error = "Completa el nombre y la descripción"); return }
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { stateMutable.value = s.copy(error = "Debes conceder el permiso de ubicación"); return }
        viewModelScope.launch {
            stateMutable.value = s.copy(loading = true, error = null, success = null)
            try {
                val location = LocationServices.getFusedLocationProviderClient(ctx)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                    ?: throw IllegalStateException("No se pudo obtener el GPS. Enciende la ubicación e inténtalo nuevamente.")
                repository.add(NewLocation(s.name.trim(), s.description.trim(), location.latitude, location.longitude))
                stateMutable.value = CaptureState(success = "Ubicación guardada correctamente")
            } catch (e: Exception) { stateMutable.value = stateMutable.value.copy(loading = false, error = readableError(e)) }
        }
    }
}

data class LocationsState(val loading: Boolean = false, val locations: List<SavedLocation> = emptyList(), val error: String? = null, val selected: SavedLocation? = null)
class LocationsViewModel(app: Application): AndroidViewModel(app) {
    private val repository = GeoRepository(app)
    private val mutable = MutableStateFlow(LocationsState())
    val state = mutable.asStateFlow()
    fun load() {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try { mutable.value = mutable.value.copy(loading = false, locations = repository.list()) }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, error = readableError(e)) }
        }
    }
    fun select(item: SavedLocation) { mutable.value = mutable.value.copy(selected = item) }
    fun dismiss() { mutable.value = mutable.value.copy(selected = null) }
}
