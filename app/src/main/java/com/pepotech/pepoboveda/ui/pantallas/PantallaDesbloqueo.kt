package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.PuertaBoveda
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Biometria
import com.pepotech.pepoboveda.util.Haptica

@Composable
fun PantallaDesbloqueo(vm: VaultViewModel, actividad: FragmentActivity) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }
    var contrasena by remember { mutableStateOf("") }
    var mostrar by remember { mutableStateOf(false) }
    var abriendo by remember { mutableStateOf(false) }
    var mensajeBiometria by remember { mutableStateOf<String?>(null) }
    var fallos by remember { mutableIntStateOf(0) }
    val sacudida = remember { Animatable(0f) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()

    val biometriaUsable = ajustes.biometriaActiva &&
        vm.repositorio.biometria.estaConfigurada &&
        Biometria.disponible(contexto)

    fun lanzarBiometria() {
        try {
            val cipher = vm.repositorio.biometria.cipherParaDesenvolver()
            Biometria.autenticar(
                actividad = actividad,
                cipher = cipher,
                titulo = "Abrir Pepo Bóveda",
                subtitulo = "Usa tu biometría para descifrar la clave maestra",
                alExito = { cifrador ->
                    try {
                        val clave = vm.repositorio.biometria.leerEnvuelta(cifrador)
                        abriendo = true
                        haptica.exito()
                        vm.desbloquearConClave(clave) { correcto -> if (!correcto) abriendo = false }
                    } catch (e: Exception) {
                        mensajeBiometria = "No se pudo descifrar la clave. Usa tu contraseña maestra."
                    }
                },
                alFallar = { texto -> mensajeBiometria = texto }
            )
        } catch (e: BiometricKeyStore.BiometriaInvalidadaException) {
            mensajeBiometria = "La biometría del dispositivo cambió. Entra con la contraseña maestra y vuelve a activarla."
            vm.ajustarBiometria(false)
        } catch (e: Exception) {
            mensajeBiometria = "La biometría no está disponible ahora mismo."
        }
    }

    LaunchedEffect(biometriaUsable) {
        if (biometriaUsable) lanzarBiometria()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PuertaBoveda(abierta = abriendo, tamano = 180)
        Spacer(Modifier.height(24.dp))
        Text("Bóveda cerrada", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Spacer(Modifier.height(6.dp))
        Text(
            "Todo sigue cifrado en este dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Column(modifier = Modifier.offset { IntOffset(sacudida.value.toInt(), 0) }) {
            CampoPepo(
                valor = contrasena,
                etiqueta = "Contraseña maestra",
                alCambiar = { contrasena = it },
                esContrasena = true,
                mostrarContrasena = mostrar,
                monoespaciada = true
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (mostrar) "Ocultar contraseña" else "Mostrar contraseña",
            color = Ambar,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable { mostrar = !mostrar }
        )
        Spacer(Modifier.height(20.dp))
        BotonAmbar("Abrir bóveda", activo = contrasena.isNotEmpty()) {
            vm.desbloquear(contrasena) { correcto ->
                if (correcto) {
                    abriendo = true
                    haptica.exito()
                } else {
                    haptica.error()
                    fallos++
                    contrasena = ""
                }
            }
        }
        if (biometriaUsable) {
            Spacer(Modifier.height(12.dp))
            BotonBorde("Usar biometría") { lanzarBiometria() }
        }
        mensajeBiometria?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }

    LaunchedEffect(fallos) {
        if (fallos > 0) {
            sacudida.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 260
                    0f at 0
                    -14f at 40
                    12f at 90
                    -8f at 140
                    5f at 190
                    0f at 260
                }
            )
        }
    }
}


