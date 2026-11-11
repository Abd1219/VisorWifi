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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** PDF presentation layer. Its input is precomputed [ReportData]; no network metrics are changed here. */
object PdfGenerator {
    private const val W = 595; private const val H = 842; private const val M = 42f
    private const val CW = W - M * 2; private const val FOOTER = H - 28f
    // Raw ARGB values keep text-sanitizer unit tests loadable on the plain JVM.
    private val ink = 0xFF0F172A.toInt(); private val muted = 0xFF64748B.toInt()
    private val cyan = 0xFF00AFC0.toInt(); private val purple = 0xFF7C3AED.toInt()
    private val border = 0xFFD8E1EA.toInt()

    fun generateReport(context: Context, data: ReportData): File {
        val document = PdfDocument()
        document.page(1) { executive(it, data) }; document.page(2) { metrics(it, data) }; document.page(3) { evidence(context, it, data) }
        val folder = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        return File(folder, "reporte_visorwifi_${System.currentTimeMillis()}.pdf").also { file ->
            FileOutputStream(file).use { document.writeTo(it) }; document.close()
        }
    }
    private fun PdfDocument.page(n: Int, body: (Canvas) -> Unit) { val p = startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); body(p.canvas); finishPage(p) }

    private fun executive(c: Canvas, d: ReportData) {
        header(c, d, "REPORTE DE DIAGNÓSTICO DE RED")
        var y = 112f; val p = d.periodAnalysis; val palette = palette(d.periodStatus)
        card(c, M, y, CW, 104f, palette.third, palette.second)
        t(c, "ESTADO GENERAL DEL PERIODO", M + 16, y + 21, 8f, palette.second, true)
        t(c, palette.first, M + 16, y + 48, 18f, palette.second, true)
        t(c, "Periodo: ${period(p)}", M + 16, y + 66, 8.5f, muted)
        t(c, "Duración: ${duration(p)} · ${p.sampleCount} muestras", M + 16, y + 81, 8.5f, muted)
        t(c, "${p.stabilityScore}/100", W - M - 77, y + 48, 21f, palette.second, true)
        t(c, "SCORE DE ESTABILIDAD", W - M - 116, y + 64, 6.8f, muted, true); y += 128
        section(c, "INDICADORES PRINCIPALES", y); y += 12
        val gap = 10f; val cw = (CW - gap * 2) / 3
        indicator(c, M, y, cw, "RED LOCAL", ms(d.lanLatencyAvgMs), lan(d.lanLatencyAvgMs))
        indicator(c, M + cw + gap, y, cw, "INTERNET", ms(d.wanLatencyAvgMs), wan(d.wanLatencyAvgMs))
        indicator(c, M + (cw + gap) * 2, y, cw, "CONEXIÓN WIFI", rssi(p.rssiAvg), quality(p.rssiAvg.toInt())); y += 76
        card(c, M, y, CW, 28f, Color.parseColor("#F8FAFC"), border)
        t(c, "Pérdida WAN: ${"%.1f".format(Locale.US, p.packetLossPercent)}%    ·    Jitter WAN: ${"%.1f".format(Locale.US, p.avgJitterMs)} ms    ·    Roaming: ${p.roamingEventsCount}", M + 12, y + 18, 8.5f, muted); y += 52
        section(c, "DIAGNÓSTICO AUTOMÁTICO", y); y += 12
        card(c, M, y, CW, 224f, Color.parseColor("#F8FAFC"), border)
        t(c, d.diagnosisTitle, M + 14, y + 22, 11f, ink, true); t(c, "CONFIANZA ${d.confidence}%", W - M - 86, y + 22, 8f, cyan, true)
        wrap(c, ReportNarrativeGenerator.statusContext(p, d.periodStatus, d.diagnosisTitle), M + 14, y + 30, CW - 28, tp(8.2f, Color.parseColor("#334155")))
        t(c, "EVIDENCIAS DETECTADAS", M + 14, y + 79, 7.5f, muted, true)
        var ey = y + 95
        if (d.evidences.isEmpty()) { t(c, "Sin evidencias adicionales registradas.", M + 14, ey, 8.5f, muted); ey += 16 } else d.evidences.forEach { e ->
            c.drawCircle(M + 18, ey - 3, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan }); ey += wrap(c, e, M + 26, ey - 8, CW - 44, tp(8.5f, Color.parseColor("#334155"))) + 5
        }
        val ry = maxOf(ey + 8, y + 150); card(c, M + 14, ry, CW - 28, 52f, Color.parseColor("#EAFBFD"), Color.parseColor("#9DE5EC"))
        t(c, "RECOMENDACIÓN", M + 25, ry + 15, 7.5f, cyan, true); wrap(c, d.recommendation, M + 25, ry + 21, CW - 50, tp(8.5f, ink, true))
        // Mantener la conclusión debajo de una recomendación que pueda ocupar varias líneas.
        y = maxOf(y + 242, ry + 60)
        section(c, "CONCLUSIÓN DEL MONITOREO", y); y += 10
        card(c, M, y, CW, 70f, Color.parseColor("#F8FAFC"), border)
        wrap(c, d.executiveConclusion, M + 14, y + 13, CW - 28, tp(8.7f, ink))
        footer(c, 1)
    }

    private fun metrics(c: Canvas, d: ReportData) {
        header(c, d, "MÉTRICAS TÉCNICAS DETALLADAS"); var y = 114f; val p = d.periodAnalysis
        y = group(c, "RED LOCAL (LAN)", y, listOf(
            row("Latencia LAN promedio", ms(d.lanLatencyAvgMs), lan(d.lanLatencyAvgMs), "Referencia: < 15 ms óptimo; > 50 ms inestable."),
            row("Pico LAN", ms(p.maxLanLatencyMs), spike(p.lanSpikeCount), "${p.lanSpikeCount} muestras por encima de 50 ms."))) + 18
        y = group(c, "CONECTIVIDAD A INTERNET (WAN)", y, listOf(
            row("Latencia WAN promedio", ms(d.wanLatencyAvgMs), wan(d.wanLatencyAvgMs), "Referencia: < 100 ms estable; > 200 ms crítico."),
            row("Pico WAN", ms(p.maxWanLatencyMs), spike(p.latencySpikeCount), "${p.latencySpikeCount} muestras por encima de 200 ms."),
            row("Pérdida ICMP WAN", "%.1f%%".format(Locale.US, p.packetLossPercent), loss(p.packetLossPercent), lossNote(p.packetLossPercent)),
            row("Jitter WAN prom. / máx.", "%.1f / %.1f ms".format(Locale.US, p.avgJitterMs, p.maxJitterMs), jitter(p.avgJitterMs), "Variación entre respuestas WAN consecutivas."),
            row("TCP complementario LAN", tcp(d.latencyHistory.map { it.lanTcpLatency }, d.latencyHistory.count { it.lanTcpTarget != null }), "Informativo", "TCP connect; destino: ${target(d.latencyHistory.map { it.lanTcpTarget })}"),
            row("TCP complementario WAN", tcp(d.latencyHistory.map { it.wanTcpLatency }, d.latencyHistory.count { it.wanTcpTarget != null }), "Informativo", "TCP connect; destino: ${target(d.latencyHistory.map { it.wanTcpTarget })}"))) + 18
        y = group(c, "CONEXIÓN WIFI", y, listOf(
            row("RSSI promedio", rssi(p.rssiAvg), quality(p.rssiAvg.toInt()), "Nivel promedio de intensidad de señal."),
            row("RSSI mínimo", rssi(p.rssiMin), quality(p.rssiMin.toInt()), "Peor nivel registrado durante la sesión."),
            row("Varianza RSSI", "%.1f dBm^2".format(Locale.US, p.rssiVariance), variance(p.rssiVariance), "< 10 estable; > 30 fluctuante."),
            row("Velocidad PHY", "${d.phyRateMbps} Mbps", "Informativo", "Velocidad negociada entre AP y dispositivo."),
            row("Banda WiFi activa", d.wifiBand, "Informativo", "Banda detectada al generar el reporte."),
            row("Eventos roaming / SSID", "${p.roamingEventsCount} / ${p.ssidChangeCount}", roaming(p.roamingEventsCount), "Eventos detectados durante el periodo."))) + 18
        section(c, "NOTAS TÉCNICAS", y); y += 10
        wrap(c, "Las latencias, el jitter y la pérdida corresponden a respuestas ICMP. Las sondas TCP connect se muestran de forma complementaria y no equivalen a RTT ICMP.", M, y, CW, tp(8f, muted, italic = true))
        wrap(c, "La velocidad PHY es la velocidad negociada entre el AP y el dispositivo; no representa la velocidad real de Internet.", M, y + 31, CW, tp(8f, muted, italic = true)); footer(c, 2)
    }

    private fun evidence(context: Context, c: Canvas, d: ReportData) {
        header(c, d, "EVIDENCIA GRÁFICA Y CIERRE"); var y = 114f
        section(c, "FIGURA A — HISTORIAL DE LATENCIAS", y); y = chart(c, graph(context, d.latencyHistory, false), y + 10)
        t(c, "LAN en cyan y WAN en morado. Las escalas y la leyenda reflejan las muestras registradas.", M, y + 12, 8f, muted); y += 34
        section(c, "FIGURA B — HISTORIAL DE INTENSIDAD DE SEÑAL WIFI", y); y = chart(c, graph(context, d.latencyHistory, true), y + 10)
        t(c, "RSSI en dBm durante el periodo monitoreado.", M, y + 12, 8f, muted); y += 32
        section(c, "REGISTRO DE EVENTOS Y CAMBIOS DE RED", y); y += 10; card(c, M, y, CW, 82f, Color.parseColor("#F8FAFC"), border)
        if (d.networkEvents.isEmpty()) wrap(c, "No se detectaron eventos significativos de red durante el monitoreo.", M + 12, y + 15, CW - 24, tp(8.5f, muted))
        else { var ey = y + 13; d.networkEvents.takeLast(5).forEach { ey += wrap(c, "• $it", M + 12, ey, CW - 24, tp(7.8f, Color.parseColor("#334155"))) + 3 } }
        y += 100
        if (!d.technicianNotes.isNullOrBlank()) { section(c, "OBSERVACIONES Y ANOTACIONES DE CAMPO", y); y += 10; card(c, M, y, CW, 60f, Color.parseColor("#F8FAFC"), border); wrap(c, d.technicianNotes, M + 12, y + 12, CW - 24, tp(8.3f, ink, italic = true)); y += 76 }
        signatures(c, d, minOf(y, 680f)); footer(c, 3)
    }

    // Bitmap pixels are converted by aspect ratio into PDF points; they are never pasted 1:1.
    private fun chart(c: Canvas, b: Bitmap, y: Float): Float { val h = minOf(CW * b.height / b.width, 166f); val w = h * b.width / b.height; val x = M + (CW - w) / 2; c.drawBitmap(b, null, RectF(x, y, x + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); return y + h }
    private fun header(c: Canvas, d: ReportData, subtitle: String) { t(c, "VISOR WIFI", M, 56f, 19f, ink, true); t(c, subtitle, M, 73f, 9.5f, cyan, true); val x = W - M - 168; t(c, datetime(d.dateTime), x, 45f, 8.3f, muted); t(c, "Técnico: ${d.technicianName}", x, 59f, 8.3f, muted); t(c, "Sitio: ${d.siteName}", x, 73f, 8.3f, muted); c.drawRect(M, 86f, W - M, 88f, Paint().apply { color = cyan }) }
    private fun footer(c: Canvas, p: Int) { c.drawLine(M, FOOTER - 10, W - M, FOOTER - 10, Paint().apply { color = border }); t(c, "Reporte generado por Visor WiFi · El análisis refleja el periodo monitoreado.", M, FOOTER, 7.3f, muted); t(c, "Página $p de 3", W - M - 50, FOOTER, 7.3f, muted) }
    private fun indicator(c: Canvas, x: Float, y: Float, w: Float, title: String, value: String, state: String) { card(c, x, y, w, 64f, Color.WHITE, border); t(c, title, x + 10, y + 16, 7f, muted, true); t(c, value, x + 10, y + 38, 13f, ink, true); t(c, state.uppercase(), x + 10, y + 53, 7.2f, color(state), true) }
    private data class Row(val label: String, val value: String, val state: String, val note: String); private fun row(a: String,b: String,c: String,d: String)=Row(a,b,c,d)
    private fun group(c: Canvas, title: String, start: Float, rows: List<Row>): Float { section(c, title, start); var y = start + 10; rows.forEachIndexed { i, r -> val rh = maxOf(31f, height(r.note, CW - 277, tp(7.6f, muted)) + 14); c.drawRect(M,y,W-M,y+rh,Paint().apply{color=if(i%2==0)Color.parseColor("#F8FAFC")else Color.WHITE}); c.drawRect(M,y,W-M,y+rh,Paint().apply{color=border;style=Paint.Style.STROKE;strokeWidth=.5f}); t(c,r.label,M+8,y+15,8f,ink,true);t(c,r.value,M+155,y+15,8f,ink,true);t(c,r.state,M+155,y+27,7f,color(r.state),true);wrap(c,r.note,M+260,y+8,CW-269,tp(7.6f,muted));y+=rh }; return y }
    private fun signatures(c: Canvas, d: ReportData, y: Float) { val line=Paint().apply{color=Color.parseColor("#94A3B8")};val sw=165f;val l=M+35;val r=W-M-sw-35;c.drawLine(l,y+40,l+sw,y+40,line);c.drawLine(r,y+40,r+sw,y+40,line);d.technicianSignature?.let{c.drawBitmap(it,null,RectF(l+8,y,l+sw-8,y+36),null)};t(c,"Firma del Técnico",l+40,y+54,8f,ink,true);t(c,"Nombre: ${d.technicianName}",l,y+67,7.5f,muted);t(c,"Firma del Cliente",r+42,y+54,8f,ink,true);t(c,"Sitio: ${d.siteName}",r,y+67,7.5f,muted) }
    private fun section(c:Canvas,s:String,y:Float)=t(c,s,M,y,10f,ink,true); private fun card(c:Canvas,x:Float,y:Float,w:Float,h:Float,bg:Int,stroke:Int){val r=RectF(x,y,x+w,y+h);c.drawRoundRect(r,4f,4f,Paint().apply{color=bg});c.drawRoundRect(r,4f,4f,Paint().apply{color=stroke;style=Paint.Style.STROKE;strokeWidth=.8f})}
    private fun t(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false)=c.drawText(sanitizePdfText(s),x,y,tp(size,color,bold)); private fun tp(size:Float,color:Int,bold:Boolean=false,italic:Boolean=false)=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=Typeface.create(Typeface.SANS_SERIF,if(bold)Typeface.BOLD else if(italic)Typeface.ITALIC else Typeface.NORMAL);fontFeatureSettings="\"liga\" 0, \"clig\" 0"}
    private fun wrap(c:Canvas,s:String,x:Float,y:Float,w:Float,p:TextPaint):Float{val l=layout(s,w,p);c.save();c.translate(x,y);l.draw(c);c.restore();return l.height.toFloat()};private fun height(s:String,w:Float,p:TextPaint)=layout(s,w,p).height.toFloat();private fun layout(s:String,w:Float,p:TextPaint):StaticLayout{val safe=sanitizePdfText(s);return if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)StaticLayout.Builder.obtain(safe,0,safe.length,p,w.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0f,1.12f).build() else @Suppress("DEPRECATION") StaticLayout(safe,p,w.toInt(),Layout.Alignment.ALIGN_NORMAL,1.12f,0f,false)}
    private fun palette(s:PeriodReportStatus)=when(s){PeriodReportStatus.OPTIMAL->Triple("ÓPTIMO",Color.parseColor("#15803D"),Color.parseColor("#F0FDF4"));PeriodReportStatus.DEGRADED->Triple("DEGRADADO",Color.parseColor("#B45309"),Color.parseColor("#FFFBEB"));PeriodReportStatus.UNSTABLE->Triple("INESTABLE",Color.parseColor("#C2410C"),Color.parseColor("#FFF7ED"));PeriodReportStatus.CRITICAL->Triple("CRÍTICO",Color.parseColor("#B91C1C"),Color.parseColor("#FEF2F2"))}
    private fun color(s:String)=when(s.lowercase()){ "óptimo","excelente","muy buena","buena","estable"->Color.parseColor("#15803D");"degradado","aceptable","regular","moderada","informativo"->Color.parseColor("#B45309");"inestable","crítico","débil","frecuente","timeout","sin señal","fluctuante"->Color.parseColor("#B91C1C");else->muted }
    private fun ms(v:Float)=if(v>=0)"%.1f ms".format(Locale.US,v)else "Timeout";private fun rssi(v:Float)=if(v>-127)"%.1f dBm".format(Locale.US,v)else "Sin señal";private fun lossNote(v:Float)=if(v == 0f) "No se detectaron pérdidas de paquetes ICMP durante el periodo monitoreado." else "Porcentaje de respuestas ICMP WAN sin respuesta durante el periodo."
    private fun lan(v:Float)=when{v<0->"Timeout";v<15->"Óptimo";v<50->"Aceptable";else->"Inestable"};private fun wan(v:Float)=when{v<0->"Timeout";v<100->"Óptimo";v<150->"Degradado";v<200->"Inestable";else->"Crítico"};private fun spike(v:Int)=when{v==0->"Óptimo";v<5->"Degradado";v<10->"Inestable";else->"Crítico"};private fun loss(v:Float)=when{v<2->"Óptimo";v<5->"Degradado";v<10->"Inestable";else->"Crítico"};private fun jitter(v:Float)=when{v<15->"Óptimo";v<30->"Aceptable";else->"Inestable"};private fun variance(v:Float)=when{v<10->"Estable";v<30->"Moderada";else->"Fluctuante"};private fun roaming(v:Int)=when{v==0->"Óptimo";v<3->"Aceptable";else->"Frecuente"};private fun quality(v:Int)=when{v>=-50->"Excelente";v>=-60->"Muy buena";v>=-70->"Buena";v>=-80->"Regular";v>-127->"Débil";else->"Sin señal"}
    private fun period(p:PeriodAnalysis):String{val f=SimpleDateFormat("HH:mm:ss",Locale.getDefault());return "${f.format(Date(p.periodStartMs))} - ${f.format(Date(p.periodEndMs))}"};private fun duration(p:PeriodAnalysis):String{val s=((p.periodEndMs-p.periodStartMs)/1000).coerceAtLeast(0);return if(s<60)"${s}s" else if(s<3600)"${s/60}m ${s%60}s" else "${s/3600}h ${(s%3600)/60}m"};private fun datetime(s:String)=s.replace(Regex("^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{1,2}:\\d{2})$"),"$1 · $2")
    private fun tcp(v:List<Float>,n:Int):String{if(n==0)return "No utilizada";val ok=v.filter{it>=0};return if(ok.isEmpty())"Sin respuesta (0/$n)" else "%.1f ms (%d/%d)".format(Locale.US,ok.average(),ok.size,n)};private fun target(t:List<String?>)=t.lastOrNull{it!=null}?:"no disponible"
    private fun graph(context:Context,h:List<LatencyPoint>,rssi:Boolean):Bitmap{val w=1400;val ht=430;val chart=LineChart(context).apply{layoutParams=ViewGroup.LayoutParams(w,ht);description.isEnabled=false;setTouchEnabled(false);setDrawGridBackground(false);setBackgroundColor(Color.WHITE);setNoDataText("Sin datos históricos para graficar");setExtraOffsets(8f,6f,8f,4f);legend.apply{isEnabled=true;textColor=Color.parseColor("#334155");textSize=14f;form=Legend.LegendForm.LINE;formSize=14f;horizontalAlignment=Legend.LegendHorizontalAlignment.CENTER;verticalAlignment=Legend.LegendVerticalAlignment.TOP;orientation=Legend.LegendOrientation.HORIZONTAL;setDrawInside(false)};xAxis.apply{position=XAxis.XAxisPosition.BOTTOM;textColor=muted;textSize=14f;gridColor=border;setDrawGridLines(true);setLabelCount(5,false);setAvoidFirstLastClipping(true);valueFormatter=object:ValueFormatter(){override fun getFormattedValue(v:Float)="${v.toInt()}s"}};axisLeft.apply{textColor=muted;textSize=14f;gridColor=border;setDrawGridLines(true);axisMinimum=if(rssi)-100f else 0f;setLabelCount(if(rssi)4 else 5,false);if(rssi){axisMaximum=-30f;valueFormatter=object:ValueFormatter(){override fun getFormattedValue(v:Float)="${v.toInt()} dBm"}}else valueFormatter=object:ValueFormatter(){override fun getFormattedValue(v:Float)="${v.toInt()} ms"}};axisRight.isEnabled=false}
        if(rssi){val e=h.mapIndexedNotNull{i,p->p.rssi.takeIf{it>-127}?.let{Entry(i.toFloat(),it.toFloat())}};if(e.isNotEmpty())chart.data=LineData(LineDataSet(e,"RSSI (dBm)").apply{color=cyan;lineWidth=5f;setDrawCircles(false);setDrawValues(false);setDrawFilled(true);fillColor=cyan;fillAlpha=24})}else{val l=h.mapIndexedNotNull{i,p->p.lanLatency.takeIf{it>=0}?.let{Entry(i.toFloat(),it)}};val n=h.mapIndexedNotNull{i,p->p.wanLatency.takeIf{it>=0}?.let{Entry(i.toFloat(),it)}};val sets=mutableListOf<ILineDataSet>();if(l.isNotEmpty())sets+=LineDataSet(l,"LAN (Acceso local)").apply{color=cyan;lineWidth=5f;setDrawCircles(false);setDrawValues(false)};if(n.isNotEmpty())sets+=LineDataSet(n,"WAN (Internet)").apply{color=purple;lineWidth=5f;setDrawCircles(false);setDrawValues(false)};if(sets.isNotEmpty())chart.data=LineData(sets)}
        chart.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ht,View.MeasureSpec.EXACTLY));chart.layout(0,0,w,ht);return Bitmap.createBitmap(w,ht,Bitmap.Config.ARGB_8888).also{chart.draw(Canvas(it))}}
    internal fun sanitizePdfText(s:String)=s.replace("\uFB00","ff").replace("\uFB01","fi").replace("\uFB02","fl").replace("\uFB03","ffi").replace("\uFB04","ffl").replace("\u2013","-").replace("\u2014","-").replace("\u2265",">=").replace("\u2264","<=").replace("\u2248","~").replace("\u00B2","^2").replace("✓","[OK]").replace("✗","[X]").replace("⚠️","[!]").replace("⚠","[!]").replace("🔀","[roaming]").replace("📡","[AP]").replace("📶","[señal]").replace("➔","->").replace("→","->").replace("←","<-").replace("\u201C","\"").replace("\u201D","\"").replace("\u2018","'").replace("\u2019","'").replace("\u00B7",".").replace("\u2022","-").replace("\u2026","...").replace("\u00A0"," ")
}
