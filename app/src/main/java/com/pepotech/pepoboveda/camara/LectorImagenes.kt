package com.pepotech.pepoboveda.camara

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.pepotech.pepoboveda.util.Diagnostico
import kotlin.math.ceil
import kotlin.math.max

/**
 * Busca un QR en una imagen elegida por el usuario (una captura, una foto). No necesita
 * permiso de cámara ni de almacenamiento: el selector del sistema nos da acceso solo a
 * ese archivo. Sirve cuando la cámara no va, o cuando el QR está en la pantalla del
 * propio móvil.
 */
object LectorImagenes {

    private const val LADO_MAXIMO = 1600

    fun leerQr(contexto: Context, uri: Uri): String? {
        val bitmap = try {
            val fuente = ImageDecoder.createSource(contexto.contentResolver, uri)
            ImageDecoder.decodeBitmap(fuente) { decodificador, info, _ ->
                // Software: con el asignador por defecto (hardware) no se pueden leer los píxeles.
                decodificador.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decodificador.isMutableRequired = false
                val mayor = max(info.size.width, info.size.height)
                if (mayor > LADO_MAXIMO) {
                    decodificador.setTargetSampleSize(ceil(mayor / LADO_MAXIMO.toDouble()).toInt())
                }
            }
        } catch (e: Exception) {
            // Solo la clase: el mensaje puede llevar el nombre del archivo elegido.
            Diagnostico.apuntar("camara", "No se pudo abrir la imagen elegida: ${e.javaClass.simpleName}")
            return null
        }
        return try {
            decodificar(bitmap)
        } catch (e: Exception) {
            Diagnostico.apuntar("camara", "No se pudo leer la imagen elegida: ${e.javaClass.simpleName}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodificar(bitmap: Bitmap): String? {
        val ancho = bitmap.width
        val alto = bitmap.height
        val pixeles = IntArray(ancho * alto)
        bitmap.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        val texto = LectorQr.decodificarPixeles(pixeles, ancho, alto)
        Diagnostico.apuntar("camara", "Imagen ${ancho}x${alto}: ${if (texto != null) "QR encontrado" else "sin QR"}")
        return texto
    }
}
