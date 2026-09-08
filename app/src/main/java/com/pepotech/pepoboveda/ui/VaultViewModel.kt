package com.pepotech.pepoboveda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pepotech.pepoboveda.R
import com.pepotech.pepoboveda.crypto.OtpAuth
import com.pepotech.pepoboveda.crypto.Zeroizar
import com.pepotech.pepoboveda.data.AjustesApp
import com.pepotech.pepoboveda.data.Entrada
import com.pepotech.pepoboveda.data.EstadoBoveda
import com.pepotech.pepoboveda.data.FrenoIntentos
import com.pepotech.pepoboveda.data.TipoEntrada
import com.pepotech.pepoboveda.data.VaultRepository
import com.pepotech.pepoboveda.util.Diagnostico
import com.pepotech.pepoboveda.util.Portapapeles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface Pantalla {
    @Serializable data object Onboarding : Pantalla
    @Serializable data object Desbloqueo : Pantalla
    @Serializable data object Lista : Pantalla
    @Serializable data class Detalle(val id: String) : Pantalla
    @Serializable data class Editar(val id: String?, val contrasenaInicial: String = "") : Pantalla
    @Serializable data object Generador : Pantalla
    @Serializable data object Passkeys : Pantalla
    @Serializable data object Autenticador : Pantalla
    /** Si [entradaDestino] es null, el QR crea una entrada nueva de 2FA. */
    @Serializable
    data class Escaner(
        val entradaDestino: String? = null,
        /** true = entrar directo a escribir la clave a mano, sin cámara. */
        val soloManual: Boolean = false
    ) : Pantalla
    @Serializable data object Ajustes : Pantalla
    @Serializable data object AcercaDe : Pantalla
}

