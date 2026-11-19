package com.abdapps.visorwifi.ui.components

import android.graphics.Color
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
import kotlin.math.abs

/** Muestra la variación entre mediciones ICMP consecutivas, separada de la latencia. */
@Composable
fun JitterChart(
    history: List<LatencyPoint>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setupJitterChartStyling(this)
            }
        },
        modifier = modifier,
        update = { chart -> populateJitterChartWithHistory(chart, history) }
    )
}

private fun setupJitterChartStyling(chart: LineChart) {
    chart.apply {
        description.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        isScaleXEnabled = true
        isScaleYEnabled = false
        setPinchZoom(true)
        setDrawGridBackground(false)
        setBackgroundColor(Color.TRANSPARENT)
        setNoDataText("Se requieren dos mediciones para visualizar el jitter")
        setNoDataTextColor(Color.parseColor("#536371"))

        legend.apply {
            isEnabled = true
            textColor = Color.parseColor("#B8C4D0")
            textSize = 11f
            form = Legend.LegendForm.LINE
            formSize = 8f
            formLineWidth = 2f
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            xEntrySpace = 16f
        }

        xAxis.apply {
            textColor = Color.parseColor("#8E9AA8")
            textSize = 10f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()}s"
            }
        }

        axisLeft.apply {
            textColor = Color.parseColor("#8E9AA8")
            textSize = 10f
            gridColor = Color.parseColor("#1C2331")
            gridLineWidth = 0.8f
            enableGridDashedLine(10f, 10f, 0f)
            setDrawGridLines(true)
            setDrawAxisLine(true)
            axisLineColor = Color.parseColor("#1C2331")
            axisMinimum = 0f
            spaceTop = 15f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()} ms"
            }
        }

        axisRight.isEnabled = false
    }
}

private fun populateJitterChartWithHistory(chart: LineChart, history: List<LatencyPoint>) {
    val lanJitter = ArrayList<Entry>()
    val wanJitter = ArrayList<Entry>()

    history.zipWithNext().forEachIndexed { index, (previous, current) ->
        val x = (index + 1).toFloat()
        if (previous.lanLatency >= 0f && current.lanLatency >= 0f) {
            lanJitter += Entry(x, abs(current.lanLatency - previous.lanLatency))
        }
        if (previous.wanLatency >= 0f && current.wanLatency >= 0f) {
            wanJitter += Entry(x, abs(current.wanLatency - previous.wanLatency))
        }
    }

    if (lanJitter.isEmpty() && wanJitter.isEmpty()) {
        chart.clear()
        chart.invalidate()
        return
    }

    val dataSets = ArrayList<ILineDataSet>()
    if (lanJitter.isNotEmpty()) dataSets += createJitterDataSet(lanJitter, "Jitter LAN", "#00F0FF")
    if (wanJitter.isNotEmpty()) dataSets += createJitterDataSet(wanJitter, "Jitter WAN", "#D946EF")

    chart.data = LineData(dataSets)
    chart.notifyDataSetChanged()
    chart.setVisibleXRangeMaximum(60f)
    chart.moveViewToX(if (history.size > 60) (history.size - 1).toFloat() else 0f)
    chart.invalidate()
}

private fun createJitterDataSet(
    entries: List<Entry>,
    label: String,
    colorHex: String
): LineDataSet = LineDataSet(entries, label).apply {
    color = Color.parseColor(colorHex)
    lineWidth = 2.2f
    setDrawCircles(false)
    setDrawCircleHole(false)
    // No interpolar: el jitter debe conservar sus cambios entre muestras.
    mode = LineDataSet.Mode.LINEAR
    setDrawValues(false)
    setDrawFilled(false)
}
