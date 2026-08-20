package com.pepotech.pepoboveda

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.pepotech.pepoboveda.data.VaultRepository

class PepoBovedaApp : Application(), Application.ActivityLifecycleCallbacks {

    private var actividadesVisibles = 0
    private var momentoAlFondo = 0L

    override fun onCreate() {
        super.onCreate()
        VaultRepository.obtener(this)
        registerActivityLifecycleCallbacks(this)
    }

    private val repositorio: VaultRepository get() = VaultRepository.obtener(this)

    override fun onActivityStarted(activity: Activity) {
        if (actividadesVisibles == 0 && momentoAlFondo > 0L) {
            val ajustes = repositorio.ajustes.actual
            val transcurrido = System.currentTimeMillis() - momentoAlFondo
            val limite = ajustes.autoBloqueoSegundos * 1000L
            if (ajustes.autoBloqueoSegundos == 0 || transcurrido >= limite) {
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
            if (repositorio.ajustes.actual.autoBloqueoSegundos == 0) {
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
