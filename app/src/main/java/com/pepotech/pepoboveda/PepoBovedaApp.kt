package com.pepotech.pepoboveda

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.util.Diagnostico
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class PepoBovedaApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "PepoBovedaApp"
        private const val MAX_SALIDA_PENDIENTE_MS = 2 * 60_000L
        private const val HASH_FIRMA_ESPERADO = ""

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
    @Volatile private var salidaCaducaEn = 0L
    private var integridadVerificada = false

    override fun onCreate() {
        super.onCreate()
        Diagnostico.iniciar(File(filesDir, "diagnostico.log"))
        VaultRepository.obtener(this)
        registerActivityLifecycleCallbacks(this)
        verificarIntegridad()
        
        if (com.pepotech.pepoboveda.util.SeguridadApp.estaComprometido(this)) {
            Diagnostico.apuntar("seguridad", "Entorno no seguro detectado")
        }
    }

    private val repositorio: VaultRepository get() = VaultRepository.obtener(this)

    private fun verificarIntegridad() {
        if (integridadVerificada) return
        try {
            val paquete = packageName
            val info = packageManager.getPackageInfo(
                paquete,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            )
            val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (sig != null) {
                val hash = MessageDigest.getInstance("SHA-256").digest(sig)
                val hashB64 = android.util.Base64.encodeToString(hash, android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
                if (hashB64 != HASH_FIRMA_ESPERADO && !esDebug()) {
                    Diagnostico.apuntar("seguridad", "Firma de APT modificada")
                }
            }
        } catch (e: Exception) {
            Diagnostico.apuntar("seguridad", "No se pudo verificar integridad: ${e.javaClass.simpleName}")
        } finally {
            integridadVerificada = true
        }
    }

    private fun esDebug(): Boolean = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

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
