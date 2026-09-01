package com.pepotech.pepoboveda

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.util.Diagnostico
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class PepoBovedaApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        /** Tope de la excepción al bloqueo mientras un selector está abierto. */
        private const val MAX_SALIDA_PENDIENTE_MS = 2 * 60_000L

        /**
         * Vamos a abrir un selector del sistema (una imagen, un archivo) del que volveremos
         * con un resultado: esa salida no debe cerrar la bóveda aunque el ajuste sea "Al
         * cerrar la app", porque al volver con la imagen la bóveda estaría bloqueada y el
         * alta fallaría. Hay que emparejarla SIEMPRE con [salidaTerminada] en el callback
         * del resultado (y en el catch si el lanzamiento falla). Caduca sola a los dos
         * minutos por si algo se pierde; a partir de ahí el bloqueo vuelve a mandar.
         */
        fun salidaPendiente(contexto: Context) {
            (contexto.applicationContext as? PepoBovedaApp)?.salidaPendiente()
        }

        fun salidaTerminada(contexto: Context) {
            (contexto.applicationContext as? PepoBovedaApp)?.salidaTerminada()
        }
    }

    private var actividadesVisibles = 0
    private var momentoAlFondo = 0L

    private val salidasPendientes = AtomicInteger(0)

    @Volatile
    private var salidaCaducaEn = 0L

    override fun onCreate() {
        super.onCreate()
        Diagnostico.iniciar(File(filesDir, "diagnostico.log"))
        VaultRepository.obtener(this)
        registerActivityLifecycleCallbacks(this)
    }

    private val repositorio: VaultRepository get() = VaultRepository.obtener(this)

    fun salidaPendiente() {
        salidasPendientes.incrementAndGet()
        salidaCaducaEn = System.currentTimeMillis() + MAX_SALIDA_PENDIENTE_MS
    }

    fun salidaTerminada() {
        if (salidasPendientes.decrementAndGet() <= 0) {
            salidasPendientes.set(0)
            salidaCaducaEn = 0L
        }
    }

    private val salidaPermitida: Boolean
        get() {
            if (salidasPendientes.get() <= 0) return false
            if (System.currentTimeMillis() < salidaCaducaEn) return true
            // Caducó: alguien no llamó a salidaTerminada. Se limpia y el bloqueo manda.
            salidasPendientes.set(0)
            salidaCaducaEn = 0L
            return false
        }

    override fun onActivityStarted(activity: Activity) {
        if (actividadesVisibles == 0 && momentoAlFondo > 0L) {
            val ajustes = repositorio.ajustes.actual
            val transcurrido = System.currentTimeMillis() - momentoAlFondo
            val limite = ajustes.autoBloqueoSegundos * 1000L
            val porCierre = ajustes.autoBloqueoSegundos == 0 && !salidaPermitida
            val porTiempo = ajustes.autoBloqueoSegundos > 0 && transcurrido >= limite
            if (porCierre || porTiempo) {
                repositorio.bloquear()
            }
        }
        actividadesVisibles++
    }

    override fun onActivityStopped(activity: Activity) {
        actividadesVisibles--
        if (actividadesVisibles <= 0) {
            actividadesVisibles = 0
            momentoAlFondo = System.currentTimeMillis()
            if (repositorio.ajustes.actual.autoBloqueoSegundos == 0 && !salidaPermitida) {
                repositorio.bloquear()
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
