package com.pepotech.pepoboveda.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Obsidiana = Color(0xFF0E0F13)
val Superficie = Color(0xFF161922)
val SuperficieAlta = Color(0xFF1C2130)
val Borde = Color(0xFF232838)
val Ambar = Color(0xFFFFB74D)
val AmbarFuerte = Color(0xFFFF8A3D)
val Menta = Color(0xFF57E6B4)
val Peligro = Color(0xFFFF5D5D)
val TextoPrincipal = Color(0xFFF2F4F8)
val TextoSecundario = Color(0xFF9AA3B8)

val DegradadoAmbar = Brush.horizontalGradient(listOf(Ambar, AmbarFuerte))

fun degradadoAmbarVertical() = Brush.verticalGradient(listOf(Ambar, AmbarFuerte))

private val esquemaOscuro = darkColorScheme(
    primary = Ambar,
    onPrimary = Obsidiana,
    primaryContainer = AmbarFuerte,
    onPrimaryContainer = Obsidiana,
    secondary = Menta,
    onSecondary = Obsidiana,
    background = Obsidiana,
    onBackground = TextoPrincipal,
    surface = Superficie,
    onSurface = TextoPrincipal,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoSecundario,
    outline = Borde,
    error = Peligro,
    onError = Obsidiana
)

private val formas = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val tipografia = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp
    )
)

val EstiloMonoGrande = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    letterSpacing = 1.sp
)

val EstiloMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    letterSpacing = 0.5.sp
)

@Composable
fun PepoBovedaTheme(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = esquemaOscuro,
        typography = tipografia,
        shapes = formas,
        content = contenido
    )
}
