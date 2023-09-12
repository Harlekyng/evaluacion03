package com.example.evaluacion03

import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime

enum class Pantalla {
    FORMULARIO,
    FOTO,
    VISTA_PREVIA
}

class GeolocalizacionAppViewModel : ViewModel() {
    val pantalla = mutableStateOf(Pantalla.FORMULARIO)
    val fotosTomadas = mutableListOf<Uri>()
    var onPermisoCamaraOk: () -> Unit = {}
    var onPermisoUbicacionOk: () -> Unit = {}
    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null

    fun cambiarPantalla(pantallaNueva: Pantalla) {
        pantalla.value = pantallaNueva
    }
}

class GeolocalizacionFormViewModel : ViewModel() {
    val lugarVisitado = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val registroFotografico = mutableStateOf<Uri?>(null)
    val mostrarImagenDetalle = mutableStateOf(false)
    val imagenDetalleUri = mutableStateOf<Uri?>(null)
}

class MainActivity : ComponentActivity() {
    val geolocalizacionAppVm: GeolocalizacionAppViewModel by viewModels()
    lateinit var cameraController: LifecycleCameraController

    val lanzadorPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false) or (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicación concedido")
                    geolocalizacionAppVm.onPermisoUbicacionOk()
                }

                (it[android.Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso cámara concedido")
                    geolocalizacionAppVm.onPermisoCamaraOk()
                }

                else -> {
                }
            }
        }

    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geolocalizacionAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}

fun generarNombreSegunFechaHastaSegundo(): String =
    LocalDateTime.now().toString().replace(Regex(" [T:.-]"), "").substring(0, 14)

fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)

fun uri2imageBitmap(uri: Uri, contexto: Context) =
    BitmapFactory.decodeStream(contexto.contentResolver.openInputStream(uri)).asImageBitmap()

fun tomarFotografia(
    cameraController: CameraController,
    archivo: File,
    contexto: Context,
    imagenGuardadaOk: (uri: Uri) -> Unit
) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()

    cameraController.takePicture(
        outputFileOptions, ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also { uri ->
                    Log.v("tomarFotografia()::onImageSaved", "Foto guardada en ${uri.toString()}")
                    imagenGuardadaOk(uri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("tomarFotografia()", "Error: ${exception.message}")
            }
        }
    )
}

class SinPermisoException(mensaje: String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk: (location: Location) -> Unit) {
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e: SecurityException) {
        throw SinPermisoException(e.message ?: "No tiene permisos para obtener la ubicación")
    }
}

