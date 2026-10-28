package com.example.geotracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF175E8D))) { GeoApp() } }
    }
}

@Composable
fun GeoApp(loginVm: LoginViewModel = viewModel(), captureVm: CaptureViewModel = viewModel(), locationsVm: LocationsViewModel = viewModel()) {
    val nav = rememberNavController()
    val loginState by loginVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(loginState.success) {
        if (loginState.success) nav.navigate("dashboard") { popUpTo("login") { inclusive = true } }
    }
    NavHost(navController = nav, startDestination = if (loginVm.isLoggedIn()) "dashboard" else "login") {
        composable("login") { LoginScreen(loginVm) }
        composable("dashboard") { Dashboard(onCapture = { nav.navigate("capture") }, onList = { nav.navigate("list") }, onMap = { nav.navigate("map") }, onLogout = { loginVm.logout(); nav.navigate("login") { popUpTo("dashboard") { inclusive = true } } }) }
        composable("capture") { CaptureScreen(captureVm, onBack = { nav.popBackStack() }) }
        composable("list") {
            LaunchedEffect(Unit) { locationsVm.load() }
            LocationsScreen(locationsVm, onBack = { nav.popBackStack() }, onMap = { nav.navigate("map") })
        }
        composable("map") {
            LaunchedEffect(Unit) { locationsVm.load() }
            MapScreen(locationsVm, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("GeoTracker", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Inicia sesión para registrar tus ubicaciones", modifier = Modifier.padding(bottom = 24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.login(email, password) }, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text(if (s.loading) "Conectando..." else "Iniciar sesión") }
        if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun Dashboard(onCapture: () -> Unit, onList: () -> Unit, onMap: () -> Unit, onLogout: () -> Unit) {
    Scaffold(topBar = { AppBar("GeoTracker", onLogout) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Panel principal", style = MaterialTheme.typography.headlineMedium)
            Text("Registra y consulta tus lugares de interés")
            Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddLocation, null); Spacer(Modifier.width(8.dp)); Text("Capturar ubicación GPS") }
            Button(onClick = onList, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.List, null); Spacer(Modifier.width(8.dp)); Text("Ver mis ubicaciones") }
            Button(onClick = onMap, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Ver mapa de ubicaciones") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, onBack: () -> Unit) { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }

@Composable
fun CaptureScreen(vm: CaptureViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        // El ViewModel valida los permisos y muestra un mensaje si fueron rechazados.
        vm.capture()
    }
    Scaffold(topBar = { AppBar("Nueva ubicación", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Capturar ubicación actual", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = s.name, onValueChange = vm::name, label = { Text("Nombre del lugar") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = s.description, onValueChange = vm::description, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(modifier = Modifier.fillMaxWidth(), enabled = !s.loading, onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text(if (s.loading) "Obteniendo GPS y guardando..." else "Capturar y guardar") }
            if (s.loading) CircularProgressIndicator()
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            s.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun LocationsScreen(vm: LocationsViewModel, onBack: () -> Unit, onMap: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppBar("Mis ubicaciones", onBack) }, floatingActionButton = { FloatingActionButton(onClick = onMap) { Icon(Icons.Default.Map, "Ir al mapa") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)); Button(onClick = vm::load) { Text("Reintentar") } }
            if (!s.loading && s.locations.isEmpty() && s.error == null) Text("Aún no tienes ubicaciones guardadas", modifier = Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.locations, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(item.description)
                            Text("Lat: ${item.latitude}  |  Long: ${item.longitude}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapScreen(vm: LocationsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val camera = rememberCameraPositionState { position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(LatLng(15.319, -91.47), 8f) }
    LaunchedEffect(s.locations, s.loading) {
        if (!s.loading && s.locations.isNotEmpty()) {
            val bounds = LatLngBounds.builder()
            s.locations.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
            if (s.locations.size == 1) camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(s.locations[0].latitude, s.locations[0].longitude), 15f))
            else camera.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
        }
    }
    Scaffold(topBar = { AppBar("Mapa de lugares", onBack) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camera) {
                s.locations.forEach { item ->
                    Marker(state = MarkerState(position = LatLng(item.latitude, item.longitude)), title = item.name, onClick = { vm.select(item); true })
                }
            }
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (!s.loading && s.locations.isEmpty() && s.error == null) Card(Modifier.align(Alignment.TopCenter).padding(16.dp)) { Text("No hay ubicaciones para mostrar", Modifier.padding(16.dp)) }
            s.error?.let { Card(Modifier.align(Alignment.TopCenter).padding(16.dp)) { Column(Modifier.padding(16.dp)) { Text(it); Button(onClick = vm::load) { Text("Reintentar") } } } }
        }
    }
    s.selected?.let { item ->
        AlertDialog(onDismissRequest = vm::dismiss, title = { Text(item.name) }, text = {
            Column { Text(item.description); Spacer(Modifier.height(8.dp)); Text("Latitud: ${item.latitude}"); Text("Longitud: ${item.longitude}") }
        }, confirmButton = { TextButton(onClick = vm::dismiss) { Text("Cerrar") } })
    }
}
