# Argon2 cruza a codigo nativo por JNI: la parte en C busca las clases y los
# metodos por su nombre exacto, asi que si R8 los renombra deja de encontrarlos.
# Esta regla no se puede tocar.
-keep class com.lambdapioneer.argon2kt.** { *; }

# kotlinx.serialization trae sus propias reglas dentro del .jar, y aqui los
# serializadores se piden a mano (ContenidoBoveda.serializer()), no por reflexion,
# asi que R8 los rastrea solo. Lo unico que hay que sujetar son los serializadores
# generados de los modelos, que si se pierden se lleva por delante la boveda.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.pepotech.pepoboveda.data.**$$serializer { *; }
-keepclassmembers class com.pepotech.pepoboveda.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.pepotech.pepoboveda.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Numeros de linea en las trazas, para que un informe de fallo siga sirviendo de
# algo. El nombre del fichero se sustituye por uno falso: guardarlo de verdad
# seria regalar la estructura del codigo, que es justo lo que se quiere esconder.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Nada mas. Ni -keep de los paquetes de la app (eso deja todo sin ofuscar y
# convierte el minify en un gesto), ni reglas de librerias que este proyecto no
# usa, ni -dontwarn en bloque que tape avisos de verdad.
