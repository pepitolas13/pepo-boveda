package com.pepotech.pepoboveda.ui.pantallas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.Superficie
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import java.util.concurrent.Executors

@Composable
fun PantallaEscaner(vm: VaultViewModel, entradaDestino: String?) {
    val contexto = LocalContext.current
    var permiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var denegado by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var fallo by remember { mutableStateOf(false) }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permiso = concedido
        denegado = !concedido
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Añadir doble factor", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Text(
            "Casi todas las webs te dejan elegir: enseñarte un QR o darte el código escrito. Aquí puedes hacer las dos cosas, y la cámara solo se enciende si tú la pides.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario
        )
        Spacer(Modifier.height(16.dp))

        if (permiso) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                VistaCamara { texto ->
                    if (vm.altaTotp(texto, entradaDestino)) {
                        vm.avisar("Doble factor añadido")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Apunta al QR. En cuanto lo lea, se guarda.", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        } else {
            TarjetaPepo {
                Text("Escanear el QR", color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Para leerlo, Android tiene que darme la cámara. Se usa solo aquí, para descifrar ese QR, y no hay permiso de red con el que enviar nada.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                BotonAmbar("Usar la cámara") { pedirPermiso.launch(Manifest.permission.CAMERA) }
                if (denegado) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Sin permiso no hay cámara, y no pasa nada: escribe el código a mano aquí abajo.",
                        color = Peligro,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))) {
            Column(modifier = Modifier.fillMaxWidth().padding(0.dp)) {
                Text("O escríbelo a mano", color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                CampoPepo(
                    valor = manual,
                    etiqueta = "Clave del 2FA o enlace otpauth://",
                    alCambiar = { manual = it; fallo = false },
                    monoespaciada = true
                )
                if (fallo) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Eso no me sirve. Espero la clave en Base32 (letras A-Z y números 2-7) o un enlace otpauth://totp/...",
                        color = Peligro,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
                BotonAmbar("Añadir este código", activo = manual.isNotBlank()) {
                    if (vm.altaTotp(manual.trim(), entradaDestino)) {
                        vm.avisar("Doble factor añadido")
                    } else {
                        fallo = true
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BotonBorde("Cancelar") { if (!vm.retroceder()) vm.volverALista() }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun VistaCamara(alLeer: (String) -> Unit) {
    val contexto = LocalContext.current
    val duenoCiclo = LocalLifecycleOwner.current
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    val lector = remember {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)))
        }
    }
    var yaLeido by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { ejecutor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val vista = PreviewView(ctx)
            vista.setBackgroundColor(Superficie.value.toInt())
            val futuro = ProcessCameraProvider.getInstance(ctx)
            futuro.addListener({
                val proveedor = futuro.get()
                val previa = Preview.Builder().build().also {
                    it.surfaceProvider = vista.surfaceProvider
                }
                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analisis.setAnalyzer(ejecutor) { imagen ->
                    if (!yaLeido) {
                        val texto = decodificar(lector, imagen)
                        if (texto != null) {
                            yaLeido = true
                            vista.post { alLeer(texto) }
                        }
                    }
                    imagen.close()
                }
                try {
                    proveedor.unbindAll()
                    proveedor.bindToLifecycle(duenoCiclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, analisis)
                } catch (e: Exception) {
                    // Si el móvil no da cámara, queda el campo manual.
                }
            }, ContextCompat.getMainExecutor(ctx))
            vista
        }
    )
}

private fun decodificar(lector: MultiFormatReader, imagen: ImageProxy): String? {
    return try {
        val plano = imagen.planes[0]
        val buffer = plano.buffer
        val datos = ByteArray(buffer.remaining())
        buffer.get(datos)
        val fuente = PlanarYUVLuminanceSource(
            datos,
            plano.rowStride,
            imagen.height,
            0,
            0,
            minOf(plano.rowStride, imagen.width),
            imagen.height,
            false
        )
        lector.decodeWithState(BinaryBitmap(HybridBinarizer(fuente))).text
    } catch (e: Exception) {
        null
    } finally {
        lector.reset()
    }
}
