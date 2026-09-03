# Librerías externas críticas
-keep class com.lambdapioneer.argon2kt.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class org.bouncycastle.** { *; }

# Tink (Seguridad)
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }

# Atributos esenciales para Compose y Kotlin
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod
-keep class kotlin.Metadata { *; }

# Compose y Material 3
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}

# Blindaje de la UI de la aplicación (para evitar problemas con el Tema y Compose)
-keep class com.pepotech.pepoboveda.ui.** { *; }
-keep class com.pepotech.pepoboveda.data.** { *; }
-keep class com.pepotech.pepoboveda.crypto.** { *; }

# Mantener entry points de Android
-keep class com.pepotech.pepoboveda.PepoBovedaApp { *; }
-keep class com.pepotech.pepoboveda.ui.MainActivity { *; }
-keep class com.pepotech.pepoboveda.passkey.** { *; }
-keep class com.pepotech.pepoboveda.autofill.** { *; }

# Advertencias y optimizaciones generales
-dontwarn android.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Eliminación de logs en la versión final
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** d(...);
    public static *** e(...);
}
