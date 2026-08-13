package com.abdapps.visorwifi.model

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Clase encargada de generar un archivo PDF profesional a partir de los datos telemétricos y del técnico.
 * No utiliza capturas de pantalla, sino que dibuja el documento de manera programática en un Canvas de PDF,
 * incluyendo la renderización de las gráficas de MPAndroidChart fuera de pantalla (off-screen).
 */
object PdfGenerator {

    // Tamaño de página A4 estándar en puntos (72 puntos por pulgada)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 45f

    /**
     * Genera el PDF en el directorio de caché de la aplicación y retorna el archivo creado.
     *
     * @param context Contexto de la aplicación.
     * @param data Datos consolidados a escribir en el reporte.
     * @return Archivo PDF generado.
     */
    fun generateReport(context: Context, data: ReportData): File {
        val pdfDocument = PdfDocument()
        
        // --- PÁGINA 1: Encabezado, Resumen, Indicadores, Diagnóstico y Notas ---
        val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas
        drawPage1(context, canvas1, data)
        pdfDocument.finishPage(page1)

        // --- PÁGINA 2: Gráficas de Evidencia, Eventos de Red y Firmas ---
        val pageInfo2 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas
        drawPage2(context, canvas2, data)
        pdfDocument.finishPage(page2)

        // Crear directorio temporal de reportes si no existe
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        // Crear archivo final con marca de tiempo para evitar colisiones
        val timestamp = System.currentTimeMillis()
        val file = File(reportsDir, "reporte_visorwifi_$timestamp.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()

        return file
    }

    /**
     * Dibuja los elementos de la primera página del reporte.
     */
    private fun drawPage1(context: Context, canvas: Canvas, data: ReportData) {
        var currentY = MARGIN

        // 1. Cabecera (Header)
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A") // Slate Oscuro
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("VISOR WIFI", MARGIN, currentY + 16f, titlePaint)

        val reportTypePaint = TextPaint().apply {
            color = Color.parseColor("#0284C7") // Azul Tech
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(data.reportType.uppercase(Locale.getDefault()), MARGIN, currentY + 32f, reportTypePaint)

        // Metadatos de la cabecera (Fecha, Técnico, Sitio)
        val metaPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val rightAlignX = PAGE_WIDTH - MARGIN - 160f
        canvas.drawText("Fecha/Hora: ${data.dateTime}", rightAlignX, currentY + 14f, metaPaint)
        canvas.drawText("Técnico: ${data.technicianName}", rightAlignX, currentY + 26f, metaPaint)
        canvas.drawText("Sitio: ${data.siteName}", rightAlignX, currentY + 38f, metaPaint)

        currentY += 52f

        // Línea divisoria elegante
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
        currentY += 16f

        // 2. Resumen Ejecutivo (Ejecutive Summary Card)
        val cardPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        // Elegir color según el estado del PERIODO analizado (no el estado instantáneo)
        // Mapeo intencional: PeriodReportStatus ≠ DiagnosisStatus
        val (statusText, statusColor, cardBg) = when (data.periodStatus) {
            PeriodReportStatus.OPTIMAL   -> Triple("ESTADO DEL PERIODO: ÓPTIMO",    "#16A34A", "#F0FDF4") // Verde
            PeriodReportStatus.DEGRADED  -> Triple("ESTADO DEL PERIODO: DEGRADADO", "#EA580C", "#FFF7ED") // Naranja
            PeriodReportStatus.UNSTABLE  -> Triple("ESTADO DEL PERIODO: INESTABLE", "#D97706", "#FFFBEB") // Ámbar
            PeriodReportStatus.CRITICAL  -> Triple("ESTADO DEL PERIODO: CRÍTICO",   "#DC2626", "#FEF2F2") // Rojo
        }

        cardPaint.color = Color.parseColor(cardBg)
        cardBorderPaint.color = Color.parseColor(statusColor)

        // Dibujar tarjeta de estado general
        val cardHeight = 100f
        val rect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + cardHeight)
        canvas.drawRoundRect(rect, 6f, 6f, cardPaint)
        canvas.drawRoundRect(rect, 6f, 6f, cardBorderPaint)

        // Texto de estado (bandera de periodo)
        val cardTextTitlePaint = TextPaint().apply {
            color = Color.parseColor(statusColor)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(statusText, MARGIN + 12f, currentY + 18f, cardTextTitlePaint)

        // Rango temporal del periodo analizado
        val periodSdf = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val periodStartStr = periodSdf.format(java.util.Date(data.periodAnalysis.periodStartMs))
        val periodEndStr   = periodSdf.format(java.util.Date(data.periodAnalysis.periodEndMs))
        val durationSecs   = (data.periodAnalysis.periodEndMs - data.periodAnalysis.periodStartMs) / 1000L
        val durationStr    = when {
            durationSecs < 60  -> "${durationSecs}s"
            durationSecs < 3600 -> "${durationSecs / 60}m ${durationSecs % 60}s"
            else               -> "${durationSecs / 3600}h ${(durationSecs % 3600) / 60}m"
        }
        val periodLabelPaint = TextPaint().apply {
            color = Color.parseColor("#64748B")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }
        canvas.drawText(
            "Periodo analizado: $periodStartStr – $periodEndStr  (≈ $durationStr, ${data.periodAnalysis.sampleCount} muestras)",
            MARGIN + 12f, currentY + 30f, periodLabelPaint
        )

        // Score de estabilidad
        val scoreLabel = "Score de estabilidad del periodo: ${data.periodAnalysis.stabilityScore}/100"
        val scoreColor = when {
            data.periodAnalysis.stabilityScore >= 80 -> "#16A34A"
            data.periodAnalysis.stabilityScore >= 60 -> "#EA580C"
            data.periodAnalysis.stabilityScore >= 40 -> "#D97706"
            else                                     -> "#DC2626"
        }
        val scorePaint = TextPaint().apply {
            color = Color.parseColor(scoreColor)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(scoreLabel, MARGIN + 12f, currentY + 42f, scorePaint)

        // Conclusión ejecutiva (texto libre, debajo del score)
        val cardTextPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val textWidth = PAGE_WIDTH - (MARGIN * 2) - 24f
        drawTextWrapped(
            canvas = canvas,
            text = data.executiveConclusion,
            x = MARGIN + 12f,
            y = currentY + 52f,
            width = textWidth,
            paint = cardTextPaint
        )

        // Nota técnica aclaratoria (pie de la tarjeta)
        val notePaint = TextPaint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 6.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }
        canvas.drawText(
            "⚠️ Este estado resume el comportamiento del enlace durante el periodo completo, no el último instante.",
            MARGIN + 12f, currentY + cardHeight - 6f, notePaint
        )

        currentY += cardHeight + 18f

        // 3. Tabla de Indicadores Clave
        val secTitlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Métricas del Periodo Analizado", MARGIN, currentY, secTitlePaint)
        currentY += 8f

        val colWidths = listOf(160f, 160f, 185f)
        val rowHeight = 18f
        val thBgPaint = Paint().apply { color = Color.parseColor("#F1F5F9") }
        val tdBgPaintEven = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val tdBgPaintOdd = Paint().apply { color = Color.WHITE }

        val tableHeaderPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val tableCellPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        // Encabezados de tabla
        drawTableRow(
            canvas, MARGIN, currentY, colWidths, rowHeight,
            listOf("Métrica Evaluada", "Valor Obtenido", "Rango de Referencia / Estado"),
            tableHeaderPaint, thBgPaint
        )
        currentY += rowHeight

        // ─── Tabla de Indicadores de PERIODO ─────────────────────────────────────
        // Nota: lanLatencyAvgMs y wanLatencyAvgMs en ReportData ya provienen de periodAnalysis.
        val rssiText = "${data.rssiDbm} dBm (${rssiToQuality(data.rssiDbm)})"
        val lanText  = if (data.lanLatencyAvgMs >= 0) String.format(Locale.US, "%.2f ms", data.lanLatencyAvgMs) else "Timeout"
        val wanText  = if (data.wanLatencyAvgMs >= 0) String.format(Locale.US, "%.2f ms", data.wanLatencyAvgMs) else "Timeout"
        val phyText  = "${data.phyRateMbps} Mbps"
        val p        = data.periodAnalysis  // alias corto para legibilidad

        val rows = listOf(
            listOf("Latencia LAN prom. (Periodo)",  lanText,
                "< 15 ms (Óptimo) | > 50 ms (Inestable)"),
            listOf("Picos LAN > 50 ms (Periodo)",   "${p.lanSpikeCount} eventos",
                "0 (Óptimo) | 1-4 (Degradado) | ≥5 (Crítico)"),
            listOf("Latencia WAN prom. (Periodo)",  wanText,
                "< 100 ms (Estable) | > 200 ms (Crítico)"),
            listOf("Picos WAN > 200 ms (Periodo)",  "${p.latencySpikeCount} eventos",
                "0-1 (Óptimo) | 2-4 (Degradado) | ≥10 (Crítico)"),
            listOf("Pérdida de Paquetes WAN",        String.format(Locale.US, "%.1f%%", p.packetLossPercent),
                "< 2% (Óptimo) | > 10% (Crítico)"),
            listOf("Jitter WAN prom. (Periodo)",    String.format(Locale.US, "%.1f ms", p.avgJitterMs),
                "< 15 ms (Óptimo) | > 50 ms (Inestable)"),
            listOf("Jitter WAN máx. (Periodo)",     String.format(Locale.US, "%.1f ms", p.maxJitterMs),
                "Referencia de estabilidad temporal"),
            listOf("RSSI prom. en el periodo",       String.format(Locale.US, "%.1f dBm", p.rssiAvg),
                "-30 a -65 dBm (Excelente) | < -80 dBm (Débil)"),
            listOf("RSSI mínimo en el periodo",      "${p.rssiMin.toInt()} dBm",
                "Peor señal registrada; impacto en estabilidad"),
            listOf("Varianza de señal RSSI",         String.format(Locale.US, "%.1f dBm²", p.rssiVariance),
                "< 10 (Estable) | > 30 (Fluctuante)"),
            listOf("Velocidad Física del Enlace",    phyText,
                "Depende del estándar (Máx disponible)"),
            listOf("Banda Wi-Fi Activa",             data.wifiBand,
                "2.4 GHz (Cobertura) / 5 u 6 GHz (Velocidad)"),
            listOf("Eventos de Roaming (Periodo)",   "${p.roamingEventsCount} roaming | ${p.ssidChangeCount} cambios de SSID",
                "0 (Óptimo) | ≥5 (Crítico para VoIP/Gaming)")
        )

        rows.forEachIndexed { index, row ->
            val bg = if (index % 2 == 0) tdBgPaintEven else tdBgPaintOdd
            drawTableRow(canvas, MARGIN, currentY, colWidths, rowHeight, row, tableCellPaint, bg)
            currentY += rowHeight
        }

        currentY += 18f

        // 4. Diagnóstico Técnico Detallado
        canvas.drawText("Diagnóstico Automático Basado en Reglas e IA", MARGIN, currentY, secTitlePaint)
        currentY += 8f

        // Caja de Diagnóstico
        val diagRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 120f)
        val diagBg = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val diagBorder = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(diagRect, 4f, 4f, diagBg)
        canvas.drawRoundRect(diagRect, 4f, 4f, diagBorder)

        // Diagnóstico Principal
        val diagTitlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Resultado del Análisis: ${data.diagnosisTitle} (Confianza: ${data.confidence}%)", MARGIN + 12f, currentY + 18f, diagTitlePaint)

        // Evidencias evaluadas
        var evidenceY = currentY + 30f
        val evidencePaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        
        canvas.drawText("Evidencias detectadas en la sesión:", MARGIN + 12f, evidenceY, diagTitlePaint)
        evidenceY += 10f
        
        data.evidences.take(3).forEach { ev ->
            canvas.drawCircle(MARGIN + 18f, evidenceY - 3f, 2f, Paint().apply { color = Color.parseColor("#0284C7") })
            canvas.drawText(ev, MARGIN + 26f, evidenceY, evidencePaint)
            evidenceY += 11f
        }

        // Recomendación técnica (Caja resaltada dentro del diagnóstico)
        val recBoxRect = RectF(MARGIN + 12f, currentY + 80f, PAGE_WIDTH - MARGIN - 12f, currentY + 110f)
        val recBoxBg = Paint().apply { color = Color.parseColor("#EFF6FF") } // Soft blue
        val recBoxBorder = Paint().apply {
            color = Color.parseColor("#BFDBFE")
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        canvas.drawRoundRect(recBoxRect, 3f, 3f, recBoxBg)
        canvas.drawRoundRect(recBoxRect, 3f, 3f, recBoxBorder)

        val recPaint = TextPaint().apply {
            color = Color.parseColor("#1E40AF") // Navy Blue
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        drawTextWrapped(
            canvas = canvas,
            text = data.recommendation,
            x = MARGIN + 20f,
            y = currentY + 88f,
            width = textWidth - 16f,
            paint = recPaint
        )

        currentY += 138f

        // 5. Observaciones Opcionales del Técnico
        if (!data.technicianNotes.isNullOrBlank()) {
            canvas.drawText("Observaciones y Anotaciones de Campo", MARGIN, currentY, secTitlePaint)
            currentY += 8f

            val noteRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 58f)
            canvas.drawRoundRect(noteRect, 4f, 4f, Paint().apply { color = Color.parseColor("#FCFDFD") })
            canvas.drawRoundRect(noteRect, 4f, 4f, diagBorder)

            val notesPaint = TextPaint().apply {
                color = Color.parseColor("#1E293B")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                isAntiAlias = true
            }
            drawTextWrapped(
                canvas = canvas,
                text = data.technicianNotes,
                x = MARGIN + 12f,
                y = currentY + 12f,
                width = textWidth,
                paint = notesPaint
            )
        }

        // Pie de Página 1
        val footerPaint = TextPaint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(
            "Reporte generado por Visor WiFi • El estado refleja el periodo analizado completo, no un instante puntual.",
            MARGIN, PAGE_HEIGHT - 30f, footerPaint
        )
        canvas.drawText("Página 1 de 2", PAGE_WIDTH - MARGIN - 50f, PAGE_HEIGHT - 30f, footerPaint)
    }

    /**
     * Dibuja los elementos de la segunda página del reporte.
     */
    private fun drawPage2(context: Context, canvas: Canvas, data: ReportData) {
        var currentY = MARGIN

        // Cabecera compacta
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("VISOR WIFI — EVIDENCIA GRÁFICA Y EVENTOS DE RED", MARGIN, currentY + 12f, titlePaint)
        
        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(MARGIN, currentY + 18f, PAGE_WIDTH - MARGIN, currentY + 18f, linePaint)
        currentY += 32f

        // 1. Gráficas de Evidencia (Historial de Latencia y RSSI)
        val secTitlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Historial Gráfico de Conexión (Últimos 10 Minutos)", MARGIN, currentY, secTitlePaint)
        currentY += 12f

        // Dimensiones físicas de la gráfica a insertar en el PDF (480 x 140 pixeles)
        val chartWidth = 505
        val chartHeight = 145

        // Generar y dibujar Gráfica de Latencia (LAN vs WAN)
        try {
            val latencyBitmap = generateChartBitmap(context, data.latencyHistory, isRssi = false, width = chartWidth, height = chartHeight)
            canvas.drawBitmap(latencyBitmap, MARGIN, currentY, null)
            
            // Título de la gráfica
            val graphLabelPaint = TextPaint().apply {
                color = Color.parseColor("#475569")
                textSize = 7.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Fig A. Historial de Latencias LAN (Acceso Local) y WAN (Acceso a Internet)", MARGIN + 4f, currentY + chartHeight + 10f, graphLabelPaint)
            currentY += chartHeight + 22f
        } catch (e: Exception) {
            val errorPaint = TextPaint().apply { color = Color.RED; textSize = 9f }
            canvas.drawText("Error al renderizar gráfico de latencia: ${e.message}", MARGIN, currentY + 20f, errorPaint)
            currentY += 50f
        }

        // Generar y dibujar Gráfica de RSSI (Intensidad de Señal)
        try {
            val rssiBitmap = generateChartBitmap(context, data.latencyHistory, isRssi = true, width = chartWidth, height = chartHeight)
            canvas.drawBitmap(rssiBitmap, MARGIN, currentY, null)
            
            val graphLabelPaint = TextPaint().apply {
                color = Color.parseColor("#475569")
                textSize = 7.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("Fig B. Historial de Intensidad de Señal Inalámbrica (RSSI en dBm)", MARGIN + 4f, currentY + chartHeight + 10f, graphLabelPaint)
            currentY += chartHeight + 26f
        } catch (e: Exception) {
            val errorPaint = TextPaint().apply { color = Color.RED; textSize = 9f }
            canvas.drawText("Error al renderizar gráfico de RSSI: ${e.message}", MARGIN, currentY + 20f, errorPaint)
            currentY += 50f
        }

        // 2. Registro de Eventos de Red (Roaming e Itinerancia)
        canvas.drawText("Registro de Eventos y Cambios de Red", MARGIN, currentY, secTitlePaint)
        currentY += 8f

        val eventsBoxRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 98f)
        val boxBg = Paint().apply { color = Color.parseColor("#FCFDFD") }
        val boxBorder = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(eventsBoxRect, 4f, 4f, boxBg)
        canvas.drawRoundRect(eventsBoxRect, 4f, 4f, boxBorder)

        val eventPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        var eventY = currentY + 14f
        if (data.networkEvents.isEmpty()) {
            canvas.drawText("No se detectaron eventos significativos de red (roaming o cambio de SSID) durante el monitoreo.", MARGIN + 12f, eventY, TextPaint(eventPaint).apply { typeface = Typeface.DEFAULT })
        } else {
            // Mostrar últimos 6 eventos para no desbordar la caja
            data.networkEvents.takeLast(7).forEach { event ->
                canvas.drawText(event, MARGIN + 12f, eventY, eventPaint)
                eventY += 12f
            }
        }

        currentY += 114f

        // 3. Firmas
        canvas.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, linePaint)
        currentY += 16f

        val sigTitlePaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val sigLinePaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = 1f
        }

        // Firma del Técnico (Izquierda)
        val leftSigX = MARGIN + 40f
        val sigWidth = 140f
        canvas.drawLine(leftSigX, currentY + 50f, leftSigX + sigWidth, currentY + 50f, sigLinePaint)
        canvas.drawText("Firma del Técnico", leftSigX + 26f, currentY + 62f, sigTitlePaint)
        canvas.drawText("Nombre: ${data.technicianName}", leftSigX + 4f, currentY + 74f, TextPaint(sigTitlePaint).apply { typeface = Typeface.DEFAULT; textSize = 8f })

        // Dibujar el bitmap de la firma si existe
        data.technicianSignature?.let { signatureBitmap ->
            val destRect = RectF(leftSigX + 10f, currentY + 5f, leftSigX + sigWidth - 10f, currentY + 45f)
            canvas.drawBitmap(signatureBitmap, null, destRect, null)
        }

        // Firma del Cliente / Sitio (Derecha)
        val rightSigX = PAGE_WIDTH - MARGIN - sigWidth - 40f
        canvas.drawLine(rightSigX, currentY + 50f, rightSigX + sigWidth, currentY + 50f, sigLinePaint)
        canvas.drawText("Firma del Cliente", rightSigX + 28f, currentY + 62f, sigTitlePaint)
        canvas.drawText("Sitio: ${data.siteName}", rightSigX + 4f, currentY + 74f, TextPaint(sigTitlePaint).apply { typeface = Typeface.DEFAULT; textSize = 8f })

        // Pie de Página 2
        val footerPaint = TextPaint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Visor WiFi - Evidencia de Red Confiable y Segura", MARGIN, PAGE_HEIGHT - 30f, footerPaint)
        canvas.drawText("Página 2 de 2", PAGE_WIDTH - MARGIN - 50f, PAGE_HEIGHT - 30f, footerPaint)
    }

    /**
     * Dibuja una celda de tabla en el Canvas del PDF.
     */
    private fun drawTableRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        colWidths: List<Float>,
        rowHeight: Float,
        values: List<String>,
        textPaint: Paint,
        bgPaint: Paint? = null
    ) {
        var currentX = x
        
        // Fondo
        if (bgPaint != null) {
            val totalWidth = colWidths.sum()
            canvas.drawRect(x, y, x + totalWidth, y + rowHeight, bgPaint)
        }
        
        // Bordes y textos
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        
        for (i in values.indices) {
            val width = colWidths[i]
            canvas.drawRect(currentX, y, currentX + width, y + rowHeight, borderPaint)
            
            // Centrado vertical básico
            val text = values[i]
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            val textY = y + (rowHeight + textBounds.height()) / 2f - 1.5f
            val textX = currentX + 6f // Margen interno de celda
            
            // Truncar texto si excede el ancho de columna
            var safeText = text
            val textLimitWidth = width - 12f
            if (textPaint.measureText(text) > textLimitWidth) {
                var len = text.length
                while (len > 0 && textPaint.measureText(text.substring(0, len) + "...") > textLimitWidth) {
                    len--
                }
                safeText = if (len > 0) text.substring(0, len) + "..." else ""
            }
            
            canvas.drawText(safeText, textX, textY, textPaint)
            currentX += width
        }
    }

