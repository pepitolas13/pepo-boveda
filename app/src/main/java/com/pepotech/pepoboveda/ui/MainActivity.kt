package com.pepotech.pepoboveda.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pepotech.pepoboveda.data.EstadoBoveda
import com.pepotech.pepoboveda.ui.pantallas.PantallaAcercaDe
import com.pepotech.pepoboveda.ui.pantallas.PantallaAjustes
import com.pepotech.pepoboveda.ui.pantallas.PantallaAutenticador
import com.pepotech.pepoboveda.ui.pantallas.PantallaDesbloqueo
import com.pepotech.pepoboveda.ui.pantallas.PantallaEscaner
import com.pepotech.pepoboveda.ui.pantallas.PantallaDetalle
import com.pepotech.pepoboveda.ui.pantallas.PantallaEdicion
import com.pepotech.pepoboveda.ui.pantallas.PantallaGenerador
import com.pepotech.pepoboveda.ui.pantallas.PantallaLista
import com.pepotech.pepoboveda.ui.pantallas.PantallaOnboarding
import com.pepotech.pepoboveda.ui.pantallas.PantallaPasskeys
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.PepoBovedaTheme
import com.pepotech.pepoboveda.ui.theme.SuperficieAlta
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Biometria
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val vm: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarFlagSecure(vm.repositorio.ajustes.actual.modoGrabacion)
        lifecycleScope.launch {
            vm.ajustes.collectLatest { aplicarFlagSecure(it.modoGrabacion) }
        }
        setContent {
            PepoBovedaTheme {
                RaizPepoBoveda(vm, this)
            }
        }
    }

    private fun aplicarFlagSecure(modoGrabacion: Boolean) {
        if (modoGrabacion) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
fun RaizPepoBoveda(vm: VaultViewModel, actividad: FragmentActivity) {
    val pantalla by vm.pantalla.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val aviso by vm.aviso.collectAsStateWithLifecycle()
    val cuentaAtras by vm.cuentaAtrasPortapapeles.collectAsStateWithLifecycle()
    val anfitrion = remember { SnackbarHostState() }

    // Sin esto, atrás cerraba la app desde generador, passkeys o ajustes.
    // En lista, desbloqueo y onboarding no lo tocamos: ahí atrás sí sale de la app
    // (y desde el desbloqueo jamás debe entrar a la bóveda).
    val esRaiz = pantalla is Pantalla.Lista ||
        pantalla is Pantalla.Desbloqueo ||
        pantalla is Pantalla.Onboarding
    BackHandler(enabled = !esRaiz) {
        if (!vm.retroceder()) vm.volverALista()
    }

    LaunchedEffect(Unit) { vm.vigilarInactividad() }

    val ofrecerBiometria by vm.ofrecerBiometria.collectAsStateWithLifecycle()
    if (ofrecerBiometria && Biometria.disponible(actividad)) {
        DialogoOfrecerBiometria(vm, actividad)
    }

    LaunchedEffect(estado) {
        if (estado is EstadoBoveda.Bloqueada && pantalla !is Pantalla.Desbloqueo) {
            vm.ir(Pantalla.Desbloqueo)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            anfitrion.showSnackbar(it)
            vm.limpiarError()
        }
    }

    LaunchedEffect(aviso) {
        aviso?.let {
            anfitrion.showSnackbar(it)
            vm.limpiarAviso()
        }
    }

    Scaffold(
        containerColor = Obsidiana,
        snackbarHost = { SnackbarHost(anfitrion) }
    ) { relleno ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            vm.registrarInteraccion()
                        }
                    }
                }
        ) {
            AnimatedContent(
                targetState = pantalla,
                transitionSpec = {
                    val entrada = slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
                    ) { ancho -> ancho / 4 } + fadeIn(spring(dampingRatio = 0.6f))
                    val salida = slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
                    ) { ancho -> -ancho / 6 } + fadeOut(spring(dampingRatio = 0.6f))
                    entrada togetherWith salida
                },
                label = "navegacion"
            ) { destino ->
                when (destino) {
                    Pantalla.Onboarding -> PantallaOnboarding(vm, actividad)
                    Pantalla.Desbloqueo -> PantallaDesbloqueo(vm, actividad)
                    Pantalla.Lista -> PantallaLista(vm, estado)
                    is Pantalla.Detalle -> PantallaDetalle(vm, destino.id)
                    is Pantalla.Editar -> PantallaEdicion(vm, destino.id, destino.contrasenaInicial)
                    Pantalla.Generador -> PantallaGenerador(vm)
                    Pantalla.Passkeys -> PantallaPasskeys(vm)
                    Pantalla.Autenticador -> PantallaAutenticador(vm, estado)
                    is Pantalla.Escaner -> PantallaEscaner(vm, destino.entradaDestino)
                    Pantalla.Ajustes -> PantallaAjustes(vm, actividad)
                    Pantalla.AcercaDe -> PantallaAcercaDe(vm)
                }
            }

            if (cuentaAtras > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SuperficieAlta)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "Copiado · el portapapeles se borra en ${cuentaAtras}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (cuentaAtras <= 5) Ambar else TextoPrincipal
                    )
                }
            }
        }
    }
}

/**
 * Se ofrece una sola vez, justo al crear la bóveda. Si dice no, no vuelve a salir:
 * queda el interruptor de siempre en Ajustes.
 */
@Composable
private fun DialogoOfrecerBiometria(vm: VaultViewModel, actividad: FragmentActivity) {
    fun activar() {
        val clave = vm.repositorio.claveMaestraEnMemoria()
        if (clave == null) {
            vm.avisar("La bóveda está bloqueada")
            vm.cerrarOfertaBiometria()
            return
        }
        try {
            val cipher = vm.repositorio.biometria.cipherParaEnvolver()
            Biometria.autenticar(
                actividad = actividad,
                cipher = cipher,
                titulo = "Activar biometría",
                subtitulo = "Confirma para envolver tu clave maestra",
                alExito = { cifrador ->
                    try {
                        vm.repositorio.biometria.guardarEnvuelta(cifrador, clave)
                        vm.ajustarBiometria(true)
                        vm.avisar("Listo: la próxima vez entras con la huella")
                    } catch (e: Exception) {
                        vm.avisar("No se pudo envolver la clave")
                    }
                    vm.cerrarOfertaBiometria()
                },
                alFallar = {
                    vm.avisar("Biometría cancelada. Puedes activarla en Ajustes.")
                    vm.cerrarOfertaBiometria()
                }
            )
        } catch (e: Exception) {
            vm.avisar("No se pudo preparar la clave biométrica")
            vm.cerrarOfertaBiometria()
        }
    }

    AlertDialog(
        onDismissRequest = { vm.cerrarOfertaBiometria() },
        containerColor = SuperficieAlta,
        title = { Text("¿Abrir con tu huella?", color = TextoPrincipal) },
        text = {
            Text(
                "Tu contraseña maestra seguirá siendo la única llave: la huella solo la desenvuelve, " +
                    "guardada por el Keystore de Android y atada a este móvil. Si cambias la biometría del " +
                    "dispositivo, deja de valer y toca escribir la contraseña.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { activar() }) { Text("Activar", color = Ambar) }
        },
        dismissButton = {
            TextButton(onClick = { vm.cerrarOfertaBiometria() }) {
                Text("Ahora no", color = TextoSecundario)
            }
        }
    )
}
