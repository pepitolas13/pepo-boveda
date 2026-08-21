package com.pepotech.pepoboveda.ui.pantallas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pepotech.pepoboveda.data.AlmacenAjustes
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
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
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
        if (uri != null) {
            uriPendiente = uri
            dialogoImportar = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = TextoPrincipal)
        Spacer(Modifier.height(18.dp))

        TarjetaPepo {
            EtiquetaSeccion("Seguridad")
            Spacer(Modifier.height(10.dp))
            FilaAjuste(
                titulo = "Abrir con biometría",
                descripcion = if (Biometria.disponible(contexto)) {
                    "La clave maestra se guarda envuelta por el Keystore, atada a tu huella."
                } else {
                    "Este dispositivo no tiene biometría fuerte disponible."
                },
                activo = ajustes.biometriaActiva,
                habilitado = Biometria.disponible(contexto),
                alCambiar = { activar ->
                    if (activar) {
                        val clave = vm.repositorio.claveMaestraEnMemoria()
                        if (clave == null) {
                            vm.avisar("Desbloquea la bóveda antes de activar la biometría")
                        } else {
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
                                            haptica.exito()
                                            vm.avisar("Biometría activada")
                                        } catch (e: Exception) {
                                            vm.avisar("No se pudo envolver la clave")
                                        }
                                    },
                                    alFallar = { vm.avisar("Biometría cancelada") }
                                )
                            } catch (e: Exception) {
                                vm.avisar("No se pudo preparar la clave biométrica")
                            }
                        }
                    } else {
                        vm.repositorio.biometria.eliminar()
                        vm.ajustarBiometria(false)
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
            EtiquetaSeccion("Bloqueo automático")
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
            EtiquetaSeccion("Borrado del portapapeles")
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
            EtiquetaSeccion("Copia de seguridad")
            Spacer(Modifier.height(8.dp))
            Text(
                "El archivo exportado va cifrado con su propia contraseña y con Argon2id. Sin esa contraseña es ruido.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            BotonBorde("Exportar bóveda cifrada") { dialogoExportar = true }
            Spacer(Modifier.height(10.dp))
            BotonBorde("Importar copia") { lanzadorAbrir.launch(arrayOf("*/*")) }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion("Contraseña maestra")
            Spacer(Modifier.height(10.dp))
            BotonBorde("Cambiar contraseña maestra") { dialogoCambio = true }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion("Passkeys")
            Spacer(Modifier.height(10.dp))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Text(
                    "Tu Android admite passkeys. Activa Pepo Bóveda como proveedor de credenciales en los ajustes del sistema y gestiónalas desde la sección Passkeys.",
                    color = TextoPrincipal,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                BotonBorde("Ver mis passkeys") { vm.ir(Pantalla.Passkeys) }
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
            BotonBorde("Audítame") { vm.ir(Pantalla.AcercaDe) }
        }

        Spacer(Modifier.height(16.dp))

        TarjetaPepo {
            EtiquetaSeccion("Zona peligrosa")
            Spacer(Modifier.height(10.dp))
            BotonBorde("Borrar la bóveda de este dispositivo", color = Peligro) { dialogoBorrar = true }
        }

        Spacer(Modifier.height(20.dp))
        BotonBorde("Volver") { vm.volverALista() }
        Spacer(Modifier.height(40.dp))
    }

    if (dialogoExportar) {
        DialogoContrasena(
            titulo = "Contraseña de la copia",
            descripcion = "Elige una contraseña solo para este archivo. Apúntala donde toque: sin ella la copia no se abre.",
            textoBoton = "Exportar",
            alConfirmar = { clave ->
                passwordExportacion = clave
                dialogoExportar = false
                lanzadorCrear.launch("pepo-boveda-${System.currentTimeMillis()}.bvda")
            },
            alCancelar = { dialogoExportar = false }
        )
    }

    if (dialogoImportar) {
        DialogoContrasena(
            titulo = "Contraseña de la copia",
            descripcion = "Escribe la contraseña con la que cifraste ese archivo.",
            textoBoton = "Importar",
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
            title = { Text("Cambiar contraseña maestra") },
            text = {
                Column {
                    CampoPepo(valor = actualMaestra, etiqueta = "Contraseña actual", alCambiar = { actualMaestra = it }, esContrasena = true)
                    Spacer(Modifier.height(10.dp))
                    CampoPepo(valor = nuevaMaestra, etiqueta = "Nueva contraseña", alCambiar = { nuevaMaestra = it }, esContrasena = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Se vuelve a cifrar toda la bóveda y se desactiva la biometría.",
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
                ) { Text("Cambiar", color = Ambar) }
            },
            dismissButton = { TextButton(onClick = { dialogoCambio = false }) { Text("Cancelar") } }
        )
    }

    if (dialogoBorrar) {
        AlertDialog(
            onDismissRequest = { dialogoBorrar = false },
            title = { Text("¿Borrar la bóveda entera?") },
            text = { Text("Se elimina el archivo cifrado y la clave biométrica. Si no tienes copia, no hay vuelta atrás.") },
            confirmButton = {
                TextButton(onClick = {
                    dialogoBorrar = false
                    vm.repositorio.borrarTodo()
                    vm.ir(Pantalla.Onboarding)
                }) { Text("Borrar todo", color = Peligro) }
            },
            dismissButton = { TextButton(onClick = { dialogoBorrar = false }) { Text("Cancelar") } }
        )
    }
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
                CampoPepo(valor = valor, etiqueta = "Contraseña", alCambiar = { valor = it }, esContrasena = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valor.length >= 8, onClick = { alConfirmar(valor) }) {
                Text(textoBoton, color = Ambar)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
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