    /**
     * Dibuja un texto que se auto-ajusta en múltiples renglones (Word Wrap) usando StaticLayout.
     */
    private fun drawTextWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        paint: TextPaint
    ): Float {
        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                width.toInt(),
                Layout.Alignment.ALIGN_NORMAL,
                1.15f,
                0f,
                false
            )
        }
        
        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()
        
        return staticLayout.height.toFloat()
    }

    /**
     * Convierte la frecuencia en una descripción legible de la banda WiFi.
     */
    private fun frequencyToBand(frequencyMHz: Int): String {
        return when {
            frequencyMHz in 2400..2500 -> "2.4 GHz"
            frequencyMHz in 5150..5850 -> "5 GHz"
            frequencyMHz >= 5945 -> "6 GHz"
            else -> "Desconocida"
        }
    }

    /**
     * Clasifica cualitativamente el RSSI.
     */
    private fun rssiToQuality(rssi: Int): String {
        return when {
            rssi >= -50 -> "Excelente"
            rssi >= -60 -> "Muy Buena"
            rssi >= -70 -> "Buena"
            rssi >= -80 -> "Regular"
            rssi > -127 -> "Débil"
            else -> "Sin Señal"
        }
    }

    /**
     * Genera dinámicamente un Bitmap a partir de una instancia de LineChart en memoria.
     */
    private fun generateChartBitmap(
        context: Context,
        history: List<LatencyPoint>,
        isRssi: Boolean,
        width: Int,
        height: Int
    ): Bitmap {
        val chart = LineChart(context)
        chart.layoutParams = ViewGroup.LayoutParams(width, height)
        
        // Estilización de alta calidad orientada a impresión en PDF (Fondo blanco, cuadrícula tenue)
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.WHITE)
        chart.setNoDataText("Sin datos históricos para graficar")
        chart.setNoDataTextColor(Color.GRAY)

        // Leyenda
        chart.legend.apply {
            isEnabled = true
            textColor = Color.parseColor("#334155")
            textSize = 8f
            form = Legend.LegendForm.LINE
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
        }

        // Eje X
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.parseColor("#475569")
        xAxis.textSize = 7.5f
        xAxis.gridColor = Color.parseColor("#E2E8F0")
        xAxis.setDrawGridLines(true)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "${value.toInt()}s"
        }

        // Eje Y
        val axisLeft = chart.axisLeft
        axisLeft.textColor = Color.parseColor("#475569")
        axisLeft.textSize = 7.5f
        axisLeft.gridColor = Color.parseColor("#E2E8F0")
        axisLeft.setDrawGridLines(true)
        
        if (isRssi) {
            axisLeft.axisMinimum = -100f
            axisLeft.axisMaximum = -30f
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()} dBm"
            }
        } else {
            axisLeft.axisMinimum = 0f
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()} ms"
            }
        }

        chart.axisRight.isEnabled = false

        // Inyección de Datos
        if (isRssi) {
            val rssiEntries = ArrayList<Entry>()
            history.forEachIndexed { index, point ->
                if (point.rssi > -127) {
                    rssiEntries.add(Entry(index.toFloat(), point.rssi.toFloat()))
                }
            }
            if (rssiEntries.isNotEmpty()) {
                val set = LineDataSet(rssiEntries, "Intensidad Señal (RSSI)").apply {
                    color = Color.parseColor("#0284C7")
                    lineWidth = 1.8f
                    setDrawCircles(false)
                    setDrawValues(false)
                    setDrawFilled(true)
                    fillColor = Color.parseColor("#0284C7")
                    fillAlpha = 22
                }
                chart.data = LineData(set)
            }
        } else {
            val lanEntries = ArrayList<Entry>()
            val wanEntries = ArrayList<Entry>()
            history.forEachIndexed { index, point ->
                val x = index.toFloat()
                if (point.lanLatency >= 0) lanEntries.add(Entry(x, point.lanLatency))
                if (point.wanLatency >= 0) wanEntries.add(Entry(x, point.wanLatency))
            }

            if (lanEntries.isNotEmpty() || wanEntries.isNotEmpty()) {
                val dataSets = ArrayList<LineDataSet>()
                if (lanEntries.isNotEmpty()) {
                    dataSets.add(LineDataSet(lanEntries, "LAN (Acceso Local)").apply {
                        color = Color.parseColor("#0D9488") // Teal
                        lineWidth = 1.8f
                        setDrawCircles(false)
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = Color.parseColor("#0D9488")
                        fillAlpha = 15
                    })
                }
                if (wanEntries.isNotEmpty()) {
                    dataSets.add(LineDataSet(wanEntries, "WAN (Internet)").apply {
                        color = Color.parseColor("#D946EF") // Magenta
                        lineWidth = 1.8f
                        setDrawCircles(false)
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = Color.parseColor("#D946EF")
                        fillAlpha = 15
                    })
                }
                chart.data = LineData(dataSets.toList())
            }
        }

        // Layout off-screen forzado
        chart.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        chart.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        chart.draw(canvas)

        return bitmap
    }
}
