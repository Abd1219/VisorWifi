package com.abdapps.visorwifi.ui.components

import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.abdapps.visorwifi.model.LatencyPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.ArrayList
import kotlin.math.abs

/**
 * Componente Compose que envuelve de forma interoperable el LineChart de MPAndroidChart.
 * Configura los conjuntos de datos iniciales y proporciona una función callback para
 * exponer la referencia del componente de vista nativo para su manipulación.
 *
 * @param modifier Modificador Compose de diseño.
 */
@Composable
fun LatencyChart(
    history: List<LatencyPoint>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setupChartStyling(this)
            }
        },
        modifier = modifier,
        update = { chart ->
            populateChartWithHistory(chart, history)
        }
    )
}

/**
 * Define y personaliza el estilo gráfico, interacciones y rejillas del componente LineChart.
 * Configura los ejes, la posición de las leyendas y los formateadores de valores.
 *
 * @param chart Instancia de LineChart (MPAndroidChart) a estilizar.
 */
fun setupChartStyling(chart: LineChart) {
    chart.apply {
        description.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        isScaleXEnabled = true
        isScaleYEnabled = false // Bloqueado el zoom vertical para mantener consistencia de lectura
        setPinchZoom(true)
        setDrawGridBackground(false)
        setBackgroundColor(Color.TRANSPARENT)
        setNoDataText("Inicia el monitoreo para visualizar la gráfica")
        setNoDataTextColor(Color.parseColor("#536371"))

        // Estilización de la leyenda informativa inferior
        legend.apply {
            isEnabled = true
            textColor = Color.parseColor("#8E9AA8")
            textSize = 10f
            form = Legend.LegendForm.LINE
            formSize = 8f
            formLineWidth = 2f
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            xEntrySpace = 16f
        }

        // Estilización del Eje X (Línea de tiempo en segundos transcurridos)
        xAxis.apply {
            textColor = Color.parseColor("#536371")
            textSize = 9f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            // Agrega el sufijo "s" (segundos) a los valores del eje temporal
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}s"
            }
        }

        // Estilización del Eje Y Izquierdo (Latencia en milisegundos)
        axisLeft.apply {
            textColor = Color.parseColor("#536371")
            textSize = 9f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            axisMinimum = 0f // El valor mínimo no puede ser negativo
            spaceTop = 15f
            // Agrega el sufijo " ms" a los valores de latencia
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()} ms"
            }
        }

        // Deshabilitar el eje derecho redundante para limpiar el área útil del gráfico
        axisRight.isEnabled = false
    }
}

/**
 * Carga todo el historial almacenado en el búfer del servicio directamente a la gráfica.
 * Se ejecuta al iniciar la aplicación o al reconectarse al servicio activo.
 *
 * @param chart Instancia de la gráfica.
 * @param history Colección de puntos de medición previos.
 */
fun populateChartWithHistory(chart: LineChart, history: List<LatencyPoint>) {
    Log.d("LatencyChart", "populateChartWithHistory: history size = ${history.size}")
    val lanEntries = ArrayList<Entry>()
    val wanEntries = ArrayList<Entry>()
    val lanJitterEntries = ArrayList<Entry>()
    val wanJitterEntries = ArrayList<Entry>()

    history.forEachIndexed { index, point ->
        val x = index.toFloat()
        // Filtrar mediciones con error/timeouts (-1f) para evitar caídas a cero erróneas en la visualización.
        if (point.lanLatency >= 0) lanEntries.add(Entry(x, point.lanLatency))
        if (point.wanLatency >= 0) wanEntries.add(Entry(x, point.wanLatency))

        // Calcular Jitter punto a punto: abs(currentPing - previousPing)
        if (index > 0) {
            val prev = history[index - 1]
            if (prev.lanLatency >= 0 && point.lanLatency >= 0) {
                lanJitterEntries.add(Entry(x, abs(point.lanLatency - prev.lanLatency)))
            }
            if (prev.wanLatency >= 0 && point.wanLatency >= 0) {
                wanJitterEntries.add(Entry(x, abs(point.wanLatency - prev.wanLatency)))
            }
        }
    }

    if (lanEntries.isEmpty() && wanEntries.isEmpty()) {
        chart.clear()
        chart.invalidate()
        return
    }

    val dataSets = ArrayList<ILineDataSet>()
    // Siempre inyectar los 4 datasets en orden para consistencia de índices y evitar crashes
    dataSets.add(createDataSet(lanEntries, "LAN (Acceso Local)", "#00F0FF"))
    dataSets.add(createDataSet(wanEntries, "WAN (Internet)", "#D946EF"))
    dataSets.add(createJitterDataSet(lanJitterEntries, "#6600F0FF")) // LAN Jitter tenue
    dataSets.add(createJitterDataSet(wanJitterEntries, "#66D946EF")) // WAN Jitter tenue

    val lineData = LineData(dataSets)
    chart.data = lineData
    chart.notifyDataSetChanged()

    // CORRECCIÓN: siempre posicionar el viewport para que la gráfica sea visible.
    if (history.size > 60) {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX((history.size - 1).toFloat())
    } else {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(0f)
    }

    chart.invalidate()
}

