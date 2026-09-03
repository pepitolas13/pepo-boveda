package com.pepotech.pepoboveda.data

import com.pepotech.pepoboveda.crypto.Base32
import com.pepotech.pepoboveda.crypto.Zeroizar
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

@Serializable(with = DatosPasskey.Serializer::class)
data class DatosPasskey(
    val rpId: String,
    val rpName: String,
    val userHandle: String,
    val credId: String,
    var clavePrivada: ByteArray,
    val algoritmo: String = "ES256",
    val usuario: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatosPasskey) return false
        return rpId == other.rpId &&
            rpName == other.rpName &&
            userHandle == other.userHandle &&
            credId == other.credId &&
            clavePrivada.contentEquals(other.clavePrivada) &&
            algoritmo == other.algoritmo &&
            usuario == other.usuario
    }

    override fun hashCode(): Int {
        var result = rpId.hashCode()
        result = 31 * result + rpName.hashCode()
        result = 31 * result + userHandle.hashCode()
        result = 31 * result + credId.hashCode()
        result = 31 * result + clavePrivada.contentHashCode()
        result = 31 * result + algoritmo.hashCode()
        result = 31 * result + usuario.hashCode()
        return result
    }

    fun limpiar() {
        Zeroizar.borrar(clavePrivada)
        clavePrivada = ByteArray(0)
    }

    object Serializer : kotlinx.serialization.KSerializer<DatosPasskey> {
        override val descriptor = buildClassSerialDescriptor("DatosPasskey") {
            element("rpId", PrimitiveSerialDescriptor("rpId", PrimitiveKind.STRING))
            element("rpName", PrimitiveSerialDescriptor("rpName", PrimitiveKind.STRING))
            element("userHandle", PrimitiveSerialDescriptor("userHandle", PrimitiveKind.STRING))
            element("credId", PrimitiveSerialDescriptor("credId", PrimitiveKind.STRING))
            element("clavePrivada", PrimitiveSerialDescriptor("clavePrivada", PrimitiveKind.STRING))
            element("algoritmo", PrimitiveSerialDescriptor("algoritmo", PrimitiveKind.STRING))
            element("usuario", PrimitiveSerialDescriptor("usuario", PrimitiveKind.STRING))
        }

        override fun serialize(encoder: Encoder, value: DatosPasskey) {
            val b64Url = android.util.Base64.encodeToString(value.clavePrivada, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val map = buildMap {
                put("rpId", JsonPrimitive(value.rpId))
                put("rpName", JsonPrimitive(value.rpName))
                put("userHandle", JsonPrimitive(value.userHandle))
                put("credId", JsonPrimitive(value.credId))
                put("clavePrivada", JsonPrimitive(b64Url))
                put("algoritmo", JsonPrimitive(value.algoritmo))
                put("usuario", JsonPrimitive(value.usuario))
            }
            (encoder as JsonEncoder).encodeJsonElement(JsonObject(map))
        }

        override fun deserialize(decoder: Decoder): DatosPasskey {
            val jsonElement = (decoder as JsonDecoder).decodeJsonElement()
            val jsonObject = jsonElement as JsonObject
            val rpId = (jsonObject["rpId"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val rpName = (jsonObject["rpName"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val userHandle = (jsonObject["userHandle"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val credId = (jsonObject["credId"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val claveB64 = (jsonObject["clavePrivada"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val algoritmo = (jsonObject["algoritmo"] as? JsonPrimitive)?.toString()?.trim('"') ?: "ES256"
            val usuario = (jsonObject["usuario"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val clavePrivada = android.util.Base64.decode(claveB64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            return DatosPasskey(rpId, rpName, userHandle, credId, clavePrivada, algoritmo, usuario)
        }
    }
}

@Serializable(with = Entrada.Serializer::class)
data class Entrada(
    val id: String,
    val tipo: TipoEntrada = TipoEntrada.LOGIN,
    val titulo: String = "",
    val usuario: String = "",
    val contrasena: ByteArray = ByteArray(0),
    val urls: List<String> = emptyList(),
    val notas: String = "",
    val secretoTotp: ByteArray? = null,
    val totpEmisor: String = "",
    val totpDigitos: Int = 6,
    val totpPeriodo: Int = 30,
    val favorito: Boolean = false,
    val creadaEn: Long = 0L,
    val modificadaEn: Long = 0L,
    val passkey: DatosPasskey? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Entrada) return false
        return id == other.id && tipo == other.tipo && titulo == other.titulo &&
            usuario == other.usuario && contrasena.contentEquals(other.contrasena) && urls == other.urls &&
            notas == other.notas && (
                (secretoTotp == null && other.secretoTotp == null) ||
                (secretoTotp != null && other.secretoTotp != null && secretoTotp.contentEquals(other.secretoTotp))
            ) &&
            totpEmisor == other.totpEmisor && totpDigitos == other.totpDigitos &&
            totpPeriodo == other.totpPeriodo && favorito == other.favorito &&
            creadaEn == other.creadaEn && modificadaEn == other.modificadaEn &&
            passkey == other.passkey
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + tipo.hashCode()
        result = 31 * result + titulo.hashCode()
        result = 31 * result + usuario.hashCode()
        result = 31 * result + contrasena.contentHashCode()
        result = 31 * result + urls.hashCode()
        result = 31 * result + notas.hashCode()
        result = 31 * result + (secretoTotp?.contentHashCode() ?: 0)
        result = 31 * result + totpEmisor.hashCode()
        result = 31 * result + totpDigitos
        result = 31 * result + totpPeriodo
        result = 31 * result + favorito.hashCode()
        result = 31 * result + creadaEn.hashCode()
        result = 31 * result + modificadaEn.hashCode()
        result = 31 * result + (passkey?.hashCode() ?: 0)
        return result
    }

    fun limpiar() {
        Zeroizar.borrar(contrasena)
        secretoTotp?.let { Zeroizar.borrar(it) }
        passkey?.limpiar()
    }

    companion object {
        fun Entrada.sinSecretos(): Entrada = copy(
            contrasena = ByteArray(0),
            notas = "",
            usuario = "",
            secretoTotp = null
        )
    }

    object Serializer : KSerializer<Entrada> {
        override val descriptor = buildClassSerialDescriptor("Entrada") {
            element("id", PrimitiveSerialDescriptor("id", PrimitiveKind.STRING))
            element("tipo", PrimitiveSerialDescriptor("tipo", PrimitiveKind.STRING))
            element("titulo", PrimitiveSerialDescriptor("titulo", PrimitiveKind.STRING))
            element("usuario", PrimitiveSerialDescriptor("usuario", PrimitiveKind.STRING))
            element("contrasena", PrimitiveSerialDescriptor("contrasena", PrimitiveKind.STRING))
            element("urls", ListSerializer(String.serializer()).descriptor)
            element("notas", PrimitiveSerialDescriptor("notas", PrimitiveKind.STRING))
            element("secretoTotp", PrimitiveSerialDescriptor("secretoTotp", PrimitiveKind.STRING).nullable)
            element("totpEmisor", PrimitiveSerialDescriptor("totpEmisor", PrimitiveKind.STRING))
            element("totpDigitos", PrimitiveSerialDescriptor("totpDigitos", PrimitiveKind.INT))
            element("totpPeriodo", PrimitiveSerialDescriptor("totpPeriodo", PrimitiveKind.INT))
            element("favorito", PrimitiveSerialDescriptor("favorito", PrimitiveKind.BOOLEAN))
            element("creadaEn", PrimitiveSerialDescriptor("creadaEn", PrimitiveKind.LONG))
            element("modificadaEn", PrimitiveSerialDescriptor("modificadaEn", PrimitiveKind.LONG))
            element("passkey", buildClassSerialDescriptor("passkey"))
        }

        override fun serialize(encoder: Encoder, value: Entrada) {
            val secretoB64 = value.secretoTotp?.let {
                Base32.codificar(it)
            }
            val contrasenaB64 = if (value.contrasena.isNotEmpty()) {
                android.util.Base64.encodeToString(value.contrasena, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            } else {
                null
            }
            val map = buildMap {
                put("id", JsonPrimitive(value.id))
                put("tipo", JsonPrimitive(value.tipo.name))
                put("titulo", JsonPrimitive(value.titulo))
                put("usuario", JsonPrimitive(value.usuario))
                if (contrasenaB64 != null) put("contrasena", JsonPrimitive(contrasenaB64))
                put("urls", kotlinx.serialization.json.JsonArray(value.urls.map { JsonPrimitive(it) }))
                put("notas", JsonPrimitive(value.notas))
                if (secretoB64 != null) put("secretoTotp", JsonPrimitive(secretoB64))
                put("totpEmisor", JsonPrimitive(value.totpEmisor))
                put("totpDigitos", JsonPrimitive(value.totpDigitos))
                put("totpPeriodo", JsonPrimitive(value.totpPeriodo))
                put("favorito", JsonPrimitive(value.favorito))
                put("creadaEn", JsonPrimitive(value.creadaEn))
                put("modificadaEn", JsonPrimitive(value.modificadaEn))
                if (value.passkey != null) {
                    val passkeyElement = kotlinx.serialization.json.Json.encodeToJsonElement(
                        DatosPasskey.Serializer,
                        value.passkey
                    )
                    put("passkey", passkeyElement)
                }
            }
            (encoder as JsonEncoder).encodeJsonElement(JsonObject(map))
        }

        override fun deserialize(decoder: Decoder): Entrada {
            val jsonElement = (decoder as JsonDecoder).decodeJsonElement()
            val json = jsonElement as JsonObject
            val id = (json["id"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val tipo = runCatching { TipoEntrada.valueOf((json["tipo"] as? JsonPrimitive)?.toString()?.trim('"') ?: "LOGIN") }.getOrDefault(TipoEntrada.LOGIN)
            val titulo = (json["titulo"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val usuario = (json["usuario"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val contrasenaB64 = (json["contrasena"] as? JsonPrimitive)?.toString()?.trim('"')
            val contrasena = contrasenaB64?.takeIf { it.isNotEmpty() }?.let {
                runCatching { android.util.Base64.decode(it, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP) }.getOrNull()
            } ?: ByteArray(0)
            val urlsElement = json["urls"]
            val urls = if (urlsElement is kotlinx.serialization.json.JsonArray) {
                urlsElement.mapNotNull { (it as? JsonPrimitive)?.toString()?.trim('"') }
            } else emptyList()
            val notas = (json["notas"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val secretoB64 = (json["secretoTotp"] as? JsonPrimitive)?.toString()?.trim('"')
            val secretoTotp = secretoB64?.takeIf { it.isNotEmpty() }?.let {
                runCatching { Base32.decodificar(it) }.getOrNull()
            }
            val totpEmisor = (json["totpEmisor"] as? JsonPrimitive)?.toString()?.trim('"') ?: ""
            val totpDigitos = (json["totpDigitos"] as? JsonPrimitive)?.toString()?.trim('"')?.toIntOrNull() ?: 6
            val totpPeriodo = (json["totpPeriodo"] as? JsonPrimitive)?.toString()?.trim('"')?.toIntOrNull() ?: 30
            val favorito = (json["favorito"] as? JsonPrimitive)?.toString()?.trim('"')?.toBooleanStrictOrNull() ?: false
            val creadaEn = (json["creadaEn"] as? JsonPrimitive)?.toString()?.trim('"')?.toLongOrNull() ?: 0L
            val modificadaEn = (json["modificadaEn"] as? JsonPrimitive)?.toString()?.trim('"')?.toLongOrNull() ?: 0L
            val passkeyElement = json["passkey"]
            val passkey = if (passkeyElement is JsonObject) {
                runCatching { kotlinx.serialization.json.Json.decodeFromJsonElement(DatosPasskey.Serializer, passkeyElement) }.getOrNull()
            } else null
            return Entrada(id, tipo, titulo, usuario, contrasena, urls, notas, secretoTotp, totpEmisor, totpDigitos, totpPeriodo, favorito, creadaEn, modificadaEn, passkey)
        }
    }
}

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

fun ByteArray?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()
