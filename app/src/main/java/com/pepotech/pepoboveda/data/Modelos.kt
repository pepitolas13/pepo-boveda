package com.pepotech.pepoboveda.data

import kotlinx.serialization.Serializable

@Serializable
enum class TipoEntrada {
    LOGIN, PASSKEY, NOTA;

    val etiqueta: String
        get() = when (this) {
            LOGIN -> "Contraseña"
            PASSKEY -> "Passkey"
            NOTA -> "Nota segura"
        }
}

@Serializable
data class DatosPasskey(
    val rpId: String,
    val rpName: String,
    val userHandle: String,
    val credId: String,
    val clavePrivada: String,
    val algoritmo: String = "ES256",
    val usuario: String = ""
)

@Serializable
data class Entrada(
    val id: String,
    val tipo: TipoEntrada = TipoEntrada.LOGIN,
    val titulo: String = "",
    val usuario: String = "",
    val contrasena: String = "",
    val urls: List<String> = emptyList(),
    val notas: String = "",
    val secretoTotp: String? = null,
    val totpEmisor: String = "",
    val totpDigitos: Int = 6,
    val totpPeriodo: Int = 30,
    val favorito: Boolean = false,
    val creadaEn: Long = 0L,
    val modificadaEn: Long = 0L,
    val passkey: DatosPasskey? = null
)

@Serializable
data class ContenidoBoveda(
    val version: Int = 1,
    val entradas: List<Entrada> = emptyList()
)

sealed interface EstadoBoveda {
    object SinCrear : EstadoBoveda
    object Bloqueada : EstadoBoveda
    data class Desbloqueada(val entradas: List<Entrada>) : EstadoBoveda
}
