package com.pepotech.pepoboveda.camara

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.pepotech.pepoboveda.util.Diagnostico
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor principal: CameraX sobre camera2. Cada paso que puede fallar está protegido y
 * apuntado en el diagnóstico, y si en unos segundos no llega ningún frame se da por
 * roto: hay HAL que "abren" la cámara y no entregan nada.
 *
 * [alFrame] llega en el hilo de análisis con el plano Y compacto; el array se reutiliza
 * entre frames, así que no hay que guardarlo.
 */
class MotorCameraX(
    private val contexto: Context,
    private val dueno: LifecycleOwner,
    private val alFrame: (ByteArray, Int, Int) -> Unit,
    alEstado: (EstadoCamara) -> Unit
) : MotorBase("CameraX", alEstado) {

    companion object {
        private val RESOLUCION_DESEADA = Size(1280, 720)
        private const val ESPERA_CIERRE_MS = 1_500L
    }

    private val ejecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var proveedor: ProcessCameraProvider? = null
    private var previa: Preview? = null
    private var analisis: ImageAnalysis? = null
    private var camara: Camera? = null
    private var conImagen = true
    private var planoY = ByteArray(0)

    fun iniciar(vista: PreviewView) {
        empezarACronometrar()
        val futuro = try {
            ProcessCameraProvider.getInstance(contexto)
        } catch (e: Exception) {
            fallar("CameraX no arranca en este móvil", e)
            return
        }
        futuro.addListener({
            if (terminado.get()) return@addListener
            val prov = try {
                futuro.get()
            } catch (e: Exception) {
                fallar("CameraX no pudo inicializarse", e)
                return@addListener
            }
            proveedor = prov
            val selector = elegirCamara(prov)
            if (selector == null) {
                fallar("Este móvil no expone ninguna cámara a Android", null)
                return@addListener
            }
            enganchar(prov, selector, vista)
        }, ContextCompat.getMainExecutor(contexto))
    }

    private fun elegirCamara(prov: ProcessCameraProvider): CameraSelector? {
        for (candidato in listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)) {
            try {
                if (prov.hasCamera(candidato)) return candidato
            } catch (e: Exception) {
                Diagnostico.apuntar("camara", "CameraX: hasCamera falló", e)
            }
        }
        return try {
            prov.availableCameraInfos.firstOrNull()?.cameraSelector
        } catch (e: Exception) {
            Diagnostico.apuntar("camara", "CameraX: no se pudo listar las cámaras", e)
            null
        }
    }

    private fun enganchar(prov: ProcessCameraProvider, selector: CameraSelector, vista: PreviewView) {
        val analizador = crearAnalisis()
        analisis = analizador
        val vistaPrevia = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
        previa = vistaPrevia
        try {
            prov.unbindAll()
            camara = prov.bindToLifecycle(dueno, selector, vistaPrevia, analizador)
            conImagen = true
            Diagnostico.apuntar("camara", "CameraX: previsualización + análisis enganchados")
        } catch (e: Exception) {
            // Nivel LEGACY sin esa combinación de superficies, o algo peor. Segundo intento
            // solo con el análisis: sin imagen en pantalla, pero leyendo.
            Diagnostico.apuntar("camara", "CameraX: no admite previsualización + análisis; pruebo solo análisis", e)
            try {
                prov.unbindAll()
                previa = null
                camara = prov.bindToLifecycle(dueno, selector, analizador)
                conImagen = false
                Diagnostico.apuntar("camara", "CameraX: solo análisis enganchado")
            } catch (e2: Exception) {
                fallar("CameraX no pudo abrir la cámara", e2)
                return
            }
        }
        armarVigilante()
    }

    private fun crearAnalisis(): ImageAnalysis {
        val resolucion = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(RESOLUCION_DESEADA, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        val analizador = ImageAnalysis.Builder()
            .setResolutionSelector(resolucion)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analizador.setAnalyzer(ejecutor) { imagen ->
            try {
                if (terminado.get()) return@setAnalyzer
                anotarFrame({ "${imagen.width}x${imagen.height} formato ${imagen.format}" }, conImagen)
                val plano = imagen.planes[0]
                planoY = LectorQr.copiarPlanoY(plano.buffer, plano.rowStride, plano.pixelStride, imagen.width, imagen.height, planoY)
                alFrame(planoY, imagen.width, imagen.height)
            } catch (e: Exception) {
                if (!yaHayFrames) Diagnostico.apuntar("camara", "CameraX: el primer frame no se pudo leer", e)
                // Un frame raro no tira el motor; el siguiente llegará bien.
            } finally {
                try {
                    imagen.close()
                } catch (e: Exception) {
                    // ya cerrada
                }
            }
        }
        return analizador
    }

    override fun liberar() {
        try {
            analisis?.clearAnalyzer()
        } catch (e: Exception) {
            // ya sin analizador
        }
        // Solo nuestros casos de uso: unbindAll() es global y, mientras la pantalla anterior
        // termina su animación de salida, podría desmontar el escáner recién abierto.
        val prov = proveedor
        if (prov != null) {
            try {
                val propios = listOfNotNull(previa, analisis).toTypedArray()
                if (propios.isNotEmpty()) prov.unbind(*propios)
            } catch (e: Exception) {
                Diagnostico.apuntar("camara", "CameraX: unbind falló", e)
            }
        }
        ejecutor.shutdown()
    }

    /**
     * CameraX cierra el dispositivo en su propio hilo. El motor compatible no puede hacer
     * Camera.open hasta que el estado sea CLOSED, así que esperamos a verlo (con un tope,
     * por si el HAL no lo comunica).
     */
    override fun esperarCierre(alCerrada: () -> Unit) {
        val cam = camara ?: return alCerrada()
        val avisado = AtomicBoolean(false)
        val estado = cam.cameraInfo.cameraState
        lateinit var observador: Observer<CameraState>
        val terminar = {
            if (avisado.compareAndSet(false, true)) {
                try {
                    estado.removeObserver(observador)
                } catch (e: Exception) {
                    // ya quitado
                }
                alCerrada()
            }
        }
        observador = Observer { nuevo ->
            if (nuevo.type == CameraState.Type.CLOSED) {
                Diagnostico.apuntar("camara", "CameraX: cámara cerrada; el siguiente motor puede abrirla")
                terminar()
            }
        }
        try {
            estado.observeForever(observador)
        } catch (e: Exception) {
            terminar()
            return
        }
        principal.postDelayed({
            if (!avisado.get()) Diagnostico.apuntar("camara", "CameraX: no confirmó el cierre en ${ESPERA_CIERRE_MS} ms; sigo")
            terminar()
        }, ESPERA_CIERRE_MS)
    }
}
