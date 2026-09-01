package com.pepotech.pepoboveda.ui

import androidx.fragment.app.FragmentActivity
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import com.pepotech.pepoboveda.crypto.BiometricKeyStore.FalloKeystore
import com.pepotech.pepoboveda.crypto.BiometricKeyStore.Modo
import com.pepotech.pepoboveda.crypto.Zeroizar
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.data.modoBiometriaActivo
import com.pepotech.pepoboveda.util.Biometria
import com.pepotech.pepoboveda.util.Diagnostico
import javax.crypto.Cipher

/**
 * La única implementación de "activar la huella" y "abrir con la huella". La usan el
 * desbloqueo, Ajustes, la bienvenida, el autofill y las passkeys, así todos tratan igual
 * los fallos y ninguno se queda ofreciendo un botón muerto cuando la clave se invalida.
 */
class FlujoBiometria(
    private val actividad: FragmentActivity,
    private val repositorio: VaultRepository
) {

    private val almacen: BiometricKeyStore get() = repositorio.biometria

    sealed class ResultadoActivacion {
        data class Activada(val modo: Modo) : ResultadoActivacion()
        object Cancelada : ResultadoActivacion()

        /** El modo fuerte no funciona en este móvil: Android dio la huella por buena y el Keystore la rechazó. */
        data class FuerteRota(val detalle: String) : ResultadoActivacion()
        data class Error(val texto: String) : ResultadoActivacion()
    }

    sealed class FalloDesbloqueo {
        object Cancelado : FalloDesbloqueo()
        object Invalidada : FalloDesbloqueo()
        data class KeystoreRoto(val detalle: String) : FalloDesbloqueo()
        data class Prompt(val fallo: Biometria.Fallo) : FalloDesbloqueo()
        data class Otro(val mensaje: String) : FalloDesbloqueo()

        /** Texto para el usuario, o null si no hay nada que decir. */
        val texto: String?
            get() = when (this) {
                Cancelado -> null
                Invalidada -> "La biometría del dispositivo cambió. Entra con la contraseña maestra y vuelve a activarla en Ajustes."
                is KeystoreRoto -> "Tu Android dio la huella por buena pero su Keystore la rechazó. Entra con la contraseña; en Ajustes > Seguridad puedes activar el modo compatible."
                is Prompt -> fallo.texto
                is Otro -> mensaje
            }

        /** Tras estos fallos la disponibilidad ha podido cambiar: la pantalla debe volver a preguntar. */
        val cambiaDisponibilidad: Boolean get() = this is Invalidada || this is KeystoreRoto
    }

    companion object {
        /** Qué modo toca en un móvil con esta capacidad, o null si ninguno. Único sitio que lo decide. */
        fun modoRecomendado(c: Biometria.Capacidad): Modo? = when (Biometria.decidirNivel(c)) {
            Biometria.Nivel.FUERTE -> Modo.FUERTE
            Biometria.Nivel.COMPATIBLE -> Modo.COMPATIBLE
            Biometria.Nivel.NINGUNO -> null
        }

        fun nivelDe(modo: Modo): Biometria.Nivel =
            if (modo == Modo.FUERTE) Biometria.Nivel.FUERTE else Biometria.Nivel.COMPATIBLE

        /** ¿Ofrece el móvil lo que este modo necesita ahora mismo? */
        fun capaz(c: Biometria.Capacidad, modo: Modo): Boolean =
            if (modo == Modo.FUERTE) Biometria.hayFuerte(c) else Biometria.hayCompatible(c)
    }

    /** Modo guardado en Ajustes, o null si la huella no está activada. */
    val modoActivo: Modo? get() = repositorio.ajustes.actual.modoBiometriaActivo

    fun capacidadActual(): Biometria.Capacidad = Biometria.capacidad(actividad)

    /** ¿Se puede ofrecer el botón de huella ahora mismo? Se pregunta cada vez, nunca se cachea. */
    fun disponible(capacidad: Biometria.Capacidad = capacidadActual()): Boolean {
        val modo = modoActivo ?: return false
        if (!almacen.estaConfigurada(modo)) return false
        return capaz(capacidad, modo)
    }

    fun etiquetaBoton(): String = if (modoActivo == Modo.COMPATIBLE) "Usar huella o PIN" else "Usar la huella"

    /** Activa el modo pedido envolviendo la clave maestra que hay en memoria. */
    fun activar(modo: Modo, alResultado: (ResultadoActivacion) -> Unit) {
        val viva = repositorio.claveMaestraEnMemoria()
        if (viva == null) {
            alResultado(ResultadoActivacion.Error("Desbloquea la bóveda antes de activar la huella"))
            return
        }
        // Copia propia: el auto-bloqueo pone a cero el array vivo, y con el prompt en
        // pantalla puede pasar. Sin la copia envolveríamos treinta y dos ceros.
        val clave = viva.copyOf()

        fun terminar(resultado: ResultadoActivacion) {
            Zeroizar.borrar(clave)
            alResultado(resultado)
        }

        fun deshacer() {
            almacen.eliminar(modo)
            if (modoActivo == modo) repositorio.desactivarBiometria()
        }

        val cipher: Cipher? = if (modo == Modo.FUERTE) {
            try {
                almacen.cipherParaEnvolver()
            } catch (e: Exception) {
                Diagnostico.apuntar("huella", "No se pudo crear la clave fuerte en el Keystore", e)
                deshacer()
                terminar(ResultadoActivacion.FuerteRota(Diagnostico.describir(e)))
                return
            }
        } else {
            null
        }

        Biometria.autenticar(
            actividad = actividad,
            nivel = nivelDe(modo),
            cipher = cipher,
            titulo = if (modo == Modo.FUERTE) "Activar huella" else "Activar huella (modo compatible)",
            subtitulo = if (modo == Modo.FUERTE) "Confirma para envolver tu clave maestra" else "Confirma con tu huella o con el PIN del móvil",
            alExito = { autenticado ->
                if (!repositorio.estaDesbloqueada) {
                    // Se bloqueó mientras el prompt estaba en pantalla: la copia ya no es de fiar.
                    deshacer()
                    terminar(ResultadoActivacion.Error("La bóveda se bloqueó mientras esperaba la huella. Ábrela y vuelve a intentarlo."))
                    return@autenticar
                }
                try {
                    almacen.envolver(modo, clave, autenticado)
                    almacen.eliminar(modo.otro)
                    repositorio.ajustes.actualizar { it.copy(biometriaActiva = true, biometriaModo = modo.clave) }
                    Diagnostico.apuntar("huella", "Modo ${modo.clave} activado")
                    terminar(ResultadoActivacion.Activada(modo))
                } catch (e: Exception) {
                    val fallo = BiometricKeyStore.clasificar(e)
                    Diagnostico.apuntar("huella", "Android dio la huella por buena pero el Keystore rechazó envolver en modo ${modo.clave} ($fallo)", e)
                    deshacer()
                    terminar(
                        if (modo == Modo.FUERTE) ResultadoActivacion.FuerteRota(Diagnostico.describir(e))
                        else ResultadoActivacion.Error("El Keystore de este móvil no pudo guardar la clave (${Diagnostico.describir(e)}).")
                    )
                }
            },
            alFallo = { fallo ->
                if (modo == Modo.FUERTE) deshacer()
                terminar(
                    if (fallo == Biometria.Fallo.Cancelado) ResultadoActivacion.Cancelada
                    else ResultadoActivacion.Error(fallo.texto ?: "La huella no ha funcionado.")
                )
            }
        )
    }

    fun desactivar() {
        repositorio.desactivarBiometria()
        Diagnostico.apuntar("huella", "Huella desactivada")
    }

    /**
     * Pide la huella (o el PIN, en modo compatible) y entrega la clave maestra desenvuelta.
     * Quien la recibe debe borrarla al terminar.
     */
    fun desbloquear(
        titulo: String,
        subtitulo: String,
        alClave: (ByteArray) -> Unit,
        alFallo: (FalloDesbloqueo) -> Unit,
        alIntentoFallido: () -> Unit = {}
    ) {
        val modo = modoActivo
        if (modo == null) {
            alFallo(FalloDesbloqueo.Otro("La huella no está activada."))
            return
        }
        val cipher: Cipher? = if (modo == Modo.FUERTE) {
            try {
                almacen.cipherParaDesenvolver()
            } catch (e: Exception) {
                alFallo(falloDeKeystore(e, modo, "No se pudo preparar la clave"))
                return
            }
        } else {
            null
        }
        Biometria.autenticar(
            actividad = actividad,
            nivel = nivelDe(modo),
            cipher = cipher,
            titulo = titulo,
            subtitulo = subtitulo,
            alExito = { autenticado ->
                val clave = try {
                    almacen.desenvolver(modo, autenticado)
                } catch (e: Exception) {
                    alFallo(falloDeKeystore(e, modo, "Android dio la huella por buena pero el Keystore rechazó desenvolver"))
                    return@autenticar
                }
                alClave(clave)
            },
            alFallo = { fallo ->
                alFallo(if (fallo == Biometria.Fallo.Cancelado) FalloDesbloqueo.Cancelado else FalloDesbloqueo.Prompt(fallo))
            },
            alIntentoFallido = alIntentoFallido
        )
    }

    /** Clasifica un fallo del Keystore, lo apunta y, si la clave ya no vale, desactiva la huella. */
    private fun falloDeKeystore(e: Exception, modo: Modo, contexto: String): FalloDesbloqueo {
        val fallo = BiometricKeyStore.clasificar(e)
        Diagnostico.apuntar("huella", "$contexto (modo ${modo.clave}, $fallo)", e)
        return when (fallo) {
            FalloKeystore.Invalidada -> {
                desactivar()
                FalloDesbloqueo.Invalidada
            }
            FalloKeystore.NoAutenticada -> FalloDesbloqueo.KeystoreRoto("KEY_USER_NOT_AUTHENTICATED")
            is FalloKeystore.Otro ->
                if (modo == Modo.FUERTE) FalloDesbloqueo.KeystoreRoto(fallo.detalle)
                else FalloDesbloqueo.Otro("No se pudo recuperar la clave (${fallo.detalle}). Usa la contraseña.")
        }
    }

    /** Confirmación de presencia con la bóveda ya abierta (firma de una passkey). */
    fun confirmar(titulo: String, subtitulo: String, alExito: () -> Unit, alFallo: (Biometria.Fallo) -> Unit) {
        Biometria.confirmar(actividad, titulo, subtitulo, alExito, alFallo)
    }
}
