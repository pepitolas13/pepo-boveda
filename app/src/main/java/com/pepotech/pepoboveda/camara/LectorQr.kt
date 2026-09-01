package com.pepotech.pepoboveda.camara

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/**
 * Decodificación de QR con ZXing, sin nada de Android dentro: recibe bytes de luminancia
 * o píxeles ARGB y devuelve el texto. Así se prueba en la JVM y lo comparten los dos
 * motores de cámara y la lectura desde una imagen.
 */
object LectorQr {

    private val hintsRapidos: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
    )

    private val hintsAFondo: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    private val lector = ThreadLocal.withInitial { QRCodeReader() }

    /**
     * Copia el plano Y de un frame YUV_420_888 a un array compacto de [ancho]x[alto].
     * Los HAL de MediaTek (y otros) rellenan cada fila hasta [rowStride], que puede ser
     * mayor que el ancho; si se pasa el buffer tal cual a ZXing la imagen sale sesgada.
     * Si [destino] tiene sitio se reutiliza: a 30 frames por segundo, reservar un array
     * nuevo cada vez es medio megabyte de basura por frame.
     */
    fun copiarPlanoY(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        ancho: Int,
        alto: Int,
        destino: ByteArray? = null
    ): ByteArray {
        val necesario = ancho * alto
        val salida = if (destino != null && destino.size >= necesario) destino else ByteArray(necesario)
        val base = buffer.position()
        val limite = buffer.limit()
        val paso = if (pixelStride <= 0) 1 else pixelStride
        if (paso == 1) {
            val copia = buffer.duplicate()
            for (fila in 0 until alto) {
                val inicio = base + fila * rowStride
                val n = minOf(ancho, limite - inicio)
                if (n <= 0) break
                copia.position(inicio)
                copia.get(salida, fila * ancho, n)
            }
        } else {
            for (fila in 0 until alto) {
                val inicio = base + fila * rowStride
                for (col in 0 until ancho) {
                    val indice = inicio + col * paso
                    if (indice >= limite) break
                    salida[fila * ancho + col] = buffer.get(indice)
                }
            }
        }
        return salida
    }

    /**
     * Frame de cámara: luminancia compacta (un byte por píxel; el array puede ser más
     * largo, como un NV21 entero). Rápido; se llama muchas veces por segundo.
     * [probarInvertido] añade un segundo intento con la imagen invertida, para los QR
     * claros sobre negro de las webs en modo oscuro: cuesta otra pasada entera, así que
     * los motores lo piden solo cada pocos frames.
     */
    fun decodificarLuminancia(datos: ByteArray, ancho: Int, alto: Int, probarInvertido: Boolean = true): String? {
        if (ancho <= 0 || alto <= 0 || datos.size < ancho * alto) return null
        val fuente = PlanarYUVLuminanceSource(datos, ancho, alto, 0, 0, ancho, alto, false)
        return decodificar(fuente, hintsRapidos)
            ?: if (probarInvertido) decodificar(fuente.invert(), hintsRapidos) else null
    }

    /** Imagen de la galería o captura: píxeles ARGB. Aquí sí merece la pena TRY_HARDER. */
    fun decodificarPixeles(pixeles: IntArray, ancho: Int, alto: Int): String? {
        if (ancho <= 0 || alto <= 0 || pixeles.size < ancho * alto) return null
        val fuente = RGBLuminanceSource(ancho, alto, pixeles)
        return decodificar(fuente, hintsAFondo) ?: decodificar(fuente.invert(), hintsAFondo)
    }

    /**
     * Los QR de llave de acceso entre dispositivos (CTAP 2.2 hybrid) empiezan por "FIDO:/".
     * No los podemos atender: ese transporte necesita un túnel por internet y Bluetooth, y
     * esta app no pide permiso de red. Los detectamos para explicarlo en vez de dar un error
     * genérico que no le dice nada a nadie.
     */
    fun esQrDePasskey(texto: String): Boolean {
        val limpio = texto.trim()
        return limpio.startsWith("FIDO:/", ignoreCase = true) ||
            limpio.startsWith("fido:", ignoreCase = true)
    }

    private fun decodificar(fuente: LuminanceSource, hints: Map<DecodeHintType, Any>): String? {
        val qr = lector.get() ?: QRCodeReader()
        return try {
            qr.decode(BinaryBitmap(HybridBinarizer(fuente)), hints).text
        } catch (e: Exception) {
            // NotFoundException, ChecksumException, FormatException o un array corto: no hay QR ahí.
            null
        } finally {
            qr.reset()
        }
    }
}
