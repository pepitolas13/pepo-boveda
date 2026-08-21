package com.pepotech.pepoboveda.ui.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pepotech.pepoboveda.crypto.Totp
import com.pepotech.pepoboveda.ui.theme.Ambar
import com.pepotech.pepoboveda.ui.theme.AmbarFuerte
import com.pepotech.pepoboveda.ui.theme.Borde
import com.pepotech.pepoboveda.ui.theme.EstiloMono
import com.pepotech.pepoboveda.ui.theme.EstiloMonoGrande
import com.pepotech.pepoboveda.ui.theme.Menta
import com.pepotech.pepoboveda.ui.theme.Peligro
import com.pepotech.pepoboveda.ui.theme.TextoPrincipal
import com.pepotech.pepoboveda.ui.theme.TextoSecundario
import com.pepotech.pepoboveda.util.Haptica
import kotlinx.coroutines.delay
import java.security.SecureRandom

private val GLIFOS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%&*?/-_=+".toCharArray()
private val aleatorio = SecureRandom()

fun contrasenaColoreada(texto: String): AnnotatedString = buildAnnotatedString {
    texto.forEach { c ->
        val color = when {
            c.isDigit() -> Ambar
            c.isLetter() -> TextoPrincipal
            else -> Menta
        }
        withStyle(SpanStyle(color = color)) { append(c) }
    }
}

@Composable
fun ContrasenaSlotMachine(
    objetivo: String,
    generacion: Int,
    haptica: Haptica?,
    modifier: Modifier = Modifier
) {
    var mostrado by remember { mutableStateOf(objetivo) }
    LaunchedEffect(generacion, objetivo) {
        if (objetivo.isEmpty()) {
            mostrado = ""
            return@LaunchedEffect
        }
        val n = objetivo.length
        val buffer = CharArray(n) { GLIFOS[aleatorio.nextInt(GLIFOS.size)] }
        val fijados = BooleanArray(n)
        var siguiente = 0
        val duracionTotal = 420L
        val inicio = System.currentTimeMillis()
        while (siguiente < n) {
            val transcurrido = System.currentTimeMillis() - inicio
            val objetivoFijados = ((transcurrido.toFloat() / duracionTotal) * n).toInt().coerceAtMost(n)
            while (siguiente < objetivoFijados) {
                buffer[siguiente] = objetivo[siguiente]
                fijados[siguiente] = true
                siguiente++
                haptica?.tic()
            }
            for (i in 0 until n) {
                if (!fijados[i]) buffer[i] = GLIFOS[aleatorio.nextInt(GLIFOS.size)]
            }
            mostrado = String(buffer)
            delay(26)
        }
        mostrado = objetivo
    }
    Text(
        text = contrasenaColoreada(mostrado),
        style = if (objetivo.length > 28) EstiloMono else EstiloMonoGrande,
        modifier = modifier
    )
}