/**
 * Fábrica de conjuntos de datos lineales personalizados (LineDataSet) con sombreado degradado
 * y suavizado por curvas de Bézier cúbicas de alta fidelidad.
 *
 * @param entries Lista de coordenadas X e Y correspondientes al dataset.
 * @param label Título o descriptor del dataset.
 * @param colorHex Representación en formato hexadecimal del color de la línea.
 * @return Estructura optimizada [LineDataSet].
 */
fun createDataSet(entries: ArrayList<Entry>, label: String, colorHex: String): LineDataSet {
    return LineDataSet(entries, label).apply {
        color = Color.parseColor(colorHex)
        lineWidth = 2.2f
        setDrawCircles(false) // Deshabilita los puntos marcadores para mejorar el rendimiento
        setDrawCircleHole(false)
        mode = if (entries.size >= 3) LineDataSet.Mode.HORIZONTAL_BEZIER else LineDataSet.Mode.LINEAR
        cubicIntensity = 0.12f
        setDrawValues(false)
        setDrawFilled(true) // Área de sombra bajo la curva
        fillColor = Color.parseColor(colorHex)
        fillAlpha = 15 // Transparencia sutil de 15/255
    }
}

/**
 * Fábrica de conjuntos de datos lineales para Jitter como overlay visual tenue.
 * Diseñado para ser muy discreto y no saturar la UI.
 *
 * @param entries Lista de coordenadas X e Y correspondientes al dataset de Jitter.
 * @param colorHex Representación en formato hexadecimal del color de la línea (incluye canal alfa).
 * @return Estructura optimizada [LineDataSet] para Jitter.
 */
fun createJitterDataSet(entries: ArrayList<Entry>, colorHex: String): LineDataSet {
    return LineDataSet(entries, "").apply {
        color = Color.parseColor(colorHex)
        lineWidth = 1.2f // Trazo fino pero visible
        setDrawCircles(false)
        setDrawCircleHole(false)
        mode = if (entries.size >= 3) LineDataSet.Mode.HORIZONTAL_BEZIER else LineDataSet.Mode.LINEAR
        cubicIntensity = 0.12f
        setDrawValues(false)
        setDrawFilled(false) // Sin relleno para el Jitter
        form = Legend.LegendForm.NONE // Omitir de la leyenda para evitar ruido visual
    }
}

/**
 * Añade un nuevo punto telemétrico de forma segura en tiempo real en la gráfica interactiva.
 *
 * @param chart Instancia de la gráfica.
 * @param point Nuevo punto de latencia registrado.
 */
fun addPointToChart(chart: LineChart, point: LatencyPoint) {
    var data = chart.data

    if (data == null) {
        val lanDataSet = createDataSet(ArrayList(), "LAN (Acceso Local)", "#00F0FF")
        val wanDataSet = createDataSet(ArrayList(), "WAN (Internet)", "#D946EF")
        val lanJitterDataSet = createJitterDataSet(ArrayList(), "#6600F0FF")
        val wanJitterDataSet = createJitterDataSet(ArrayList(), "#66D946EF")
        data = LineData(lanDataSet, wanDataSet, lanJitterDataSet, wanJitterDataSet)
        chart.data = data
    }

    val lanSet = data.getDataSetByIndex(0) as? LineDataSet
    val wanSet = data.getDataSetByIndex(1) as? LineDataSet
    val lanJitterSet = data.getDataSetByIndex(2) as? LineDataSet
    val wanJitterSet = data.getDataSetByIndex(3) as? LineDataSet

    if (lanSet != null && wanSet != null && lanJitterSet != null && wanJitterSet != null) {
        // Resolver de forma segura la mayor coordenada X registrada hasta el momento
        val maxLanX = if (lanSet.entryCount > 0) lanSet.getEntryForIndex(lanSet.entryCount - 1).x else -1f
        val maxWanX = if (wanSet.entryCount > 0) wanSet.getEntryForIndex(wanSet.entryCount - 1).x else -1f
        val nextX = maxOf(maxLanX, maxWanX) + 1f

        // Adicionar únicamente si representa una latencia válida no fallida
        if (point.lanLatency >= 0) {
            data.addEntry(Entry(nextX, point.lanLatency), 0)
            if (lanSet.entryCount > 1) {
                val prevVal = lanSet.getEntryForIndex(lanSet.entryCount - 2).y
                data.addEntry(Entry(nextX, abs(point.lanLatency - prevVal)), 2)
            }
        }
        if (point.wanLatency >= 0) {
            data.addEntry(Entry(nextX, point.wanLatency), 1)
            if (wanSet.entryCount > 1) {
                val prevVal = wanSet.getEntryForIndex(wanSet.entryCount - 2).y
                data.addEntry(Entry(nextX, abs(point.wanLatency - prevVal)), 3)
            }
        }

        // Indicarle a la gráfica que su estructura de datos interna se actualizó
        data.notifyDataChanged()
        chart.notifyDataSetChanged()
        
        // Mantener el scroll dinámico acotado a los últimos 60 segundos
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(nextX)
        chart.invalidate()
    }
}