class VaultViewModel(app: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    val repositorio = VaultRepository.obtener(app)

    val estado: StateFlow<EstadoBoveda> = repositorio.estado
    val ajustes: StateFlow<AjustesApp> = repositorio.ajustes.ajustes

    private val json = Json { ignoreUnknownKeys = true }

    private val _pantalla = MutableStateFlow<Pantalla>(
        savedStateHandle.get<String>("pantalla")?.let {
            try { json.decodeFromString<Pantalla>(it) } catch (e: Exception) { null }
        } ?: if (repositorio.existeBoveda) Pantalla.Desbloqueo else Pantalla.Onboarding
    )
    val pantalla: StateFlow<Pantalla> = _pantalla

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _aviso = MutableStateFlow<String?>(null)
    val aviso: StateFlow<String?> = _aviso

    private val _cuentaAtrasPortapapeles = MutableStateFlow(0)
    val cuentaAtrasPortapapeles: StateFlow<Int> = _cuentaAtrasPortapapeles

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda

    private val _filtroTipo = MutableStateFlow<TipoEntrada?>(null)
    val filtroTipo: StateFlow<TipoEntrada?> = _filtroTipo

    private val _soloFavoritos = MutableStateFlow(false)
    val soloFavoritos: StateFlow<Boolean> = _soloFavoritos

    private var trabajoPortapapeles: Job? = null

    // ------------------------------------------------------------- navegación

    /** Pila de navegación: sin esto, el botón atrás cerraba la app. */
    private val pila: ArrayDeque<Pantalla> = savedStateHandle.get<String>("pila")?.let {
        try { ArrayDeque(json.decodeFromString<List<Pantalla>>(it)) } catch (e: Exception) { ArrayDeque() }
    } ?: ArrayDeque()

    private fun persistirNavegacion() {
        savedStateHandle["pantalla"] = json.encodeToString(_pantalla.value)
        savedStateHandle["pila"] = json.encodeToString(pila.toList())
    }

    fun ir(pantalla: Pantalla) {
        if (pantalla != _pantalla.value) {
            pila.addLast(_pantalla.value)
            if (pila.size > 20) pila.removeFirst()
        }
        _pantalla.value = pantalla
        persistirNavegacion()
    }

    /** Cambia de pantalla vaciando la pila: se usa al abrir, cerrar y bloquear. */
    private fun irRaiz(pantalla: Pantalla) {
        pila.clear()
        _pantalla.value = pantalla
        persistirNavegacion()
    }

    fun volverALista() {
        irRaiz(Pantalla.Lista)
    }

    /**
     * Devuelve true si consumió el gesto. Si devuelve false, que salga la app:
     * ya estamos en la lista, en el desbloqueo o en el onboarding.
     */
    fun retroceder(): Boolean {
        val anterior = pila.removeLastOrNull() ?: return false
        _pantalla.value = anterior
        persistirNavegacion()
        return true
    }

    // ------------------------------------------- freno a los intentos de clave

    // El contador vive en disco (ver FrenoIntentos): antes eran dos campos de aquí y
    // cerrar la app desde recientes lo reseteaba, que es justo lo que haría alguien
    // probando claves a mano.
    private val contextoApp: Application get() = getApplication()

    /** Segundos que faltan para poder volver a probar. 0 si se puede probar ya. */
    fun esperaPorIntentos(): Long = FrenoIntentos.esperaSegundos(contextoApp)

    // suspend y en IO: FrenoIntentos escribe con commit(), que es sincrono a
    // proposito, y estas dos se llaman desde dentro de ejecutar{}, que corre en
    // el hilo principal. Sin esto seria escritura a disco en el hilo de la UI.
    private suspend fun apuntarFallo() = withContext(Dispatchers.IO) {
        FrenoIntentos.apuntarFallo(contextoApp)
    }

    private suspend fun limpiarFallos() = withContext(Dispatchers.IO) {
        FrenoIntentos.limpiar(contextoApp)
    }

    // ------------------------------------- bloqueo por inactividad en pantalla

    private val _ultimaInteraccion = MutableStateFlow(System.currentTimeMillis())

    fun registrarInteraccion() {
        _ultimaInteraccion.value = System.currentTimeMillis()
    }

    private var vigilante: Job? = null

    /**
     * Cierra la bóveda si el móvil se queda abierto encima de la mesa.
     * Un solo vigilante: si la actividad se recrea, no se apilan más.
     */
    fun vigilarInactividad() {
        if (vigilante?.isActive == true) return
        vigilante = viewModelScope.launch {
            var estabaDesbloqueada = repositorio.estaDesbloqueada
            while (true) {
                delay(5_000)
                val limite = repositorio.ajustes.actual.autoBloqueoSegundos
                val desbloqueada = repositorio.estaDesbloqueada
                // Al abrirse la bóveda el contador empieza de cero, venga del camino que venga
                // (contraseña, huella o autofill). Sin esto, el tiempo que pasó bloqueada
                // contaba como inactividad y la volvía a cerrar en el siguiente tic.
                if (desbloqueada && !estabaDesbloqueada) registrarInteraccion()
                estabaDesbloqueada = desbloqueada
                // Mientras la oferta de huella está en pantalla el reloj no corre: es la primera
                // vez que el usuario ve la app y estará leyendo, no ignorándola.
                if (_ofrecerBiometria.value) registrarInteraccion()
                if (limite > 0 && desbloqueada && !_ofrecerBiometria.value) {
                    val quieto = System.currentTimeMillis() - _ultimaInteraccion.value
                    if (quieto >= limite * 1000L) {
                        bloquear()
                        _aviso.value = contextoApp.getString(R.string.aviso_inactividad)
                    }
                }
            }
        }
    }

    fun limpiarError() {
        _error.value = null
    }

    fun limpiarAviso() {
        _aviso.value = null
    }

    fun avisar(texto: String) {
        _aviso.value = texto
    }

    // ----------------------------------------------------------- bóveda: ciclo

    fun crearBoveda(password: String, alTerminar: () -> Unit = {}) {
        ejecutar {
            val chars = password.toCharArray()
            try {
                withContext(Dispatchers.Default) { repositorio.crear(chars) }
                registrarInteraccion()
                irRaiz(Pantalla.Lista)
                // Mucha gente no llega nunca a Ajustes: se lo ofrecemos aquí, una vez.
                _ofrecerBiometria.value = true
                alTerminar()
            } finally {
                Zeroizar.borrar(chars)
            }
        }
    }

    fun desbloquear(password: String, alTerminar: (Boolean) -> Unit = {}) {
        val espera = esperaPorIntentos()
        if (espera > 0) {
            _error.value = contextoApp.getString(R.string.error_demasiados_intentos, espera)
            alTerminar(false)
            return
        }
        ejecutar {
            val chars = password.toCharArray()
            try {
                withContext(Dispatchers.Default) { repositorio.desbloquear(chars) }
                limpiarFallos()
                registrarInteraccion()
                irRaiz(Pantalla.Lista)
                alTerminar(true)
            } catch (e: Exception) {
                apuntarFallo()
                _error.value = contextoApp.getString(R.string.error_contrasena_incorrecta)
                alTerminar(false)
            } finally {
                Zeroizar.borrar(chars)
            }
        }
    }

    fun desbloquearConClave(clave: ByteArray, alTerminar: (Boolean) -> Unit = {}) {
        ejecutar {
            try {
                withContext(Dispatchers.Default) { repositorio.desbloquearConClaveMaestra(clave) }
                limpiarFallos()
                registrarInteraccion()
                irRaiz(Pantalla.Lista)
                alTerminar(true)
            } catch (e: Exception) {
                Diagnostico.apuntar("huella", "La clave desenvuelta no abrió la bóveda: ${e.javaClass.simpleName}")
                _error.value = contextoApp.getString(R.string.error_abrir_huella)
                alTerminar(false)
            } finally {
                Zeroizar.borrar(clave)
            }
        }
    }

    fun bloquear() {
        repositorio.bloquear()
        // El rato que pase bloqueada no cuenta como inactividad.
        registrarInteraccion()
        irRaiz(if (repositorio.existeBoveda) Pantalla.Desbloqueo else Pantalla.Onboarding)
    }

    // ---------------------------------------------------------------- entradas

    fun entradasVisibles(entradas: List<Entrada>): List<Entrada> {
        val texto = _busqueda.value.trim().lowercase()
        return entradas
            .filter { entrada ->
                (_filtroTipo.value == null || entrada.tipo == _filtroTipo.value) &&
                    (!_soloFavoritos.value || entrada.favorito) &&
                    (texto.isEmpty() ||
                        entrada.titulo.lowercase().contains(texto) ||
                        entrada.usuario.lowercase().contains(texto) ||
                        entrada.urls.any { it.lowercase().contains(texto) })
            }
            .sortedWith(compareByDescending<Entrada> { it.favorito }.thenBy { it.titulo.lowercase() })
    }

    fun buscar(texto: String) {
        _busqueda.value = texto
    }

    fun filtrarPorTipo(tipo: TipoEntrada?) {
        _filtroTipo.value = tipo
    }

    fun alternarSoloFavoritos() {
        _soloFavoritos.value = !_soloFavoritos.value
    }

    fun entrada(id: String): Entrada? = repositorio.entrada(id)

    fun guardar(entrada: Entrada) {
        ejecutar {
            withContext(Dispatchers.IO) { repositorio.guardarEntrada(entrada) }
            _aviso.value = contextoApp.getString(R.string.aviso_guardado)
        }
    }

    fun eliminar(id: String) {
        ejecutar {
            withContext(Dispatchers.IO) { repositorio.eliminarEntrada(id) }
            irRaiz(Pantalla.Lista)
            _aviso.value = contextoApp.getString(R.string.aviso_eliminado)
        }
    }

    fun alternarFavorito(id: String) {
        ejecutar { withContext(Dispatchers.IO) { repositorio.alternarFavorito(id) } }
    }

    fun nuevoId(): String = repositorio.nuevoId()

    // -------------------------------------------------------------- 2FA (TOTP)

    fun entradasConTotp(entradas: List<Entrada>): List<Entrada> =
        entradas.filter { !it.secretoTotp.isNullOrBlank() }
            .sortedBy { it.titulo.lowercase() }

    /**
     * Alta de un doble factor a partir de un QR o de un código escrito a mano.
     * Devuelve false si el texto no sirve, para que la pantalla lo diga sin salir.
     */
    fun altaTotp(texto: String, entradaDestino: String? = null): Boolean {
        val semilla = OtpAuth.leer(texto) ?: return false
        val destino = entradaDestino?.let { repositorio.entrada(it) }
        if (destino != null) {
            guardar(
                destino.copy(
                    secretoTotp = semilla.secreto,
                    totpEmisor = semilla.emisor.ifBlank { destino.totpEmisor },
                    totpDigitos = semilla.digitos,
                    totpPeriodo = semilla.periodo
                )
            )
            ir(Pantalla.Detalle(destino.id))
            return true
        }
        val entrada = Entrada(
            id = repositorio.nuevoId(),
            tipo = TipoEntrada.LOGIN,
            titulo = semilla.titulo,
            usuario = semilla.cuenta,
            secretoTotp = semilla.secreto,
            totpEmisor = semilla.emisor,
            totpDigitos = semilla.digitos,
            totpPeriodo = semilla.periodo
        )
        guardar(entrada)
        ir(Pantalla.Autenticador)
        return true
    }

    // ------------------------------------------------------------ portapapeles

    fun copiar(etiqueta: String, valor: String, sensible: Boolean) {
        val contexto = getApplication<Application>()
        Portapapeles.copiarSensible(contexto, etiqueta, valor)
        trabajoPortapapeles?.cancel()
        if (!sensible) {
            _aviso.value = "$etiqueta copiado"
            return
        }
        val segundos = repositorio.ajustes.actual.portapapelesSegundos
        trabajoPortapapeles = viewModelScope.launch {
            for (restante in segundos downTo 1) {
                _cuentaAtrasPortapapeles.value = restante
                delay(1_000)
            }
            _cuentaAtrasPortapapeles.value = 0
            Portapapeles.limpiarSiCoincide(contexto, valor)
        }
    }

    // --------------------------------------------------------------- ajustes

    fun ajustarAutoBloqueo(segundos: Int) = repositorio.ajustes.actualizar { it.copy(autoBloqueoSegundos = segundos) }

    fun ajustarPortapapeles(segundos: Int) = repositorio.ajustes.actualizar { it.copy(portapapelesSegundos = segundos) }

    fun ajustarModoGrabacion(activo: Boolean) = repositorio.ajustes.actualizar { it.copy(modoGrabacion = activo) }

    fun ajustarMotorCamara(clave: String) = repositorio.ajustes.actualizar { it.copy(motorCamara = clave) }

    /** Se pone a true justo al crear la bóveda, para ofrecer la huella sin pasar por Ajustes. */
    private val _ofrecerBiometria = MutableStateFlow(false)
    val ofrecerBiometria: StateFlow<Boolean> = _ofrecerBiometria

    fun cerrarOfertaBiometria() {
        _ofrecerBiometria.value = false
        // Encadenamos con la otra cosa que hay que hacer una sola vez: ponerme
        // como gestor de contraseñas del sistema. Si no, nadie lo encuentra.
        _ofrecerGestor.value = true
    }

    /** Oferta, una sola vez, de activar Pepo Bóveda como gestor del sistema. */
    private val _ofrecerGestor = MutableStateFlow(false)
    val ofrecerGestor: StateFlow<Boolean> = _ofrecerGestor

    fun cerrarOfertaGestor() {
        _ofrecerGestor.value = false
    }

    // ------------------------------------------------------- exportar/importar

    fun exportar(password: String, escritor: (ByteArray) -> Unit) {
        ejecutar {
            val chars = password.toCharArray()
            try {
                val datos = withContext(Dispatchers.Default) { repositorio.exportar(chars) }
                withContext(Dispatchers.IO) { escritor(datos) }
                _aviso.value = contextoApp.getString(R.string.aviso_exportado)
            } catch (e: Exception) {
                _error.value = contextoApp.getString(R.string.error_exportar, e.message ?: contextoApp.getString(R.string.error_generico))
            } finally {
                Zeroizar.borrar(chars)
            }
        }
    }

    fun importar(password: String, lector: () -> ByteArray) {
        ejecutar {
            val chars = password.toCharArray()
            try {
                val datos = withContext(Dispatchers.IO) { lector() }
                val nuevas = withContext(Dispatchers.Default) { repositorio.importar(datos, chars) }
                _aviso.value = contextoApp.getString(R.string.aviso_importadas, nuevas)
            } catch (e: Exception) {
                _error.value = contextoApp.getString(R.string.error_importar)
            } finally {
                Zeroizar.borrar(chars)
            }
        }
    }

    fun cambiarContrasenaMaestra(actual: String, nueva: String) {
        ejecutar {
            val viejaChars = actual.toCharArray()
            val nuevaChars = nueva.toCharArray()
            try {
                val correcta = withContext(Dispatchers.Default) { repositorio.verificarContrasena(viejaChars) }
                if (!correcta) {
                    _error.value = contextoApp.getString(R.string.error_contrasena_actual_incorrecta)
                    return@ejecutar
                }
                withContext(Dispatchers.Default) { repositorio.cambiarContrasenaMaestra(nuevaChars) }
                _aviso.value = contextoApp.getString(R.string.aviso_contrasena_cambiada)
            } finally {
                Zeroizar.borrar(viejaChars)
                Zeroizar.borrar(nuevaChars)
            }
        }
    }

    private fun ejecutar(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            _trabajando.value = true
            try {
                bloque()
            } catch (e: Exception) {
                // Solo la clase: el mensaje de estas excepciones puede llevar rutas o contenido de la bóveda.
                Diagnostico.apuntar("app", "Operación fallida: ${e.javaClass.simpleName}")
                _error.value = e.message ?: contextoApp.getString(R.string.error_generico)
            } finally {
                _trabajando.value = false
            }
        }
    }
}
