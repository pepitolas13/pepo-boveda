package com.pepotech.pepoboveda.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.pepotech.pepoboveda.camara.MotorCamara
import com.pepotech.pepoboveda.camara.PermisoCamara
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.data.modoBiometriaActivo

/**
 * Lo que sabe Android de este móvil y que importa para la cámara y la huella. Es la
 * parte del informe que depende de Android; el registro en sí vive en [Diagnostico].
 * Aquí tampoco entra nada personal: modelo, versión, capacidades y ajustes, nada más.
 *
 * [estado] habla con el servicio de biometría y con el de cámara (varias llamadas Binder,
 * lentas en HAL viejos): llamarlo fuera del hilo principal.
 */
object InformeDiagnostico {

    fun cabecera(contexto: Context): String {
        val version = try {
            val info = contexto.packageManager.getPackageInfo(contexto.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (e: Exception) {
            "?"
        }
        return buildString {
            append("Pepo Bóveda ").append(version).append('\n')
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" (").append(Build.DEVICE).append(")\n")
            append("Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Build: ").append(Build.DISPLAY)
        }
    }

    /** Líneas del estado en vivo, para la pantalla y para el informe. */
    fun estado(contexto: Context, repositorio: VaultRepository): List<String> {
        val lineas = ArrayList<String>()
        val ajustes = repositorio.ajustes.actual

        val c = Biometria.capacidad(contexto)
        val nivel = Biometria.decidirNivel(c)
        lineas += "Huella: nivel ${nivel.name.lowercase()}"
        lineas += "  Clase 3: ${Biometria.explicar(c.fuerte)}"
        lineas += "  Clase 2: ${Biometria.explicar(c.debil)}"
        lineas += "  PIN del móvil: ${Biometria.explicar(c.credencial)}"
        lineas += "  Clase 2 o PIN: ${Biometria.explicar(c.compatible)}"
        val modo = ajustes.modoBiometriaActivo
        lineas += "  Modo activo en la app: ${modo?.etiqueta ?: "ninguno"}"
        if (modo != null) {
            lineas += "  Clave del modo en el Keystore: ${if (repositorio.biometria.estaConfigurada(modo)) "presente" else "ausente"}"
        }

        lineas += "Cámara: permiso ${if (PermisoCamara.concedido(contexto)) "concedido" else "no concedido"}"
        lineas += "  Motor ajustado: ${MotorCamara.desde(ajustes.motorCamara).etiqueta}"
        lineas += camaras(contexto)
        return lineas
    }

    private fun camaras(contexto: Context): List<String> {
        val gestor = contexto.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return listOf("  camera2: servicio no disponible")
        return try {
            val ids = gestor.cameraIdList
            if (ids.isEmpty()) return listOf("  camera2: ninguna cámara")
            ids.map { id ->
                try {
                    val car = gestor.getCameraCharacteristics(id)
                    val cara = when (car.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_BACK -> "trasera"
                        CameraCharacteristics.LENS_FACING_FRONT -> "frontal"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "externa"
                        else -> "desconocida"
                    }
                    val nivel = when (car.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                        else -> "?"
                    }
                    "  camera2 id $id: $cara, nivel $nivel"
                } catch (e: Exception) {
                    "  camera2 id $id: no se pudieron leer sus características (${Diagnostico.describir(e)})"
                }
            }
        } catch (e: Exception) {
            listOf("  camera2: no se pudo listar (${Diagnostico.describir(e)})")
        }
    }

    /** El informe con un [estado] ya calculado (el que la pantalla tiene en memoria). */
    fun generar(contexto: Context, estado: List<String>): String =
        Diagnostico.informe(cabecera(contexto), estado.joinToString("\n"))
}
