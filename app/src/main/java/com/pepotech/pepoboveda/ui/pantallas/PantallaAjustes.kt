package com.pepotech.pepoboveda.ui.pantallas

import androidx.compose.ui.res.stringResource
import com.pepotech.pepoboveda.R
import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepotech.pepoboveda.PepoBovedaApp
import com.pepotech.pepoboveda.camara.MotorCamara
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import com.pepotech.pepoboveda.data.AlmacenAjustes
import com.pepotech.pepoboveda.data.modoBiometriaActivo
import com.pepotech.pepoboveda.ui.FlujoBiometria
import com.pepotech.pepoboveda.ui.Pantalla
import com.pepotech.pepoboveda.ui.VaultViewModel
import com.pepotech.pepoboveda.ui.componentes.BotonAmbar
import com.pepotech.pepoboveda.ui.componentes.BotonBorde
import com.pepotech.pepoboveda.ui.componentes.CampoPepo
import com.pepotech.pepoboveda.ui.componentes.EtiquetaSeccion
import com.pepotech.pepoboveda.ui.componentes.TarjetaPepo
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.DegradadoAmbar
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.Superficie
import com.pepotech.pepoboveda.ui.theme.SuperficieAlta
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.AjustesSistema
import com.pepotech.pepoboveda.util.Biometria
import com.pepotech.pepoboveda.util.Haptica

