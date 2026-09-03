package com.pepotech.pepoboveda.data

import android.content.Context
import com.pepotech.pepoboveda.crypto.BiometricKeyStore
import com.pepotech.pepoboveda.crypto.KdfParams
import com.pepotech.pepoboveda.crypto.VaultCrypto
import com.pepotech.pepoboveda.crypto.Zeroizar
import com.pepotech.pepoboveda.util.Diagnostico
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Una sola bóveda para todo el proceso.
 *
 * Sobre hilos: esto lo tocan tres sitios a la vez. La pantalla va por
 * Dispatchers.IO y Dispatchers.Default, el servicio de autorrelleno guarda desde
 * onSaveRequest, y PasskeyCreateActivity guarda desde el hilo principal. Sin
 * candado, dos guardados a la vez pueden perder uno o dejar el .bvda a medias.
 *
 * El candado va solo sobre el cambio de estado y la escritura del archivo. Derivar
 * la clave con Argon2 tarda más de un segundo y se hace SIEMPRE fuera: si entrase
 * dentro, importar una copia en segundo plano congelaría el guardado de una passkey
 * en el hilo principal el tiempo que dure el Argon2, que es un ANR con todas las
 * letras. Los campos son @Volatile para que las lecturas sueltas (entradas(),
 * estaDesbloqueada) vean el último valor sin pedir el candado.
 */
class VaultRepository private constructor(contexto: Context) {

    private val app = contexto.applicationContext

    val archivoBoveda = File(app.filesDir, "boveda.bvda")
    val biometria = BiometricKeyStore(app.filesDir)
    val ajustes = AlmacenAjustes(app)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Protege claveMaestra, salt, params, contenido y la escritura del archivo. */
    private val candado = Any()

    @Volatile private var claveMaestra: ByteArray? = null
    @Volatile private var salt: ByteArray = ByteArray(VaultCrypto.TAM_SALT)
    @Volatile private var params: KdfParams = KdfParams.PREDETERMINADOS
    @Volatile private var contenido: ContenidoBoveda = ContenidoBoveda()

    private val _estado = MutableStateFlow<EstadoBoveda>(
        if (archivoBoveda.exists()) EstadoBoveda.Bloqueada else EstadoBoveda.SinCrear
    )
    val estado: StateFlow<EstadoBoveda> = _estado

    val existeBoveda: Boolean get() = archivoBoveda.exists()
    val estaDesbloqueada: Boolean get() = claveMaestra != null

    fun claveMaestraEnMemoria(): ByteArray? = claveMaestra

    fun saltActual(): ByteArray = salt.copyOf()

    // ---------------------------------------------------------------- creación

    fun crear(password: CharArray) {
        // Argon2 fuera del candado.
        val nuevoSalt = VaultCrypto.nuevoSalt()
        val clave = VaultCrypto.derivarClave(password, nuevoSalt, KdfParams.PREDETERMINADOS)
        synchronized(candado) {
            if (claveMaestra != null) {
                // Ya hay una bóveda abierta (doble toque en crear, o una carrera con
                // el desbloqueo). Crear encima la vaciaría entera.
                Zeroizar.borrar(clave)
                return
            }
            salt = nuevoSalt
            params = KdfParams.PREDETERMINADOS
            claveMaestra = clave
            contenido = ContenidoBoveda()
            persistir()
            publicar()
        }
    }

    // ------------------------------------------------------------- desbloqueo

    fun desbloquear(password: CharArray) {
        // Leer, derivar, descifrar y parsear no tocan el estado del repositorio:
        // todo eso va fuera del candado, que es lo caro.
        val bytes = archivoBoveda.readBytes()
        val cabecera = VaultCrypto.leerCabecera(bytes)
        val clave = VaultCrypto.derivarClave(password, cabecera.salt, cabecera.params)
        val plano = try {
            VaultCrypto.descifrar(bytes, clave)
        } catch (e: Exception) {
            Zeroizar.borrar(clave)
            throw e
        }
        val leido = json.decodeFromString(ContenidoBoveda.serializer(), String(plano, Charsets.UTF_8))
        Zeroizar.borrar(plano)
        synchronized(candado) {
            if (claveMaestra != null) {
                // Alguien ha abierto la bóveda mientras derivábamos: otra pantalla, el
                // autorrelleno o la huella. Su estado es más nuevo que el nuestro, que
                // salió del archivo ANTES del Argon2, asi que pisarlo se llevaría por
                // delante lo que hayan guardado entre medias. La bóveda esta abierta,
                // que es lo que pedía quien llama, asi que esto no es un fallo.
                // La clave recien derivada se borra aqui: si no, se queda una clave
                // maestra suelta en el heap hasta que pase el recolector.
                Zeroizar.borrar(clave)
                return
            }
            salt = cabecera.salt
            params = cabecera.params
            claveMaestra = clave
            contenido = leido
            publicar()
        }
    }

