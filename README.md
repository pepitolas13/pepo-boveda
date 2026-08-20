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

- **Nada se ha ejecutado en un dispositivo físico ni en un emulador.** El proyecto
  compila y los tests unitarios pasan (criptografía, Base32, TOTP, generador, dominios,
  CBOR), pero biometría, autofill, passkeys, cámara y exportar/importar **no se han
  visto funcionar**. Los tests instrumentados de Argon2 compilan y nunca se han corrido.
- Una auditoría adversarial encontró y corrigió tres fallos reales: suplantación de app
  en el autofill (se derivaba un dominio desde el nombre del paquete), el bloqueo por
  inactividad no actuaba en primer plano, y no había ningún freno a los intentos de
  contraseña. El freno vive en memoria, así que reiniciar la app lo reinicia.
- Sigue abierto: si Android no devuelve la firma de la app que pide una passkey, se
  firma igual en vez de abortar. Y el APK pesa ~44 MB porque incluye Argon2 para las
  cuatro ABI; con splits bajaría a unos 15 MB.

La auditoría la hizo quien escribió el código. Encontró fallos que llevaban ahí desde
el principio, lo que sugiere que se dejó otros. Si encuentras alguno, abre un issue.
