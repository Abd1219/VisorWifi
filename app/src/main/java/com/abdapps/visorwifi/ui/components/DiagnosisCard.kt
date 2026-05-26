package com.abdapps.visorwifi.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdapps.visorwifi.model.DiagnosisState
import com.abdapps.visorwifi.model.DiagnosisStatus

// ── Paleta de colores de estado ────────────────────────────────────────────────
private val ColorHealthy  = Color(0xFF00FF66)
private val ColorWarning  = Color(0xFFFFD700)
private val ColorProblem  = Color(0xFFFF3366)
private val ColorLoading  = Color(0xFF536371)
private val ColorSurface  = Color(0xFF0F141C)
private val ColorSurface2 = Color(0xFF121620)
private val ColorBorder   = Color(0xFF263238)
private val ColorLabel    = Color(0xFF536371)
private val ColorSubtext  = Color(0xFF8E9AA8)

/**
 * Componente principal del bloque "Diagnóstico de Red".
 *
 * Renderiza uno de los cuatro estados visuales definidos por [DiagnosisState]:
 * - [DiagnosisState.Loading]  → Animación pulsante mientras se recolectan datos.
 * - [DiagnosisState.Healthy]  → Indicador verde "Red saludable".
 * - [DiagnosisState.Warning]  → Indicador amarillo con evidencias y recomendación.
 * - [DiagnosisState.Problem]  → Indicador rojo con evidencias y recomendación.
 *
 * El contenido interno hace transición animada (crossfade) entre estados para
 * una experiencia fluida y no disruptiva al usuario.
 *
 * @param state  Estado actual del diagnóstico proveniente de [NetworkDiagnosisViewModel].
 * @param modifier Modificador Compose de diseño opcional.
 */
@Composable
fun DiagnosisCard(
    state: DiagnosisState,
    modifier: Modifier = Modifier
) {
    // Color de acento dinámico según el estado actual
    val accentColor = when (state.status) {
        DiagnosisStatus.LOADING -> ColorLoading
        DiagnosisStatus.HEALTHY -> ColorHealthy
        DiagnosisStatus.WARNING -> ColorWarning
        DiagnosisStatus.PROBLEM -> ColorProblem
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.30f),
                        accentColor.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ColorSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Cabecera ──────────────────────────────────────────────────────
            DiagnosisHeader(accentColor = accentColor)

            Spacer(modifier = Modifier.height(14.dp))

            // ── Cuerpo animado — transición crossfade entre estados ───────────
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                label = "diagnosis_content"
            ) { targetState ->
                when (targetState.status) {
                    DiagnosisStatus.LOADING -> DiagnosisLoadingContent()
                    DiagnosisStatus.HEALTHY -> DiagnosisHealthyContent()
                    DiagnosisStatus.WARNING -> DiagnosisResultContent(
                        accentColor    = ColorWarning,
                        statusLabel    = "CALIDAD INESTABLE",
                        title          = targetState.title,
                        confidence     = targetState.confidence,
                        evidences      = targetState.evidences,
                        recommendation = targetState.recommendation
                    )
                    DiagnosisStatus.PROBLEM -> DiagnosisResultContent(
                        accentColor    = ColorProblem,
                        statusLabel    = "PROBLEMA DETECTADO",
                        title          = targetState.title,
                        confidence     = targetState.confidence,
                        evidences      = targetState.evidences,
                        recommendation = targetState.recommendation
                    )
                }
            }
        }
    }
}

// ── Sub-composables internos ───────────────────────────────────────────────────

/**
 * Cabecera común de la card: etiqueta de sección + ícono fijo.
 */
@Composable
private fun DiagnosisHeader(accentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "DIAGNÓSTICO DE RED",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ColorLabel,
            letterSpacing = 1.sp
        )
        // Ícono de diagnóstico — pastilla con color de acento
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "🩺 AI",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

/**
 * Contenido del estado [DiagnosisState.Loading]:
 * Indicador pulsante + mensaje de espera.
 */
@Composable
private fun DiagnosisLoadingContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Punto pulsante de carga
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(ColorLoading)
        )
        Column {
            Text(
                text = "Recolectando información…",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Se necesitan al menos 10 muestras para el análisis",
                fontSize = 11.sp,
                color = ColorSubtext,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Contenido del estado [DiagnosisState.Healthy]:
 * Indicador verde con mensaje de confirmación de buena salud.
 */
@Composable
private fun DiagnosisHealthyContent() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorHealthy.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        // Indicador circular verde
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(ColorHealthy.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✓", fontSize = 18.sp, color = ColorHealthy, fontWeight = FontWeight.Black)
        }
        Column {
            Text(
                text = "Red saludable",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ColorHealthy
            )
            Text(
                text = "Todos los parámetros dentro del rango normal",
                fontSize = 11.sp,
                color = ColorSubtext,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Contenido compartido para los estados [DiagnosisState.Warning] y [DiagnosisState.Problem].
 * Muestra: indicador de estado, título, porcentaje de confianza, evidencias y recomendación.
 *
 * @param accentColor    Color de acento del estado (amarillo o rojo).
 * @param statusLabel    Etiqueta corta en mayúsculas del estado.
 * @param title          Texto principal del diagnóstico.
 * @param confidence     Porcentaje de confianza (0–100).
 * @param evidences      Lista de evidencias detectadas.
 * @param recommendation Acción sugerida al usuario.
 */
@Composable
private fun DiagnosisResultContent(
    accentColor: Color,
    statusLabel: String,
    title: String,
    confidence: Int,
    evidences: List<String>,
    recommendation: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Fila de estado: indicador + etiqueta + confianza ──────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Círculo de estado con color de acento
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = statusLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.8.sp
                )
            }
            // Porcentaje de confianza
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "$confidence% confianza",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Título del diagnóstico ────────────────────────────────────────────
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 22.sp
        )

        HorizontalDivider(color = ColorBorder)

        // ── Evidencias detectadas ─────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "EVIDENCIAS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ColorLabel,
                letterSpacing = 1.sp
            )
            evidences.forEach { evidence ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.7f))
                    )
                    Text(
                        text = evidence,
                        fontSize = 12.sp,
                        color = ColorSubtext,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // ── Recomendación ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111827))
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "💡", fontSize = 14.sp)
            Column {
                Text(
                    text = "RECOMENDACIÓN",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorLabel,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recommendation,
                    fontSize = 12.sp,
                    color = Color.White,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
