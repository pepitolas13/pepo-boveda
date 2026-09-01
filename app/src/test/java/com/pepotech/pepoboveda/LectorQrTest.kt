package com.pepotech.pepoboveda

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.pepotech.pepoboveda.camara.LectorQr
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class LectorQrTest {

    private val carga = "otpauth://totp/PepoTech:daniel@pepotech.es?secret=JBSWY3DPEHPK3PXP&issuer=PepoTech&digits=6&period=30"
    private val lado = 320

    private fun matriz(): BitMatrix =
        QRCodeWriter().encode(carga, BarcodeFormat.QR_CODE, lado, lado, mapOf(EncodeHintType.MARGIN to 4))

    /** Luminancia: negro = 0, blanco = 255. */
    private fun luminancia(m: BitMatrix): ByteArray {
        val salida = ByteArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            salida[y * m.width + x] = if (m.get(x, y)) 0 else 255.toByte()
        }
        return salida
    }

    @Test
    fun `lee un QR desde luminancia compacta`() {
        val m = matriz()
        assertEquals(carga, LectorQr.decodificarLuminancia(luminancia(m), m.width, m.height))
    }

    @Test
    fun `lee un QR claro sobre oscuro (modo oscuro)`() {
        val m = matriz()
        val invertida = luminancia(m).map { (255 - (it.toInt() and 0xFF)).toByte() }.toByteArray()
        assertEquals(carga, LectorQr.decodificarLuminancia(invertida, m.width, m.height))
    }

    @Test
    fun `copiarPlanoY quita el relleno del rowStride`() {
        val m = matriz()
        val ancho = m.width
        val alto = m.height
        val plano = luminancia(m)
        val rowStride = ancho + 32
        // Buffer con relleno basura al final de cada fila, como entregan algunos HAL.
        // La última fila no lleva relleno: así lo hacen los HAL reales y así se detecta
        // el desbordamiento si alguien lee rowStride*alto bytes.
        val conRelleno = ByteArray(rowStride * (alto - 1) + ancho) { 0x7F }
        for (fila in 0 until alto) {
            System.arraycopy(plano, fila * ancho, conRelleno, fila * rowStride, ancho)
        }
        val copiado = LectorQr.copiarPlanoY(ByteBuffer.wrap(conRelleno), rowStride, 1, ancho, alto)
        assertArrayEquals(plano, copiado)
        assertEquals(carga, LectorQr.decodificarLuminancia(copiado, ancho, alto))
    }

    @Test
    fun `copiarPlanoY respeta un pixelStride mayor que uno`() {
        val ancho = 4
        val alto = 2
        val rowStride = 10
        val esperado = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val datos = ByteArray(rowStride * alto)
        var valor = 1
        for (fila in 0 until alto) for (col in 0 until ancho) {
            datos[fila * rowStride + col * 2] = (valor++).toByte()
        }
        assertArrayEquals(esperado, LectorQr.copiarPlanoY(ByteBuffer.wrap(datos), rowStride, 2, ancho, alto))
    }

    @Test
    fun `lee un QR desde pixeles ARGB`() {
        val m = matriz()
        val pixeles = IntArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            pixeles[y * m.width + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        assertEquals(carga, LectorQr.decodificarPixeles(pixeles, m.width, m.height))
    }

    @Test
    fun `sin QR devuelve null en vez de lanzar`() {
        val gris = ByteArray(200 * 200) { 0x80.toByte() }
        assertNull(LectorQr.decodificarLuminancia(gris, 200, 200))
        assertNull(LectorQr.decodificarLuminancia(ByteArray(10), 200, 200))
        assertNull(LectorQr.decodificarPixeles(IntArray(0), 0, 0))
    }

    @Test
    fun `detecta los QR de llave de acceso`() {
        assertTrue(LectorQr.esQrDePasskey("FIDO:/0987654321"))
        assertTrue(LectorQr.esQrDePasskey("  fido:algo"))
        assertFalse(LectorQr.esQrDePasskey(carga))
    }
}