@Composable
fun AnilloTotp(
    codigo: String,
    segundosRestantes: Long,
    tamano: Int = 92,
    periodo: Long = Totp.PERIODO_SEGUNDOS
) {
    val objetivo = (segundosRestantes.toFloat() / periodo).coerceIn(0f, 1f)
    // El vaciado va continuo, no a saltos de un segundo. Cuando el ciclo se reinicia
    // (la fraccion sube) el anillo salta al maximo de golpe: rellenarse despacio
    // se veria al reves de lo que pasa.
    val animada = remember { Animatable(objetivo) }
    LaunchedEffect(objetivo) {
        if (objetivo > animada.value) {
            animada.snapTo(objetivo)
        } else {
            animada.animateTo(objetivo, tween(durationMillis = 1000, easing = LinearEasing))
        }
    }
    val fraccion = animada.value
    val color by animateColorAsState(
        targetValue = when {
            segundosRestantes <= 5 -> Peligro
            segundosRestantes <= 10 -> AmbarFuerte
            else -> Ambar
        },
        animationSpec = spring(dampingRatio = 0.7f),
        label = "colorAnillo"
    )
    Box(modifier = Modifier.size(tamano.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(tamano.dp)) {
            // Grosor proporcional: en 26dp un trazo de 7dp se come el círculo.
            val grosor = (tamano * 0.09f).coerceIn(2.5f, 7f).dp.toPx()
            drawArc(
                color = Borde,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(grosor / 2, grosor / 2),
                size = Size(size.width - grosor, size.height - grosor),
                style = Stroke(width = grosor)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraccion,
                useCenter = false,
                topLeft = Offset(grosor / 2, grosor / 2),
                size = Size(size.width - grosor, size.height - grosor),
                style = Stroke(width = grosor, cap = StrokeCap.Round)
            )
        }
        if (codigo.isBlank()) {
            // Anillo suelto (la lista): dentro va la cuenta atrás, que es lo único
            // que hay que leer. El texto se escala al círculo para que quepa.
            Text(
                text = segundosRestantes.toString(),
                fontSize = (tamano * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = color,
                lineHeight = (tamano * 0.42f).sp
            )
            return@Box
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                codigo.forEachIndexed { indice, digito ->
                    AnimatedContent(
                        targetState = digito,
                        transitionSpec = {
                            (slideInVertically { alto -> alto } togetherWith slideOutVertically { alto -> -alto })
                        },
                        label = "digito$indice"
                    ) { valor ->
                        Text(text = valor.toString(), style = EstiloMono, color = TextoPrincipal)
                    }
                }
            }
            Text("${segundosRestantes}s", style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
        }
    }
}

/** Puerta de bóveda: círculos concéntricos que rotan y se separan al desbloquear. */
@Composable
fun PuertaBoveda(abierta: Boolean, modifier: Modifier = Modifier, tamano: Int = 200) {
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(abierta) {
        if (abierta) {
            progreso.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow))
        } else {
            progreso.snapTo(0f)
        }
    }
    val transicion = rememberInfiniteTransition(label = "giroBoveda")
    val giro by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "giro"
    )
    Box(modifier = modifier.size(tamano.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(tamano.dp)) {
            val centro = Offset(size.width / 2, size.height / 2)
            val radioBase = size.minDimension / 2
            val p = progreso.value
            for (anillo in 0..2) {
                val separacion = p * (anillo + 1) * radioBase * 0.16f
                val radio = radioBase * (1f - anillo * 0.22f) - separacion
                if (radio <= 0f) continue
                val grosor = (10 - anillo * 2).dp.toPx()
                val alpha = 1f - p * 0.55f
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Ambar.copy(alpha = alpha),
                            AmbarFuerte.copy(alpha = alpha * 0.5f),
                            Ambar.copy(alpha = alpha)
                        )
                    ),
                    startAngle = giro * (if (anillo % 2 == 0) 1f else -1f) + p * 180f,
                    sweepAngle = 250f - anillo * 40f,
                    useCenter = false,
                    topLeft = Offset(centro.x - radio, centro.y - radio),
                    size = Size(radio * 2, radio * 2),
                    style = Stroke(width = grosor)
                )
            }
            val radioNucleo = radioBase * 0.2f * (1f + p * 0.4f)
            drawCircle(
                brush = Brush.linearGradient(listOf(Ambar, AmbarFuerte)),
                radius = radioNucleo,
                center = centro,
                alpha = 1f - p * 0.3f
            )
        }
    }
}

@Composable
fun IlustracionVacio(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val centro = Offset(size.width / 2, size.height / 2)
            drawCircle(color = Borde, radius = size.minDimension / 2.2f, center = centro, style = Stroke(width = 6.dp.toPx()))
            drawCircle(
                brush = Brush.linearGradient(listOf(Ambar, AmbarFuerte)),
                radius = size.minDimension / 7f,
                center = centro.copy(y = centro.y - size.minDimension / 14f),
                style = Stroke(width = 8.dp.toPx())
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Ambar, AmbarFuerte)),
                topLeft = Offset(centro.x - size.minDimension / 26f, centro.y + size.minDimension / 30f),
                size = Size(size.minDimension / 13f, size.minDimension / 4.5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
            )
        }
        Text(
            "Tu bóveda está vacía",
            style = MaterialTheme.typography.headlineSmall,
            color = TextoPrincipal,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "Guarda tu primera contraseña con el botón de abajo. Nada saldrá de este teléfono.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSecundario,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun BarraProgresoForja(modifier: Modifier = Modifier) {
    val transicion = rememberInfiniteTransition(label = "forja")
    val fase by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "fase"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(color = Borde, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
            val ancho = size.width * 0.35f
            val x = (size.width + ancho) * fase - ancho
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Ambar, AmbarFuerte, Color.Transparent)),
                topLeft = Offset(x, 0f),
                size = Size(ancho, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}
