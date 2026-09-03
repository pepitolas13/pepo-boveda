package com.pepotech.pepoboveda.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Registro de diagnóstico local: qué ha pasado con la cámara y con la huella, para
 * que quien pruebe la app en un móvil que yo no tengo pueda mandarme el informe.
 *
 * Regla de oro: aquí NUNCA entra nada secreto ni personal. Ni el contenido de un QR,
 * ni claves, ni contraseñas, ni títulos de entradas, ni dominios, ni rutas de archivos
 * elegidos por el usuario. Solo qué paso se dio, si salió bien y, si no, la clase y el
 * mensaje de la excepción cuando esa excepción viene del sistema (cámara, Keystore,
 * prompt). Para las demás, solo la clase.
 *
 * Vive en memoria (últimas [MAX_LINEAS_MEMORIA] líneas) y en un archivo pequeño del
 * almacenamiento privado, que se recorta solo cuando pasa de [MAX_BYTES_ARCHIVO]. La
 * memoria se actualiza al instante; el archivo lo escribe un hilo propio para no
 * frenar nunca al hilo principal ni al de la cámara.
 */
object Diagnostico {

    const val MAX_LINEAS_MEMORIA = 200
    const val MAX_BYTES_ARCHIVO = 32 * 1024

    private val lineas = ArrayDeque<String>()
    private var archivo: File? = null
    private val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ROOT)
    private val escritor: ExecutorService = Executors.newSingleThreadExecutor { tarea ->
        Thread(tarea, "diagnostico").apply { isDaemon = true }
    }

    /** Se llama una vez al arrancar la app. Carga lo que hubiera de sesiones anteriores. */
    @Synchronized
    fun iniciar(destino: File) {
        archivo = destino
        lineas.clear()
        try {
            if (destino.exists()) {
                destino.readLines(Charsets.UTF_8)
                    .filter { it.isNotBlank() }
                    .takeLast(MAX_LINEAS_MEMORIA)
                    .forEach { lineas.addLast(it) }
            }
        } catch (e: Exception) {
            // Un registro que no se puede leer no debe tumbar nada.
        }
    }

    /** Apunta un paso. [e] se describe por clase y mensaje, nunca por su stack completo. */
    fun apuntar(area: String, mensaje: String, e: Throwable? = null) {
        val texto = buildString {
            append(formato.format(Date()))
            append(' ')
            append(area)
            append(": ")
            append(mensaje.replace('\n', ' '))
            if (e != null) {
                append(" [")
                append(describir(e))
                append(']')
            }
        }
        val destino: File?
        synchronized(this) {
            lineas.addLast(texto)
            while (lineas.size > MAX_LINEAS_MEMORIA) lineas.removeFirst()
            destino = archivo
        }
        if (destino != null) encolar { escribir(destino, texto) }
    }

    /** Las últimas [n] líneas, de la más antigua a la más reciente. */
    @Synchronized
    fun ultimas(n: Int): List<String> = lineas.toList().takeLast(n)

    fun borrar() {
        val destino: File?
        synchronized(this) {
            lineas.clear()
            destino = archivo
        }
        if (destino != null) {
            encolar {
                try {
                    destino.delete()
                } catch (e: Exception) {
                    // nada que borrar
                }
            }
        }
    }

    /** Espera a que el hilo de escritura vacíe su cola. Para los tests y para antes de compartir el archivo. */
    fun esperarEscrituras() {
        try {
            escritor.submit { }.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // Si el hilo está muerto o tarda, seguimos: la memoria ya está al día.
        }
    }

    /**
     * Informe completo: cabecera del móvil, estado en vivo y últimas líneas. La cabecera
     * y el estado los pasa quien llama, así este objeto no depende de Android y se
     * puede probar en la JVM.
     */
    fun informe(cabecera: String, estado: String, n: Int = 60): String = buildString {
        append(cabecera.trimEnd())
        append("\n\n")
        append(estado.trimEnd())
        append("\n\nÚltimos pasos registrados:\n")
        val recientes = ultimas(n)
        if (recientes.isEmpty()) append("(nada registrado todavía)\n")
        recientes.forEach { append(it).append('\n') }
    }

    /**
     * Las rutas del sistema de archivos no son un secreto, pero sí dicen quién eres
     * (usuario, perfil de trabajo, nombre del móvil) y acaban en el informe que la
     * gente pega en un issue. Fuera de los mensajes de error.
     */
    private val PATRON_RUTA =
        Regex("""(?:/data/data/|/data/user/\d*/?|/storage/emulated/\d*/?|/sdcard/)[^\s"']*""")

    private fun sinRutas(texto: String): String = PATRON_RUTA.replace(texto, "<ruta>")

    /** Clase simple y mensaje, siguiendo las causas hasta tres niveles. */
    fun describir(e: Throwable): String {
        val partes = ArrayList<String>()
        var actual: Throwable? = e
        var nivel = 0
        while (actual != null && nivel < 3) {
            val mensaje = sinRutas(actual.message ?: "").replace('\n', ' ').take(160)
            partes += if (mensaje.isBlank()) {
                actual.javaClass.simpleName
            } else {
                "${actual.javaClass.simpleName}: $mensaje"
            }
            val causa = actual.cause
            actual = if (causa === actual) null else causa
            nivel++
        }
        return partes.joinToString(" <- ")
    }

    private fun encolar(tarea: () -> Unit) {
        try {
            escritor.execute {
                try {
                    tarea()
                } catch (e: Exception) {
                    // Sin sitio para escribir: nos quedamos con la memoria.
                }
            }
        } catch (e: Exception) {
            // Ejecutor cerrado: nos quedamos con la memoria.
        }
    }

    /** Solo en el hilo de escritura. */
    private fun escribir(destino: File, texto: String) {
        destino.appendText(texto + "\n", Charsets.UTF_8)
        if (destino.length() > MAX_BYTES_ARCHIVO) recortar(destino)
    }

    /** Deja la mitad más reciente del archivo, para que no crezca sin fin. */
    private fun recortar(destino: File) {
        val todas = destino.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        val conservar = todas.takeLast(maxOf(1, todas.size / 2))
        destino.writeText(conservar.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}
