package com.pepotech.pepoboveda.passkey

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Implementación local del autenticador WebAuthn: genera pares ES256,
 * firma aserciones y construye los JSON que espera el navegador o la app.
 * Nada de esto sale del dispositivo salvo la parte pública.
 */
object WebAuthn {

    const val ALGORITMO = -7

    private const val B64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun aB64Url(datos: ByteArray): String = Base64.encodeToString(datos, B64)

    fun deB64Url(texto: String): ByteArray = Base64.decode(texto, B64)

    fun sha256(datos: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(datos)

    /** 16 bytes aleatorios, como manda el formato de credencial. */
    fun nuevoCredId(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    class ParDeClaves(val privadaPkcs8: ByteArray, val x: ByteArray, val y: ByteArray)

    fun generarPar(): ParDeClaves {
        val generador = KeyPairGenerator.getInstance("EC")
        generador.initialize(ECGenParameterSpec("secp256r1"))
        val par = generador.generateKeyPair()
        val publica = par.public as ECPublicKey
        return ParDeClaves(
            privadaPkcs8 = par.private.encoded,
            x = coordenada(publica.w.affineX.toByteArray()),
            y = coordenada(publica.w.affineY.toByteArray())
        )
    }

    /** Normaliza una coordenada de P-256 a exactamente 32 bytes. */
    private fun coordenada(bytes: ByteArray): ByteArray = when {
        bytes.size == 32 -> bytes
        bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
        else -> ByteArray(32).also { bytes.copyInto(it, 32 - bytes.size) }
    }

    private fun privada(pkcs8: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    /** Firma ECDSA en formato DER, tal y como lo espera WebAuthn. */
    fun firmar(pkcs8: ByteArray, datos: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privada(pkcs8))
            update(datos)
            sign()
        }

    fun clientDataJson(tipo: String, retoB64Url: String, origen: String): ByteArray {
        val objeto = JSONObject()
        objeto.put("type", tipo)
        objeto.put("challenge", retoB64Url)
        objeto.put("origin", origen)
        objeto.put("crossOrigin", false)
        return objeto.toString().toByteArray(Charsets.UTF_8)
    }

    private const val BANDERA_UP = 0x01
    private const val BANDERA_UV = 0x04
    private const val BANDERA_AT = 0x40

    fun authenticatorDataRegistro(rpId: String, credId: ByteArray, x: ByteArray, y: ByteArray): ByteArray {
        val cose = Cbor.clavePublicaCose(x, y)
        val aaguid = ByteArray(16)
        val longitud = byteArrayOf(
            ((credId.size shr 8) and 0xFF).toByte(),
            (credId.size and 0xFF).toByte()
        )
        return sha256(rpId.toByteArray(Charsets.UTF_8)) +
            byteArrayOf((BANDERA_UP or BANDERA_UV or BANDERA_AT).toByte()) +
            byteArrayOf(0, 0, 0, 0) +
            aaguid + longitud + credId + cose
    }

    fun authenticatorDataAsercion(rpId: String, contador: Int): ByteArray {
        val cuenta = byteArrayOf(
            ((contador shr 24) and 0xFF).toByte(),
            ((contador shr 16) and 0xFF).toByte(),
            ((contador shr 8) and 0xFF).toByte(),
            (contador and 0xFF).toByte()
        )
        return sha256(rpId.toByteArray(Charsets.UTF_8)) +
            byteArrayOf((BANDERA_UP or BANDERA_UV).toByte()) +
            cuenta
    }

    /** Objeto de attestation con formato "none": no delatamos el dispositivo. */
    fun attestationObject(authData: ByteArray): ByteArray {
        val mapa = LinkedHashMap<Any, Any>()
        mapa["fmt"] = "none"
        mapa["attStmt"] = LinkedHashMap<Any, Any>()
        mapa["authData"] = authData
        return Cbor.codificar(mapa)
    }

    /**
     * Clave publica en formato SPKI (DER) para P-256. La cabecera es fija: solo
     * cambian los 32 bytes de X y los 32 de Y.
     */
    private fun spkiP256(x: ByteArray, y: ByteArray): ByteArray {
        val cabecera = intArrayOf(
            0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE,
            0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D,
            0x03, 0x01, 0x07, 0x03, 0x42, 0x00, 0x04
        ).map { it.toByte() }.toByteArray()
        return cabecera + rellenar32(x) + rellenar32(y)
    }

    private fun rellenar32(valor: ByteArray): ByteArray = when {
        valor.size == 32 -> valor
        valor.size > 32 -> valor.copyOfRange(valor.size - 32, valor.size)
        else -> ByteArray(32 - valor.size) + valor
    }

    fun respuestaRegistro(
        credId: ByteArray,
        clientData: ByteArray,
        attestation: ByteArray,
        authData: ByteArray,
        x: ByteArray,
        y: ByteArray
    ): String {
        val id = aB64Url(credId)
        val respuesta = JSONObject()
        respuesta.put("clientDataJSON", aB64Url(clientData))
        respuesta.put("attestationObject", aB64Url(attestation))
        // Chrome exige estos tres campos o descarta la respuesta entera.
        respuesta.put("authenticatorData", aB64Url(authData))
        respuesta.put("publicKeyAlgorithm", -7)
        respuesta.put("publicKey", aB64Url(spkiP256(x, y)))
        respuesta.put("transports", org.json.JSONArray(listOf("internal", "hybrid")))
        val raiz = JSONObject()
        raiz.put("id", id)
        raiz.put("rawId", id)
        raiz.put("type", "public-key")
        raiz.put("authenticatorAttachment", "platform")
        raiz.put("clientExtensionResults", JSONObject())
        raiz.put("response", respuesta)
        return raiz.toString()
    }

    fun respuestaAsercion(
        credId: ByteArray,
        clientData: ByteArray,
        authData: ByteArray,
        firma: ByteArray,
        userHandle: ByteArray?
    ): String {
        val id = aB64Url(credId)
        val respuesta = JSONObject()
        respuesta.put("clientDataJSON", aB64Url(clientData))
        respuesta.put("authenticatorData", aB64Url(authData))
        respuesta.put("signature", aB64Url(firma))
        respuesta.put("userHandle", userHandle?.let { aB64Url(it) })
        val raiz = JSONObject()
        raiz.put("id", id)
        raiz.put("rawId", id)
        raiz.put("type", "public-key")
        raiz.put("authenticatorAttachment", "platform")
        raiz.put("clientExtensionResults", JSONObject())
        raiz.put("response", respuesta)
        return raiz.toString()
    }

    // ------------------------------------------------------- lectura de peticiones

    class PeticionCreacion(
        val rpId: String,
        val rpName: String,
        val usuario: String,
        val userHandle: String,
        val reto: String,
        val algoritmosSoportados: Boolean
    )

    fun leerCreacion(requestJson: String): PeticionCreacion {
        val raiz = JSONObject(requestJson)
        val rp = raiz.optJSONObject("rp") ?: JSONObject()
        val usuario = raiz.optJSONObject("user") ?: JSONObject()
        val parametros = raiz.optJSONArray("pubKeyCredParams")
        var soportado = parametros == null || parametros.length() == 0
        if (parametros != null) {
            for (i in 0 until parametros.length()) {
                if (parametros.optJSONObject(i)?.optInt("alg") == ALGORITMO) soportado = true
            }
        }
        return PeticionCreacion(
            rpId = rp.optString("id"),
            rpName = rp.optString("name").ifBlank { rp.optString("id") },
            usuario = usuario.optString("name").ifBlank { usuario.optString("displayName") },
            userHandle = usuario.optString("id"),
            reto = raiz.optString("challenge"),
            algoritmosSoportados = soportado
        )
    }

    class PeticionAsercion(
        val rpId: String,
        val reto: String,
        val credencialesPermitidas: List<String>
    )

    fun leerAsercion(requestJson: String): PeticionAsercion {
        val raiz = JSONObject(requestJson)
        val permitidas = mutableListOf<String>()
        raiz.optJSONArray("allowCredentials")?.let { lista ->
            for (i in 0 until lista.length()) {
                lista.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { permitidas.add(it) }
            }
        }
        return PeticionAsercion(
            rpId = raiz.optString("rpId"),
            reto = raiz.optString("challenge"),
            credencialesPermitidas = permitidas
        )
    }
}
