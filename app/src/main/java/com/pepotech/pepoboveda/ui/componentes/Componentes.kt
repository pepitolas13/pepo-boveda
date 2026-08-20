package com.pepotech.pepoboveda.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.AmbarFuerte
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.DegradadoAmbar
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.Obsidiana
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.Superficie
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Dominios
import kotlin.math.abs

fun coloresMonograma(semilla: String): Pair<Color, Color> {
    val base = Dominios.raiz(semilla).ifEmpty { semilla }.lowercase()
    var hash = 2166136261u.toInt()
    for (c in base) {
        hash = (hash xor c.code) * 16777619
    }
    val tono = (abs(hash) % 360).toFloat()
    val primero = Color.hsv(tono, 0.55f, 0.92f)
    val segundo = Color.hsv((tono + 28f) % 360f, 0.72f, 0.78f)
    return primero to segundo
}

@Composable
fun Monograma(titulo: String, semilla: String, tamano: Int = 46) {
    val (a, b) = coloresMonograma(semilla.ifBlank { titulo })
    val letras = titulo.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
    Box(
        modifier = Modifier
            .size(tamano.dp)
            .clip(RoundedCornerShape((tamano / 3).dp))
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letras,
            color = Obsidiana,
            fontWeight = FontWeight.Bold,
            fontSize = (tamano / 2.4f).sp
        )
    }
}

@Composable
fun TarjetaPepo(
    modifier: Modifier = Modifier,
    alPulsar: (() -> Unit)? = null,
    contenido: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Superficie)
            .border(1.dp, Borde, forma)
            .then(if (alPulsar != null) Modifier.clickable { alPulsar() } else Modifier)
            .padding(16.dp)
    ) {
        Column(content = contenido)
    }
}

@Composable
fun BotonAmbar(
    texto: String,
    modifier: Modifier = Modifier,
    activo: Boolean = true,
    alPulsar: () -> Unit
) {
    val escala by animateFloatAsState(
        targetValue = if (activo) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "escalaBoton"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (activo) DegradadoAmbar else Brush.horizontalGradient(listOf(Borde, Borde)))
            .clickable(enabled = activo) { alPulsar() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = if (activo) Obsidiana else TextoSecundario,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = (16 * escala).sp)
        )
    }
}

@Composable
fun BotonBorde(
    texto: String,
    modifier: Modifier = Modifier,
    color: Color = TextoPrincipal,
    alPulsar: () -> Unit
) {
    val forma = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(forma)
            .border(1.dp, Borde, forma)
            .clickable { alPulsar() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = texto, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CampoPepo(
    valor: String,
    etiqueta: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    esContrasena: Boolean = false,
    mostrarContrasena: Boolean = false,
    monoespaciada: Boolean = false,
    varias: Boolean = false,
    tecladoNumerico: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth(),
        singleLine = !varias,
        minLines = if (varias) 3 else 1,
        textStyle = if (monoespaciada) {
            MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        visualTransformation = if (esContrasena && !mostrarContrasena) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = when {
                tecladoNumerico -> KeyboardType.Number
                esContrasena -> KeyboardType.Password
                else -> KeyboardType.Text
            }
        ),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Superficie,
            unfocusedContainerColor = Superficie,
            focusedIndicatorColor = Ambar,
            unfocusedIndicatorColor = Borde,
            focusedLabelColor = Ambar,
            unfocusedLabelColor = TextoSecundario,
            cursorColor = Ambar,
            focusedTextColor = TextoPrincipal,
            unfocusedTextColor = TextoPrincipal
        )
    )
}

@Composable
fun BarraFuerza(fraccion: Float, etiqueta: String, tiempo: String) {
    val anchoAnimado by animateFloatAsState(
        targetValue = fraccion,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "fuerza"
    )
    val color by animateColorAsState(
        targetValue = when {
            fraccion < 0.35f -> Peligro
            fraccion < 0.65f -> AmbarFuerte
            fraccion < 0.85f -> Ambar
            else -> Menta
        },
        animationSpec = spring(dampingRatio = 0.7f),
        label = "colorFuerza"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(Borde)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(anchoAnimado)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(etiqueta, color = color, style = MaterialTheme.typography.labelLarge)
            Text(
                "crackearla costaría $tiempo",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun EtiquetaSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(),
        color = TextoSecundario,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp),
        modifier = modifier
    )
}
