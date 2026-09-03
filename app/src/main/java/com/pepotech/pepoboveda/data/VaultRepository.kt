package com.pepotech.pepoboveda.data

import android.content.Context
import android.util.Log
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import com.pepotech.pepoboveda.crypto.KdfParams
import com.pepotech.pepoboveda.crypto.VaultCrypto
import com.pepotech.pepoboveda.crypto.Zeroizar
import com.pepotech.pepoboveda.util.Diagnostico
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class VaultRepository private constructor(contexto: Context) {

    companion object {
        private const val TAG = "VaultRepository"

        @Volatile
        private var instancia: VaultRepository? = null

        fun obtener(contexto: Context): VaultRepository =
            instancia ?: synchronized(this) {
                instancia ?: try {
                    Log.w(TAG, "REPO crear instancia start")
                    VaultRepository(contexto.applicationContext).also {
                        Log.w(TAG, "REPO crear instancia end")
                        instancia = it
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "REPO crear instancia failed", e)
                    throw e
                }
            }
    }

    private val app = contexto.applicationContext

    val archivoBoveda = File(app.filesDir, "boveda.bvda")
    val biometria = BiometricKeyStore(app.filesDir)
    val ajustes = AlmacenAjustes(app)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val mutex = Mutex()
    private var claveMaestra: ByteArray? = null
    private var salt: ByteArray = ByteArray(VaultCrypto.TAM_SALT)
    private var params: KdfParams = KdfParams.PREDETERMINADOS
    private var contenido: ContenidoBoveda = ContenidoBoveda()

    private val _estado = MutableStateFlow<EstadoBoveda>(
        if (archivoBoveda.exists()) EstadoBoveda.Bloqueada else EstadoBoveda.SinCrear
    )
    val estado: StateFlow<EstadoBoveda> = _estado

    val existeBoveda: Boolean get() = archivoBoveda.exists()
    val estaDesbloqueada: Boolean get() = claveMaestra != null

    fun claveMaestraEnMemoria(): ByteArray? = claveMaestra

    fun saltActual(): ByteArray = salt.copyOf()

    // ---------------------------------------------------------------- creación

    @Synchronized
    fun crear(password: CharArray) {
        val nuevoSalt = VaultCrypto.nuevoSalt()
        val clave = VaultCrypto.derivarClave(password, nuevoSalt, KdfParams.PREDETERMINADOS)
        salt = nuevoSalt
        params = KdfParams.PREDETERMINADOS
        claveMaestra = clave
        contenido = ContenidoBoveda()
        persistir()
        publicar()
    }

    // ------------------------------------------------------------- desbloqueo

    @Synchronized
    fun desbloquear(password: CharArray) {
        val bytes = archivoBoveda.readBytes()
        val cabecera = VaultCrypto.leerCabecera(bytes)
        val clave = VaultCrypto.derivarClave(password, cabecera.salt, cabecera.params)
        val plano = try {
            VaultCrypto.descifrar(bytes, clave)
        } catch (e: Exception) {
            Zeroizar.borrar(clave)
            throw e
        }
        salt = cabecera.salt
        params = cabecera.params
        claveMaestra = clave
        contenido = json.decodeFromString(ContenidoBoveda.serializer(), String(plano, Charsets.UTF_8))
        Zeroizar.borrar(plano)
        publicar()
    }

    @Synchronized
    fun desbloquearConClaveMaestra(clave: ByteArray) {
        val bytes = archivoBoveda.readBytes()
        val cabecera = VaultCrypto.leerCabecera(bytes)
        val plano = VaultCrypto.descifrar(bytes, clave)
        salt = cabecera.salt
        params = cabecera.params
        claveMaestra = clave.copyOf()
        contenido = json.decodeFromString(ContenidoBoveda.serializer(), String(plano, Charsets.UTF_8))
        Zeroizar.borrar(plano)
        publicar()
    }

    @Synchronized
    fun bloquear() {
        contenido.entradas.forEach { it.limpiar() }
        Zeroizar.borrar(claveMaestra)
        claveMaestra = null
        contenido = ContenidoBoveda()
        _estado.value = if (archivoBoveda.exists()) EstadoBoveda.Bloqueada else EstadoBoveda.SinCrear
    }

    // ------------------------------------------------------------------- CRUD

    fun entradas(): List<Entrada> = contenido.entradas

    fun entrada(id: String): Entrada? = contenido.entradas.firstOrNull { it.id == id }

    fun nuevoId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun guardarEntrada(entrada: Entrada) {
        val ahora = System.currentTimeMillis()
        val existente = contenido.entradas.indexOfFirst { it.id == entrada.id }
        val lista = contenido.entradas.toMutableList()
        if (existente >= 0) {
            lista[existente] = entrada.copy(modificadaEn = ahora, creadaEn = lista[existente].creadaEn)
        } else {
            lista.add(entrada.copy(creadaEn = ahora, modificadaEn = ahora))
        }
        contenido = contenido.copy(entradas = lista)
        persistir()
        publicar()
    }

    @Synchronized
    fun eliminarEntrada(id: String) {
        val entrada = contenido.entradas.firstOrNull { it.id == id }
        if (entrada != null) {
            entrada.passkey?.limpiar()
        }
        contenido = contenido.copy(entradas = contenido.entradas.filterNot { it.id == id })
        persistir()
        publicar()
    }

    fun alternarFavorito(id: String) {
        val entrada = entrada(id) ?: return
        guardarEntrada(entrada.copy(favorito = !entrada.favorito))
    }

    fun passkeys(): List<Entrada> = contenido.entradas.filter { it.passkey != null }

    fun passkeysDe(rpId: String): List<Entrada> =
        passkeys().filter { it.passkey?.rpId.equals(rpId, ignoreCase = true) }

    // ------------------------------------------------------------ persistencia

    private fun persistir() {
        val clave = claveMaestra ?: throw IllegalStateException("La bóveda está bloqueada")
        val plano = json.encodeToString(ContenidoBoveda.serializer(), contenido).toByteArray(Charsets.UTF_8)
        val archivo = VaultCrypto.cifrar(plano, clave, salt, params)
        VaultCrypto.escribirAtomico(archivoBoveda, archivo)
        Zeroizar.borrar(plano)
    }

    private fun publicar() {
        _estado.value = EstadoBoveda.Desbloqueada(contenido.entradas)
    }

    // ------------------------------------------------ exportación e importación

    @Synchronized
    fun exportar(passwordExportacion: CharArray): ByteArray {
        if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
        val saltExport = VaultCrypto.nuevoSalt()
        val clave = VaultCrypto.derivarClave(passwordExportacion, saltExport, params)
        val plano = json.encodeToString(ContenidoBoveda.serializer(), contenido).toByteArray(Charsets.UTF_8)
        val salida = VaultCrypto.cifrar(plano, clave, saltExport, params)
        Zeroizar.borrar(plano)
        Zeroizar.borrar(clave)
        return salida
    }

    /** Devuelve el número de entradas importadas (fusiona por id). */
    @Synchronized
    fun importar(archivo: ByteArray, passwordExportacion: CharArray): Int {
        if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
        val cabecera = VaultCrypto.leerCabecera(archivo)
        val clave = VaultCrypto.derivarClave(passwordExportacion, cabecera.salt, cabecera.params)
        val plano = try {
            VaultCrypto.descifrar(archivo, clave)
        } finally {
            Zeroizar.borrar(clave)
        }
        // Nota de seguridad: String(plano, Charsets.UTF_8) crea un String inmutable
        // que no se puede zeroizar. El ByteArray `plano` se zeroiza en finally.
        // El String intermedio vive en el heap hasta que GC lo recoja.
        val importado = json.decodeFromString(ContenidoBoveda.serializer(), String(plano, Charsets.UTF_8))
        Zeroizar.borrar(plano)
        val porId = contenido.entradas.associateBy { it.id }.toMutableMap()
        var nuevas = 0
        importado.entradas.forEach { entrada ->
            val previa = porId[entrada.id]
            if (previa == null || entrada.modificadaEn > previa.modificadaEn) {
                porId[entrada.id] = entrada
                nuevas++
            }
        }
        contenido = contenido.copy(entradas = porId.values.sortedBy { it.titulo.lowercase() })
        persistir()
        publicar()
        return nuevas
    }

    @Synchronized
    fun cambiarContrasenaMaestra(nueva: CharArray) {
        if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
        val nuevoSalt = VaultCrypto.nuevoSalt()
        val claveNueva = VaultCrypto.derivarClave(nueva, nuevoSalt, KdfParams.PREDETERMINADOS)
        Zeroizar.borrar(claveMaestra)
        claveMaestra = claveNueva
        salt = nuevoSalt
        params = KdfParams.PREDETERMINADOS
        persistir()
        desactivarBiometria()
    }

    /** Borra las claves y blobs de ambos modos y apaga el ajuste. Único sitio que lo hace. */
    fun desactivarBiometria() {
        biometria.eliminarTodo()
        ajustes.actualizar { it.copy(biometriaActiva = false, biometriaModo = "") }
    }

    fun verificarContrasena(password: CharArray): Boolean = try {
        val bytes = archivoBoveda.readBytes()
        val cabecera = VaultCrypto.leerCabecera(bytes)
        val clave = VaultCrypto.derivarClave(password, cabecera.salt, cabecera.params)
        try {
            VaultCrypto.descifrar(bytes, clave)
            true
        } finally {
            Zeroizar.borrar(clave)
        }
    } catch (e: Exception) {
        false
    }

    @Synchronized
    fun borrarTodo() {
        contenido.entradas.forEach { it.limpiar() }
        bloquear()
        archivoBoveda.delete()
        desactivarBiometria()
        Diagnostico.borrar()
        _estado.value = EstadoBoveda.SinCrear
    }
}
