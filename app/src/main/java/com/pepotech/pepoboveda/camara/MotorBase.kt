package com.pepotech.pepoboveda.camara

import android.os.Handler
import android.os.Looper
import com.pepotech.pepoboveda.util.Diagnostico
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lo que comparten los dos motores de cámara: el vigilante del primer frame, el "fallar
 * una sola vez" y el orden de parada. Está aquí para que el relevo CameraX → compatible
 * se comporte igual venga de donde venga; si se toca el tiempo de espera o el orden de
 * liberación, se toca para los dos.
 *
 * [alEstado] siempre se llama en el hilo principal.
 */
abstract class MotorBase(
    protected val nombre: String,
    private val alEstado: (EstadoCamara) -> Unit
) {

    companion object {
        const val ESPERA_PRIMER_FRAME_MS = 4_000L
    }

    protected val principal = Handler(Looper.getMainLooper())
    protected val terminado = AtomicBoolean(false)
    private val primerFrame = AtomicBoolean(false)
    protected var inicio = 0L

    private val vigilante = Runnable {
        if (!primerFrame.get()) fallar("La cámara se abre pero no entrega imagen", null)
    }

    protected fun empezarACronometrar() {
        inicio = System.currentTimeMillis()
        Diagnostico.apuntar("camara", "$nombre: iniciando")
    }

    /** Desde que la cámara "está abierta": si en unos segundos no llega un frame, se da por rota. */
    protected fun armarVigilante() {
        principal.removeCallbacks(vigilante)
        principal.postDelayed(vigilante, ESPERA_PRIMER_FRAME_MS)
    }

    /** Se llama con cada frame; solo el primero cuenta. Devuelve true si era el primero. */
    protected fun anotarFrame(descripcion: () -> String, conImagen: Boolean): Boolean {
        if (!primerFrame.compareAndSet(false, true)) return false
        val ms = System.currentTimeMillis() - inicio
        Diagnostico.apuntar("camara", "$nombre: primer frame ${descripcion()} a los $ms ms")
        principal.post {
            principal.removeCallbacks(vigilante)
            if (!terminado.get()) alEstado(EstadoCamara.Funcionando(conImagen))
        }
        return true
    }

    protected val yaHayFrames: Boolean get() = primerFrame.get()

    /** Falla una sola vez: libera la cámara, espera a que esté cerrada de verdad y avisa. */
    protected fun fallar(motivo: String, e: Throwable?) {
        if (!terminado.compareAndSet(false, true)) return
        val detalle = e?.let { Diagnostico.describir(it) } ?: motivo
        Diagnostico.apuntar("camara", "$nombre: $motivo", e)
        principal.post {
            liberar()
            esperarCierre { alEstado(EstadoCamara.Fallo(motivo, detalle)) }
        }
    }

    /** Suelta la cámara para que otro motor (o otra app) pueda abrirla. Hilo principal. */
    fun detener() {
        if (terminado.compareAndSet(false, true)) {
            principal.removeCallbacks(vigilante)
            liberar()
        }
    }

    /** Libera recursos. Se llama una sola vez, en el hilo principal. */
    protected abstract fun liberar()

    /**
     * Llama a [alCerrada] cuando la cámara está cerrada del todo. Por defecto, ya; CameraX
     * lo redefine porque cierra el dispositivo en su propio hilo y el siguiente motor no
     * puede abrirlo hasta entonces.
     */
    protected open fun esperarCierre(alCerrada: () -> Unit) = alCerrada()
}
