package com.pepotech.pepoboveda.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

data class KdfParams(
    val memoryKiB: Int = 65_536,
    val iterations: Int = 3,
    val parallelism: Int = 4,
    val hashLength: Int = 32
) {
    companion object {
        val PREDETERMINADOS = KdfParams()
    }
}

interface Kdf {
    fun derivar(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray
}

object Argon2Kdf : Kdf {

    private val argon2 by lazy { Argon2Kt() }

    override fun derivar(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val bytes = Zeroizar.aBytes(password)
        try {
            val resultado = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = bytes,
                salt = salt,
                tCostInIterations = params.iterations,
                mCostInKibibyte = params.memoryKiB,
                parallelism = params.parallelism,
                hashLengthInBytes = params.hashLength
            )
            return resultado.rawHashAsByteArray()
        } finally {
            Zeroizar.borrar(bytes)
        }
    }
}
