package com.abdapps.visorwifi.ui.components

import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.abdapps.visorwifi.model.LatencyPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.ArrayList

/**
 * Componente Compose que envuelve de forma interoperable el LineChart de MPAndroidChart
 * para visualizar el historial técnico de intensidad de señal Wi-Fi (RSSI) en dBm.
 *
 * @param history Colección de puntos de medición telemétrica.
 * @param modifier Modificador Compose de diseño.
 */
@Composable
fun RssiChart(
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
                setupRssiChartStyling(this)
            }
        },
        modifier = modifier,
        update = { chart ->
            populateRssiChartWithHistory(chart, history)
        }
    )
}

/**
 * Personaliza el estilo visual del LineChart específico de RSSI.
 * Configura los ejes, límites de rango en dBm, y líneas de umbral técnico (Bueno/Regular/Malo).
 */
fun setupRssiChartStyling(chart: LineChart) {
    chart.apply {
        description.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        isScaleXEnabled = true
        isScaleYEnabled = false // Bloqueado el zoom vertical para consistencia de lectura
        setPinchZoom(true)
        setDrawGridBackground(false)
        setBackgroundColor(Color.TRANSPARENT)
        setNoDataText("Inicia el monitoreo para visualizar la gráfica de señal")
        setNoDataTextColor(Color.parseColor("#536371"))

        // Deshabilitar la leyenda ya que es una única métrica bien descrita por el título de la tarjeta
        legend.isEnabled = false

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
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}s"
            }
        }

        // Estilización del Eje Y Izquierdo (Intensidad de Señal RSSI en dBm)
        axisLeft.apply {
            textColor = Color.parseColor("#536371")
            textSize = 9f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            
            // Límites lógicos para la señal Wi-Fi dBm (típicamente entre -100 dBm y -30 dBm)
            axisMinimum = -100f
            axisMaximum = -30f
            
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()} dBm"
            }

            // Umbrales visuales requeridos:
            // -60 dBm -> Bueno (Verde)
            val limitBueno = LimitLine(-60f, "Bueno (-60 dBm)").apply {
                lineColor = Color.parseColor("#3300FF66")
                lineWidth = 1f
                enableDashedLine(10f, 10f, 0f)
                textColor = Color.parseColor("#8E9AA8")
                textSize = 8f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
            // -70 dBm -> Regular (Naranja)
            val limitRegular = LimitLine(-70f, "Regular (-70 dBm)").apply {
                lineColor = Color.parseColor("#33FF9900")
                lineWidth = 1f
                enableDashedLine(10f, 10f, 0f)
                textColor = Color.parseColor("#8E9AA8")
                textSize = 8f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
            // -80 dBm -> Malo (Rojo)
            val limitMalo = LimitLine(-80f, "Malo (-80 dBm)").apply {
                lineColor = Color.parseColor("#33FF3366")
                lineWidth = 1f
                enableDashedLine(10f, 10f, 0f)
                textColor = Color.parseColor("#8E9AA8")
                textSize = 8f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }

            // Inyectar líneas de límite
            addLimitLine(limitBueno)
            addLimitLine(limitRegular)
            addLimitLine(limitMalo)
        }

        // Deshabilitar el eje derecho redundante
        axisRight.isEnabled = false
    }
}

/**
 * Carga el historial de valores RSSI activos al gráfico.
 */
fun populateRssiChartWithHistory(chart: LineChart, history: List<LatencyPoint>) {
    Log.d("RssiChart", "populateRssiChartWithHistory: history size = ${history.size}")
    val rssiEntries = ArrayList<Entry>()

    history.forEachIndexed { index, point ->
        val x = index.toFloat()
        // Filtrar valores de fallback por defecto cuando no hay señal o permiso (-127 dBm)
        if (point.rssi > -127) {
            rssiEntries.add(Entry(x, point.rssi.toFloat()))
        }
    }

    if (rssiEntries.isEmpty()) {
        chart.clear()
        chart.invalidate()
        return
    }

    // Diseñar el dataset RSSI con el color de acento verde
    val rssiDataSet = LineDataSet(rssiEntries, "Intensidad RSSI (dBm)").apply {
        color = Color.parseColor("#00FF66")
        lineWidth = 2.0f
        setDrawCircles(false)
        setDrawCircleHole(false)
        mode = if (rssiEntries.size >= 3) LineDataSet.Mode.HORIZONTAL_BEZIER else LineDataSet.Mode.LINEAR
        cubicIntensity = 0.12f
        setDrawValues(false)
        setDrawFilled(true)
        fillColor = Color.parseColor("#00FF66")
        fillAlpha = 10 // Sombreado de relleno sumamente tenue
    }

    val dataSets = ArrayList<ILineDataSet>()
    dataSets.add(rssiDataSet)

    val lineData = LineData(dataSets)
    chart.data = lineData
    chart.notifyDataSetChanged()

    // Mantener la ventana deslizante acotada a los últimos 60 segundos
    if (history.size > 60) {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX((history.size - 1).toFloat())
    } else {
        chart.setVisibleXRangeMaximum(60f)
        chart.moveViewToX(0f)
    }

    chart.invalidate()
}
