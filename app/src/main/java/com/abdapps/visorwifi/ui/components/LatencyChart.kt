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

/**
 * Componente Compose que envuelve de forma interoperable el LineChart de MPAndroidChart.
 * Configura los conjuntos de datos iniciales y proporciona una función callback para
 * exponer la referencia del componente de vista nativo para su manipulación.
 *
 * @param modifier Modificador Compose de diseño.
 * @param onChartRefReady Callback invocado cuando la referencia del gráfico se crea o actualiza.
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

    history.forEachIndexed { index, point ->
        val x = index.toFloat()
        // Filtrar mediciones con error/timeouts (-1f) para evitar caídas a cero erróneas en la visualización.
        // CORRECCIÓN: Si AMBOS valores son -1 aún así necesitamos avanzar el índice X;
        // de lo contrario aceptamos puntos individuales (uno puede fallar y el otro ser válido).
        if (point.lanLatency >= 0) lanEntries.add(Entry(x, point.lanLatency))
        if (point.wanLatency >= 0) wanEntries.add(Entry(x, point.wanLatency))
    }

    val dataSets = ArrayList<ILineDataSet>()
    if (lanEntries.isNotEmpty()) {
        dataSets.add(createDataSet(lanEntries, "LAN (Acceso Local)", "#00F0FF"))
    }
    if (wanEntries.isNotEmpty()) {
        dataSets.add(createDataSet(wanEntries, "WAN (Internet)", "#D946EF"))
    }

    if (dataSets.isEmpty()) {
        chart.clear()
        chart.invalidate()
        return
    }

    val lineData = LineData(dataSets)
    chart.data = lineData
    chart.notifyDataSetChanged()

    // CORRECCIÓN: siempre posicionar el viewport para que la gráfica sea visible.
    // Si hay más de 60 puntos, mostrar la ventana de los últimos 60 y hacer scroll al final.
    // Si hay pocos puntos, posicionar en el origen (x=0) para que se vean desde el inicio.
    if (history.size > 60) {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX((history.size - 1).toFloat())
    } else {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(0f)  // CORRECCIÓN: sin esto el viewport puede quedar desplazado y vacío
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
        // Usamos HORIZONTAL_BEZIER que es más estable ante saltos/picos de latencia.
        // Fallback a LINEAR si hay menos de 3 puntos para prevenir errores de rendering en la curva.
        mode = if (entries.size >= 3) LineDataSet.Mode.HORIZONTAL_BEZIER else LineDataSet.Mode.LINEAR
        cubicIntensity = 0.12f
        setDrawValues(false)
        setDrawFilled(true) // Área de sombra bajo la curva
        fillColor = Color.parseColor(colorHex)
        fillAlpha = 15 // Transparencia sutil de 15/255
    }
}

/**
 * Añade un nuevo punto telemétrico de forma segura en tiempo real en la gráfica interactiva.
 *
 * CORRECCIÓN CRÍTICA DE INTEGRIDAD:
 * Para prevenir el bloqueo o cuelgue inesperado en el dibujado de MPAndroidChart provocado por
 * entradas desordenadas en el Eje X (lo cual ocurre si una corrutina se suspende o si se registran
 * timeouts), calculamos la coordenada X de manera estrictamente creciente: `maxOf(maxLanX, maxWanX) + 1f`.
 * Esto garantiza que, incluso si no se insertó una muestra anterior (ej: LAN fallida y WAN exitosa),
 * el índice de abscisas X se mantenga en orden estrictamente ascendente como exige la librería.
 *
 * @param chart Instancia de la gráfica.
 * @param point Nuevo punto de latencia registrado.
 */
fun addPointToChart(chart: LineChart, point: LatencyPoint) {
    var data = chart.data

    if (data == null) {
        val lanDataSet = createDataSet(ArrayList(), "LAN (Acceso Local)", "#00F0FF")
        val wanDataSet = createDataSet(ArrayList(), "WAN (Internet)", "#D946EF")
        data = LineData(lanDataSet, wanDataSet)
        chart.data = data
    }

    val lanSet = data.getDataSetByIndex(0) as? LineDataSet
    val wanSet = data.getDataSetByIndex(1) as? LineDataSet

    if (lanSet != null && wanSet != null) {
        // Resolver de forma segura la mayor coordenada X registrada hasta el momento
        val maxLanX = if (lanSet.entryCount > 0) lanSet.getEntryForIndex(lanSet.entryCount - 1).x else -1f
        val maxWanX = if (wanSet.entryCount > 0) wanSet.getEntryForIndex(wanSet.entryCount - 1).x else -1f
        val nextX = maxOf(maxLanX, maxWanX) + 1f

        // Adicionar únicamente si representa una latencia válida no fallida
        if (point.lanLatency >= 0) data.addEntry(Entry(nextX, point.lanLatency), 0)
        if (point.wanLatency >= 0) data.addEntry(Entry(nextX, point.wanLatency), 1)

        // Indicarle a la gráfica que su estructura de datos interna se actualizó
        data.notifyDataChanged()
        chart.notifyDataSetChanged()
        
        // Mantener el scroll dinámico acotado a los últimos 60 segundos
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(nextX)
        chart.invalidate()
    }
}
