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
import androidx.compose.ui.res.stringResource
import com.pepotech.pepoboveda.R
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
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
import com.pepotech.pepoboveda.util.AjustesSistema
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
    // Se pregunta cuando toca ofrecerla, no al arrancar la app: así cuenta una huella
    // registrada hace un minuto, y un sensor ocupado en el arranque no la esconde para siempre.
    val modoOfrecido = remember(ofrecerBiometria) {
        if (ofrecerBiometria) FlujoBiometria.modoRecomendado(Biometria.capacidad(actividad)) else null
    }
    if (ofrecerBiometria && modoOfrecido != null) {
        DialogoOfrecerBiometria(vm, actividad, modoOfrecido)
    }
    // Sin huella ni PIN utilizables no hay nada que ofrecer: pasamos directo al
    // siguiente paso en vez de dejar la oferta colgada para siempre.
    LaunchedEffect(ofrecerBiometria, modoOfrecido) {
        if (ofrecerBiometria && modoOfrecido == null) vm.cerrarOfertaBiometria()
    }

    val ofrecerGestor by vm.ofrecerGestor.collectAsStateWithLifecycle()
    if (ofrecerGestor) {
        DialogoOfrecerGestor(vm, actividad)
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
                    is Pantalla.Escaner -> PantallaEscaner(vm, actividad, destino.entradaDestino, destino.soloManual)
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
                        text = stringResource(R.string.portapapeles_borrado_en, cuentaAtras),
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
private fun DialogoOfrecerBiometria(vm: VaultViewModel, actividad: FragmentActivity, modo: BiometricKeyStore.Modo) {
    val flujo = remember { FlujoBiometria(actividad, vm.repositorio) }
    val compatible = modo == BiometricKeyStore.Modo.COMPATIBLE

    fun activar() {
        flujo.activar(modo) { resultado ->
            when (resultado) {
                is FlujoBiometria.ResultadoActivacion.Activada ->
                    vm.avisar(actividad.getString(R.string.aviso_huella_activada))
                FlujoBiometria.ResultadoActivacion.Cancelada ->
                    vm.avisar(actividad.getString(R.string.aviso_huella_cancelada))
                is FlujoBiometria.ResultadoActivacion.FuerteRota ->
                    vm.avisar(actividad.getString(R.string.aviso_huella_fuerte_fallida))
                is FlujoBiometria.ResultadoActivacion.Error ->
                    vm.avisar(resultado.texto)
            }
            vm.cerrarOfertaBiometria()
        }
    }

    AlertDialog(
        onDismissRequest = { vm.cerrarOfertaBiometria() },
        containerColor = SuperficieAlta,
        title = { Text(stringResource(if (compatible) R.string.dialogo_huella_titulo_compatible else R.string.dialogo_huella_titulo_fuerte), color = TextoPrincipal) },
        text = {
            Text(
                stringResource(if (compatible) R.string.dialogo_huella_texto_compatible else R.string.dialogo_huella_texto_fuerte),
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { activar() }) { Text(stringResource(R.string.accion_activar), color = Ambar) }
        },
        dismissButton = {
            TextButton(onClick = { vm.cerrarOfertaBiometria() }) {
                Text(stringResource(R.string.accion_ahora_no), color = TextoSecundario)
            }
        }
    )
}

/**
 * Segunda oferta de bienvenida: activarme como gestor del sistema. Sin esto no
 * salgo al rellenar contraseñas ni al crear una llave de acceso, y nadie
 * encuentra solo el ajuste.
 */
@Composable
private fun DialogoOfrecerGestor(vm: VaultViewModel, actividad: FragmentActivity) {
    AlertDialog(
        onDismissRequest = { vm.cerrarOfertaGestor() },
        containerColor = SuperficieAlta,
        title = { Text(stringResource(R.string.dialogo_gestor_titulo), color = TextoPrincipal) },
        text = {
            Text(
                stringResource(R.string.dialogo_gestor_texto),
                color = TextoSecundario
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (!AjustesSistema.abrirProveedorCredenciales(actividad)) {
                    vm.avisar(actividad.getString(R.string.error_pantalla_no_encontrada))
                }
                vm.cerrarOfertaGestor()
            }) { Text(stringResource(R.string.accion_abrir_ajustes), color = Ambar) }
        },
        dismissButton = {
            TextButton(onClick = { vm.cerrarOfertaGestor() }) {
                Text(stringResource(R.string.accion_ahora_no), color = TextoSecundario)
            }
        }
    )
}