@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current

    val geolocalizacionAppViewModel: GeolocalizacionAppViewModel = viewModel()
    val geolocalizacionFormVm: GeolocalizacionFormViewModel = viewModel()

    when (geolocalizacionAppViewModel.pantalla.value) {
        Pantalla.FORMULARIO -> {
            GeolocalizacionFormUI(
                geolocalizacionFormVm,
                tomarFotoOnClick = {
                    geolocalizacionAppViewModel.cambiarPantalla(Pantalla.FOTO)
                    geolocalizacionAppViewModel.lanzadorPermisos?.launch(
                        arrayOf(
                            android.Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    geolocalizacionAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            geolocalizacionFormVm.latitud.value = it.latitude
                            geolocalizacionFormVm.longitud.value = it.longitude
                        }
                    }
                    geolocalizacionAppViewModel.lanzadorPermisos?.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }

        Pantalla.FOTO -> {
            GeolocalizacionPhotoUI(
                geolocalizacionFormVm, geolocalizacionAppViewModel,
                cameraController)
        }

        Pantalla.VISTA_PREVIA -> {
            GeolocalizacionPreviewUI(
                geolocalizacionFormVm,
                geolocalizacionAppViewModel,
                context = contexto
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeolocalizacionFormUI(
    geolocalizacionFormVm: GeolocalizacionFormViewModel,
    tomarFotoOnClick: () -> Unit = {},
    actualizarUbicacionOnClick: () -> Unit = {}
) {
    val contexto = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            label = { Text("Lugar visitado") },
            value = geolocalizacionFormVm.lugarVisitado.value,
            onValueChange = { geolocalizacionFormVm.lugarVisitado.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Text("Registro fotográfico del lugar visitado:")
        Button(
            onClick = {
                tomarFotoOnClick()
            },
            modifier = Modifier
                .padding(top = 16.dp)
        ) {
            Text("Tomar Fotografía")
        }
        geolocalizacionFormVm.registroFotografico.value?.also {
            Box(Modifier.size(200.dp, 100.dp)) {
                Image(
                    painter = BitmapPainter(
                        uri2imageBitmap(
                            it,
                            contexto
                        )
                    ),
                    contentDescription = "Imagen del lugar visitado a ${geolocalizacionFormVm.lugarVisitado.value}"
                )
            }
            Text("Ubicación: lat: ${geolocalizacionFormVm.latitud.value} long: ${geolocalizacionFormVm.longitud.value}")
            Button(
                onClick = {
                    actualizarUbicacionOnClick()
                },
                modifier = Modifier
                    .padding(top = 16.dp)
            ) {
                Text("Actualizar Ubicación")
            }
            Spacer(Modifier.height(100.dp))
            MapaOsmUI(geolocalizacionFormVm.latitud.value, geolocalizacionFormVm.longitud.value)
        }
    }
}

@Composable
fun GeolocalizacionPhotoUI(
    geolocalizacionFormVm: GeolocalizacionFormViewModel,
    geolocalizacionAppViewModel: GeolocalizacionAppViewModel,
    cameraController: CameraController
) {
    val contexto = LocalContext.current
    AndroidView(
        factory = {
            PreviewView(it).apply { controller = cameraController }
        },
        modifier = Modifier.fillMaxSize()
    )
    Button(
        onClick = {
            tomarFotografia(
                cameraController, crearArchivoImagenPrivado(contexto), contexto
            ) {
                geolocalizacionFormVm.registroFotografico.value = it
                geolocalizacionAppViewModel.fotosTomadas.add(it)
                geolocalizacionAppViewModel.cambiarPantalla(Pantalla.VISTA_PREVIA)
            }
        },
        modifier = Modifier
            .padding(top = 16.dp)
    ) {
        Text("Tomar fotografía")
    }
}

@Composable
fun GeolocalizacionPreviewUI(
    geolocalizacionFormVm: GeolocalizacionFormViewModel,
    geolocalizacionAppViewModel: GeolocalizacionAppViewModel,
    context: Context
) {
    val contexto = LocalContext.current

    if (geolocalizacionFormVm.mostrarImagenDetalle.value) {
        AlertDialog(
            onDismissRequest = {
                geolocalizacionFormVm.mostrarImagenDetalle.value = false
                geolocalizacionFormVm.imagenDetalleUri.value = null
            },
            title = {
                Text("Lugar visitado")
            },
            text = {
                geolocalizacionFormVm.registroFotografico.value?.also { uri ->
                    Box(Modifier.size(200.dp, 200.dp)) {
                        Image(
                            painter = BitmapPainter(
                                uri2imageBitmap(uri, contexto)
                            ),
                            contentDescription = "Imagen del lugar visitado a ${geolocalizacionFormVm.lugarVisitado.value}"
                        )
                    }
                }
                Text("Lugar: ${geolocalizacionFormVm.lugarVisitado.value}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        geolocalizacionFormVm.mostrarImagenDetalle.value = false
                        geolocalizacionFormVm.imagenDetalleUri.value = null
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            geolocalizacionAppViewModel.fotosTomadas.forEach { fotoUri ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(Modifier.size(200.dp, 200.dp)) {
                        Image(
                            painter = BitmapPainter(
                                uri2imageBitmap(fotoUri, contexto)
                            ),
                            contentDescription = "Imagen del lugar visitado a ${geolocalizacionFormVm.lugarVisitado.value}"
                        )
                    }
                    Button(
                        onClick = {
                            geolocalizacionFormVm.mostrarImagenDetalle.value = true
                            geolocalizacionFormVm.imagenDetalleUri.value = fotoUri
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Ver Detalle")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                geolocalizacionAppViewModel.cambiarPantalla(Pantalla.FORMULARIO)
            },
            modifier = Modifier
                .padding(top = 16.dp)
        ) {
            Text("Volver")
        }
    }
}

@Composable
fun MapaOsmUI(latitud: Double, longitud: Double) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue =
                    contexto.packageName
            }
        }, update = {
            it.overlays.removeIf { true }
            it.invalidate()

            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)

            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}