    fun desbloquearConClaveMaestra(clave: ByteArray) {
        val bytes = archivoBoveda.readBytes()
        val cabecera = VaultCrypto.leerCabecera(bytes)
        val plano = VaultCrypto.descifrar(bytes, clave)
        val leido = json.decodeFromString(ContenidoBoveda.serializer(), String(plano, Charsets.UTF_8))
        Zeroizar.borrar(plano)
        synchronized(candado) {
            // Misma guarda que en desbloquear(): aqui no hay Argon2, pero leer y
            // descifrar el archivo tampoco es instantaneo y la carrera es la misma.
            if (claveMaestra != null) return
            salt = cabecera.salt
            params = cabecera.params
            claveMaestra = clave.copyOf()
            contenido = leido
            publicar()
        }
    }

    fun bloquear() {
        synchronized(candado) {
            Zeroizar.borrar(claveMaestra)
            claveMaestra = null
            contenido = ContenidoBoveda()
            publicar()
        }
    }

    // ------------------------------------------------------------------- CRUD

    fun entradas(): List<Entrada> = contenido.entradas

    fun entrada(id: String): Entrada? = contenido.entradas.firstOrNull { it.id == id }

    fun nuevoId(): String = UUID.randomUUID().toString()

    fun guardarEntrada(entrada: Entrada) {
        synchronized(candado) {
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
    }

    fun eliminarEntrada(id: String) {
        synchronized(candado) {
            contenido = contenido.copy(entradas = contenido.entradas.filterNot { it.id == id })
            persistir()
            publicar()
        }
    }

    fun alternarFavorito(id: String) {
        // Leer y escribir tienen que ir juntos bajo el mismo candado: si no, dos
        // toques seguidos al corazón pueden acabar en el estado que no toca.
        synchronized(candado) {
            val entrada = contenido.entradas.firstOrNull { it.id == id } ?: return
            val lista = contenido.entradas.toMutableList()
            val posicion = lista.indexOfFirst { it.id == id }
            lista[posicion] = entrada.copy(
                favorito = !entrada.favorito,
                modificadaEn = System.currentTimeMillis()
            )
            contenido = contenido.copy(entradas = lista)
            persistir()
            publicar()
        }
    }

    fun passkeys(): List<Entrada> = contenido.entradas.filter { it.passkey != null }

    fun passkeysDe(rpId: String): List<Entrada> =
        passkeys().filter { it.passkey?.rpId.equals(rpId, ignoreCase = true) }

    // ------------------------------------------------------------ persistencia

    /** Solo se llama con el candado cogido. */
    private fun persistir() {
        val clave = claveMaestra ?: throw IllegalStateException("La bóveda está bloqueada")
        val plano = json.encodeToString(ContenidoBoveda.serializer(), contenido).toByteArray(Charsets.UTF_8)
        val archivo = VaultCrypto.cifrar(plano, clave, salt, params)
        VaultCrypto.escribirAtomico(archivoBoveda, archivo)
        Zeroizar.borrar(plano)
    }

    /**
     * Solo se llama con el candado cogido, y el estado sale de los campos de
     * verdad, no de una foto que traiga quien llama.
     *
     * Publicar fuera del candado parecia mas fino, pero abre esto: guardarEntrada
     * suelta el candado con su instantanea en la mano, entre medias el auto-bloqueo
     * cierra la boveda desde el hilo principal, y al publicar despues se anuncia
     * Desbloqueada con la lista entera de una boveda que ya esta cerrada. Al volver
     * a la app se verian las contrasenas sin pedir nada.
     *
     * Aqui dentro, cualquier transicion de estado va serializada con el cambio que
     * la provoca y no se pueden adelantar unas a otras.
     */
    private fun publicar() {
        _estado.value = when {
            claveMaestra != null -> EstadoBoveda.Desbloqueada(contenido.entradas)
            archivoBoveda.exists() -> EstadoBoveda.Bloqueada
            else -> EstadoBoveda.SinCrear
        }
    }

    // ------------------------------------------------ exportación e importación

    fun exportar(passwordExportacion: CharArray): ByteArray {
        // La foto del contenido se coge con el candado; el Argon2 y el cifrado, fuera.
        val instantanea = synchronized(candado) {
            if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
            contenido
        }
        val saltExport = VaultCrypto.nuevoSalt()
        val clave = VaultCrypto.derivarClave(passwordExportacion, saltExport, KdfParams.PREDETERMINADOS)
        val plano = json.encodeToString(ContenidoBoveda.serializer(), instantanea).toByteArray(Charsets.UTF_8)
        val salida = VaultCrypto.cifrar(plano, clave, saltExport, KdfParams.PREDETERMINADOS)
        Zeroizar.borrar(plano)
        Zeroizar.borrar(clave)
        return salida
    }

    /** Devuelve el número de entradas importadas (fusiona por id). */
    fun importar(archivo: ByteArray, passwordExportacion: CharArray): Int {
        if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
        // Descifrar el archivo que llega no toca el estado: fuera del candado.
        val cabecera = VaultCrypto.leerCabecera(archivo)
        val clave = VaultCrypto.derivarClave(passwordExportacion, cabecera.salt, cabecera.params)
        val plano = try {
            VaultCrypto.descifrar(archivo, clave)
        } finally {
            Zeroizar.borrar(clave)
        }
        val importado = json.decodeFromString(ContenidoBoveda.serializer(), String(plano, Charsets.UTF_8))
        Zeroizar.borrar(plano)
        var nuevas = 0
        synchronized(candado) {
            if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
            val porId = contenido.entradas.associateBy { it.id }.toMutableMap()
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
        }
        return nuevas
    }

    fun cambiarContrasenaMaestra(nueva: CharArray) {
        if (claveMaestra == null) throw IllegalStateException("La bóveda está bloqueada")
        // Argon2 fuera; el cambio de clave y el reescribir el archivo, dentro.
        val nuevoSalt = VaultCrypto.nuevoSalt()
        val claveNueva = VaultCrypto.derivarClave(nueva, nuevoSalt, KdfParams.PREDETERMINADOS)
        synchronized(candado) {
            if (claveMaestra == null) {
                Zeroizar.borrar(claveNueva)
                throw IllegalStateException("La bóveda está bloqueada")
            }
            Zeroizar.borrar(claveMaestra)
            claveMaestra = claveNueva
            salt = nuevoSalt
            params = KdfParams.PREDETERMINADOS
            persistir()
        }
        desactivarBiometria()
    }

    /** Borra las claves y blobs de ambos modos y apaga el ajuste. Único sitio que lo hace. */
    fun desactivarBiometria() {
        biometria.eliminarTodo()
        ajustes.actualizar { it.copy(biometriaActiva = false, biometriaModo = "") }
    }

    // No toca el estado del repositorio, solo lee el archivo: sin candado, y así
    // comprobar la clave actual no bloquea a nadie durante el Argon2.
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

    fun borrarTodo() {
        synchronized(candado) {
            Zeroizar.borrar(claveMaestra)
            claveMaestra = null
            contenido = ContenidoBoveda()
            archivoBoveda.delete()
            publicar()
        }
        desactivarBiometria()
        FrenoIntentos.limpiar(app)
        // El registro de diagnóstico no lleva secretos, pero sí fechas de uso: se va con todo.
        Diagnostico.borrar()
    }

    companion object {
        @Volatile
        private var instancia: VaultRepository? = null

        fun obtener(contexto: Context): VaultRepository =
            instancia ?: synchronized(this) {
                instancia ?: VaultRepository(contexto.applicationContext).also { instancia = it }
            }
    }
}