@Composable
fun PantallaAjustes(vm: VaultViewModel, actividad: FragmentActivity) {
    val contexto = LocalContext.current
    val haptica = remember { Haptica(contexto) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()

    var passwordExportacion by remember { mutableStateOf("") }
    var dialogoExportar by remember { mutableStateOf(false) }
    var dialogoImportar by remember { mutableStateOf(false) }
    var dialogoCambio by remember { mutableStateOf(false) }
    var dialogoBorrar by remember { mutableStateOf(false) }
    var actualMaestra by remember { mutableStateOf("") }
    var nuevaMaestra by remember { mutableStateOf("") }
    var uriPendiente by remember { mutableStateOf<Uri?>(null) }

    val lanzadorCrear = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        PepoBovedaApp.salidaTerminada(contexto)
        if (uri != null) {
            val clave = passwordExportacion
            passwordExportacion = ""
            vm.exportar(clave) { datos ->
                contexto.contentResolver.openOutputStream(uri)?.use { it.write(datos) }
            }
        }
    }

    val lanzadorAbrir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        PepoBovedaApp.salidaTerminada(contexto)
        if (uri != null) {
            uriPendiente = uri
            dialogoImportar = true
        }
    }

    // --------------------------------------------------------------- huella
    val flujo = remember { FlujoBiometria(actividad, vm.repositorio) }
    // La capacidad se vuelve a preguntar al volver a esta pantalla: si el usuario acaba
    // de registrar una huella en Android, aquí tiene que aparecer sin reiniciar nada.
    var capacidad by remember { mutableStateOf(Biometria.capacidad(contexto)) }
    LifecycleResumeEffect(Unit) {
        capacidad = Biometria.capacidad(contexto)
        onPauseOrDispose { }
    }
    val nivel = Biometria.decidirNivel(capacidad)
    val modoActivo = ajustes.modoBiometriaActivo
    var dialogoCompatible by remember { mutableStateOf<String?>(null) }

    fun tratarActivacion(resultado: FlujoBiometria.ResultadoActivacion) {
        when (resultado) {
            is FlujoBiometria.ResultadoActivacion.Activada -> {
                haptica.exito()
                vm.avisar(
                    if (resultado.modo == BiometricKeyStore.Modo.FUERTE) actividad.getString(R.string.aviso_huella_fuerte_ok)
                    else actividad.getString(R.string.aviso_huella_compatible_ok)
                )
            }
            FlujoBiometria.ResultadoActivacion.Cancelada -> vm.avisar(actividad.getString(R.string.aviso_huella_cancelada))
            is FlujoBiometria.ResultadoActivacion.FuerteRota -> {
                haptica.error()
                dialogoCompatible = actividad.getString(R.string.aviso_huella_fuerte_fallida)
            }
            is FlujoBiometria.ResultadoActivacion.Error -> {
                haptica.error()
                vm.avisar(resultado.texto)
            }
        }
    }

    // La comprobación de "bóveda abierta" la hace FlujoBiometria.activar; aquí no se repite.
    fun activarFuerte() = flujo.activar(BiometricKeyStore.Modo.FUERTE, ::tratarActivacion)

    fun ofrecerCompatible(motivo: String) {
        dialogoCompatible = motivo
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.titulo_ajustes), style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Spacer(Modifier.height(18.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_seguridad))
            Spacer(Modifier.height(10.dp))
            FilaAjuste(
                titulo = stringResource(R.string.ajuste_huella_titulo),
                descripcion = when {
                    modoActivo != null -> "Activa en modo ${modoActivo.etiqueta}."
                    nivel == Biometria.Nivel.FUERTE ->
                        "La clave maestra se guarda envuelta por el Keystore, atada a tu huella de Clase 3."
                    nivel == Biometria.Nivel.COMPATIBLE ->
                        "${Biometria.explicarFaltaDeFuerte(capacidad)} Hay un modo compatible: Android comprueba la huella o el PIN y la app abre la bóveda."
                    else ->
                        "Sin huella ni PIN utilizables ahora mismo: ${Biometria.explicar(capacidad.compatible)}."
                },
                activo = ajustes.biometriaActiva,
                habilitado = nivel != Biometria.Nivel.NINGUNO || ajustes.biometriaActiva,
                alCambiar = { activar ->
                    if (activar) {
                        when (nivel) {
                            Biometria.Nivel.FUERTE -> activarFuerte()
                            Biometria.Nivel.COMPATIBLE -> ofrecerCompatible(Biometria.explicarFaltaDeFuerte(capacidad))
                            Biometria.Nivel.NINGUNO -> vm.avisar(actividad.getString(R.string.error_sin_biometria))
                        }
                    } else {
                        flujo.desactivar()
                        haptica.tic()
                        vm.avisar(actividad.getString(R.string.aviso_huella_desactivada))
                    }
                }
            )
            if (capacidad.fuerte == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED &&
                capacidad.debil != BiometricManager.BIOMETRIC_SUCCESS
            ) {
                Spacer(Modifier.height(10.dp))
                BotonBorde(stringResource(R.string.accion_registrar_huella)) {
                    if (!AjustesSistema.abrirRegistroHuella(contexto)) vm.avisar(actividad.getString(R.string.error_pantalla_no_encontrada))
                }
            }
            when {
                ajustes.biometriaActiva && modoActivo == BiometricKeyStore.Modo.FUERTE && Biometria.hayCompatible(capacidad) ->
                    EnlaceAjuste(stringResource(R.string.accion_cambiar_compatible)) {
                        ofrecerCompatible("Si la huella te falla en este móvil aunque Android la acepte, el modo compatible suele funcionar.")
                    }
                ajustes.biometriaActiva && modoActivo == BiometricKeyStore.Modo.COMPATIBLE && Biometria.hayFuerte(capacidad) ->
                    EnlaceAjuste(stringResource(R.string.accion_volver_fuerte)) { activarFuerte() }
                !ajustes.biometriaActiva && nivel == Biometria.Nivel.FUERTE && Biometria.hayCompatible(capacidad) ->
                    EnlaceAjuste(stringResource(R.string.accion_activar_compatible)) {
                        ofrecerCompatible("Para quien ya sabe que la huella de Clase 3 le falla en este móvil.")
                    }
            }
            Spacer(Modifier.height(14.dp))
            EtiquetaSeccion(stringResource(R.string.seccion_bloqueo_auto))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AlmacenAjustes.OPCIONES_AUTO_BLOQUEO.forEach { (segundos, etiqueta) ->
                    ChipOpcion(etiqueta, ajustes.autoBloqueoSegundos == segundos) {
                        haptica.tic()
                        vm.ajustarAutoBloqueo(segundos)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            EtiquetaSeccion(stringResource(R.string.seccion_borrado_portapapeles))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AlmacenAjustes.OPCIONES_PORTAPAPELES.forEach { (segundos, etiqueta) ->
                    ChipOpcion(etiqueta, ajustes.portapapelesSegundos == segundos) {
                        haptica.tic()
                        vm.ajustarPortapapeles(segundos)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_camara))
            Spacer(Modifier.height(8.dp))
            Text(
                "Automático prueba CameraX y, si falla, pasa solo al motor compatible. Si la imagen sale negra o no lee nada, fuerza el compatible: usa la API antigua de cámara, que funciona hasta en los móviles más raros. Y si nada va, siempre puedes leer el QR desde una captura.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MotorCamara.entries.forEach { motor ->
                    ChipOpcion(motor.etiqueta, ajustes.motorCamara == motor.clave) {
                        haptica.tic()
                        vm.ajustarMotorCamara(motor.clave)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_backup))
            Spacer(Modifier.height(8.dp))
            Text(
                "El archivo exportado va cifrado con su propia contraseña y con Argon2id. Sin esa contraseña es ruido.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            BotonBorde(stringResource(R.string.accion_exportar)) { dialogoExportar = true }
            Spacer(Modifier.height(10.dp))
            BotonBorde(stringResource(R.string.accion_importar)) {
                PepoBovedaApp.salidaPendiente(contexto)
                try {
                    lanzadorAbrir.launch(arrayOf("*/*"))
                } catch (e: Exception) {
                    PepoBovedaApp.salidaTerminada(contexto)
                    vm.avisar(contexto.getString(R.string.error_sin_selector_archivos))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_maestra))
            Spacer(Modifier.height(10.dp))
            BotonBorde(stringResource(R.string.accion_cambiar_maestra)) { dialogoCambio = true }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.label_passkeys))
            Spacer(Modifier.height(10.dp))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Text(
                    "Tu Android admite passkeys. Activa Pepo Bóveda como proveedor de credenciales en los ajustes del sistema y gestiónalas desde la sección Passkeys.",
                    color = TextoPrincipal,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                BotonBorde(stringResource(R.string.accion_ver_passkeys)) { vm.ir(Pantalla.Passkeys) }
            } else {
                Text(
                    "Esta sección está oculta porque tu Android es anterior al 14. La API que permite a una app ser proveedora de passkeys del sistema (CredentialProviderService) llegó en Android 14; sin ella nadie puede ofrecerte passkeys de verdad, así que preferimos no fingirlo. Todo lo demás funciona igual.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion("Transparencia")
            Spacer(Modifier.height(10.dp))
            BotonBorde(stringResource(R.string.accion_auditar)) { vm.ir(Pantalla.AcercaDe) }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion(stringResource(R.string.seccion_peligro))
            Spacer(Modifier.height(10.dp))
            BotonBorde(stringResource(R.string.accion_borrar_boveda), color = Peligro) { dialogoBorrar = true }
        }

        Spacer(Modifier.height(20.dp))
        BotonBorde(stringResource(R.string.accion_volver)) { vm.volverALista() }
        Spacer(Modifier.height(40.dp))
    }

    dialogoCompatible?.let { motivo ->
        AlertDialog(
            onDismissRequest = { dialogoCompatible = null },
            containerColor = SuperficieAlta,
            title = { Text(stringResource(R.string.titulo_compatible), color = TextoPrincipal) },
            text = {
                Text(
                    motivo + "\n\nEn este modo la huella o el PIN los comprueba Android y la app abre la bóveda. " +
                        "La clave maestra sigue envuelta por el Keystore y no sale del móvil, pero no queda atada " +
                        "al chip como en el modo fuerte: es algo más débil. Tu contraseña maestra sigue siendo la " +
                        "única llave real, y puedes volver al modo fuerte cuando quieras.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogoCompatible = null
                    flujo.activar(BiometricKeyStore.Modo.COMPATIBLE, ::tratarActivacion)
                }) { Text(stringResource(R.string.accion_confirmar_compatible), color = Ambar) }
            },
            dismissButton = {
                TextButton(onClick = { dialogoCompatible = null }) { Text(stringResource(R.string.accion_ahora_no), color = TextoSecundario) }
            }
        )
    }

    if (dialogoExportar) {
        DialogoContrasena(
            titulo = stringResource(R.string.titulo_pass_copia),
            descripcion = "Elige una contraseña solo para este archivo. Apúntala donde toque: sin ella la copia no se abre.",
            textoBoton = stringResource(R.string.accion_exportar_btn),
            alConfirmar = { clave ->
                passwordExportacion = clave
                dialogoExportar = false
                PepoBovedaApp.salidaPendiente(contexto)
                try {
                    lanzadorCrear.launch("pepo-boveda-${System.currentTimeMillis()}.bvda")
                } catch (e: Exception) {
                    PepoBovedaApp.salidaTerminada(contexto)
                    passwordExportacion = ""
                    vm.avisar(actividad.getString(R.string.error_sin_selector_archivos))
                }
            },
            alCancelar = { dialogoExportar = false }
        )
    }

    if (dialogoImportar) {
        DialogoContrasena(
            titulo = stringResource(R.string.titulo_pass_copia),
            descripcion = "Escribe la contraseña con la que cifraste ese archivo.",
            textoBoton = stringResource(R.string.accion_importar_btn),
            alConfirmar = { clave ->
                val uri = uriPendiente
                dialogoImportar = false
                uriPendiente = null
                if (uri != null) {
                    vm.importar(clave) {
                        contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("No se pudo leer el archivo")
                    }
                }
            },
            alCancelar = { dialogoImportar = false; uriPendiente = null }
        )
    }

    if (dialogoCambio) {
        AlertDialog(
            onDismissRequest = { dialogoCambio = false },
            title = { Text(stringResource(R.string.accion_cambiar_maestra)) },
            text = {
                Column {
                    CampoPepo(valor = actualMaestra, etiqueta = stringResource(R.string.label_pass_actual), alCambiar = { actualMaestra = it }, esContrasena = true)
                    Spacer(Modifier.height(10.dp))
                    CampoPepo(valor = nuevaMaestra, etiqueta = stringResource(R.string.label_pass_nueva), alCambiar = { nuevaMaestra = it }, esContrasena = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Se vuelve a cifrar toda la bóveda y se desactiva la huella.",
                        color = TextoSecundario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nuevaMaestra.length >= 10 && actualMaestra.isNotEmpty(),
                    onClick = {
                        dialogoCambio = false
                        vm.cambiarContrasenaMaestra(actualMaestra, nuevaMaestra)
                        actualMaestra = ""
                        nuevaMaestra = ""
                    }
                ) { Text(stringResource(R.string.accion_cambiar), color = Ambar) }
            },
            dismissButton = { TextButton(onClick = { dialogoCambio = false }) { Text(stringResource(R.string.accion_cancelar)) } }
        )
    }

    if (dialogoBorrar) {
        AlertDialog(
            onDismissRequest = { dialogoBorrar = false },
            title = { Text(stringResource(R.string.titulo_borrar_boveda)) },
            text = { Text("Se elimina el archivo cifrado y la clave de la huella. Si no tienes copia, no hay vuelta atrás.") },
            confirmButton = {
                TextButton(onClick = {
                    dialogoBorrar = false
                    vm.repositorio.borrarTodo()
                    vm.ir(Pantalla.Onboarding)
                }) { Text(stringResource(R.string.accion_borrar_todo), color = Peligro) }
            },
            dismissButton = { TextButton(onClick = { dialogoBorrar = false }) { Text(stringResource(R.string.accion_cancelar)) } }
        )
    }
}

