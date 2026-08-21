# Pepo Bóveda

Gestor de contraseñas para Android. Sin cuentas, sin nube, sin permiso de internet.

No te pido que confíes en mí: te pido que lo compruebes. Abre
[`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) y mira los permisos.
Son tres, y ninguno es `INTERNET`:

| Permiso | Para qué |
|---|---|
| `USE_BIOMETRIC` | abrir con la huella |
| `VIBRATE` | las microinteracciones |
| `CAMERA` | leer el QR de un 2FA, y solo si pulsas "escanear" |

Si una app no puede hablar con la red, no puede mandar tus contraseñas a ningún sitio.

## Cómo guarda las contraseñas

La contraseña maestra **no se guarda en ninguna parte**. Se pasa por **Argon2id**
(64 MiB, 3 iteraciones, paralelismo 4) para derivar una clave, y con ella se cifra la
bóveda entera con **AES-256-GCM**.

- La cabecera del archivo (sal y parámetros de Argon2) va como AAD, así que está
  autenticada: no se puede editar para rebajar el coste del KDF sin que falle el tag.
- Sal de 16 bytes y nonce de 12 bytes aleatorios, nuevos en cada escritura.
- La bóveda es un único archivo en el almacenamiento privado de la app.
- La biometría no sustituye a la contraseña: envuelve la clave maestra con una clave del
  Android Keystore que exige Clase 3 en cada uso y se invalida si cambia la biometría
  del dispositivo.

## Qué hay dentro

Bóveda cifrada, generador con `SecureRandom` y medidor de entropía, autenticador TOTP
(RFC 6238) con lector de QR mediante ZXing, servicio de autofill, proveedor de passkeys
(WebAuthn/CBOR propio), exportar/importar cifrado y bloqueo automático por inactividad.

## Compilar

Necesitas JDK 17 y el SDK de Android (compileSdk 36, minSdk 29).

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Para una build de release firmada, copia `keystore.properties.ejemplo` a
`keystore.properties`, crea tu propio keystore y rellena tus valores:

```bash
keytool -genkeypair -v -keystore pepo-boveda.jks -alias pepoboveda \
        -keyalg RSA -keysize 4096 -validity 10000
./gradlew :app:assembleRelease
```

`keystore.properties` y los `.jks` están en `.gitignore` y no deben subirse nunca.

## Estado honesto

Esto es importante y no lo voy a esconder:

- **Probada a mano en dos dispositivos físicos.** Ahí se ha visto funcionar la bóveda,
  la biometría, el autofill, el registro de passkeys en el navegador, la cámara del
  escáner y el autenticador TOTP. Lo que no está es una batería de pruebas
  automatizadas más amplia: los tests unitarios pasan (criptografía, Base32, TOTP,
  generador, dominios, CBOR) y los instrumentados de Argon2 también se han corrido y
  pasan. Pero dos móviles no son un banco de pruebas: no esperes que esté probada en
  todos los fabricantes ni en todas las versiones de Android.
- **Aquí es donde me vienes bien tú.** Si la pruebas y algo se rompe, me vendrá
  fenomenal que me lo digas: qué móvil, qué versión de Android, qué hiciste y qué pasó.
  Un fallo que encuentres es un fallo que dejo de tener. Abre un issue sin miedo, y si
  te sabes buscar la vida, mira el código y dime qué está mal.
- Sigue abierto: si Android no devuelve la firma de la app que pide una passkey, se
  firma igual en vez de abortar. Y el APK pesa ~44 MB porque incluye Argon2 para las
  cuatro ABI; con splits bajaría a unos 15 MB.

El código lo ha revisado quien lo ha escrito, que es el peor revisor posible. Por eso
está aquí entero y por eso te pido que lo mires: no te pido confianza, te pido que lo
compruebes.