@Composable
private fun EnlaceAjuste(texto: String, alPulsar: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(
        texto,
        color = Ambar,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clickable { alPulsar() }
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun DialogoContrasena(
    titulo: String,
    descripcion: String,
    textoBoton: String,
    alConfirmar: (String) -> Unit,
    alCancelar: () -> Unit
) {
    var valor by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = {
            Column {
                Text(descripcion, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                CampoPepo(valor = valor, etiqueta = stringResource(R.string.label_contrasena), alCambiar = { valor = it }, esContrasena = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valor.length >= 8, onClick = { alConfirmar(valor) }) {
                Text(textoBoton, color = Ambar)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(R.string.accion_cancelar)) } }
    )
}

@Composable
private fun FilaAjuste(
    titulo: String,
    descripcion: String,
    activo: Boolean,
    habilitado: Boolean = true,
    alCambiar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, color = TextoPrincipal, style = MaterialTheme.typography.titleMedium)
            Text(descripcion, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = activo,
            enabled = habilitado,
            onCheckedChange = alCambiar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidiana,
                checkedTrackColor = Ambar,
                uncheckedTrackColor = Borde
            )
        )
    }
}

@Composable
private fun ChipOpcion(texto: String, activo: Boolean, alPulsar: () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(forma)
            .background(if (activo) DegradadoAmbar else Brush.horizontalGradient(listOf(Superficie, Superficie)))
            .clickable { alPulsar() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(texto, color = if (activo) Obsidiana else TextoSecundario, style = MaterialTheme.typography.bodyMedium)
    }
}
