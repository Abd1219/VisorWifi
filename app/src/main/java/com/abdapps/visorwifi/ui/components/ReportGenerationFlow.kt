package com.abdapps.visorwifi.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.abdapps.visorwifi.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Estructura para almacenar trazos de la firma
data class SignatureStroke(val points: List<Offset>)

/**
 * Diálogo de captura de datos para el reporte.
 * Solicita los datos del Técnico, Cliente/Sitio, Notas y la Firma digital.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (technician: String, site: String, notes: String, signature: Bitmap?) -> Unit
) {
    var technician by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    // Almacena los trazos del lienzo de firma
    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    var currentStrokePoints = remember { mutableStateListOf<Offset>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
            border = BorderStroke(1.dp, Color(0xFF263238))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "GENERAR REPORTE TÉCNICO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00F0FF),
                    letterSpacing = 1.sp
                )

                // Campos de entrada con estilos oscuros acordes a la app
                OutlinedTextField(
                    value = technician,
                    onValueChange = { technician = it },
                    label = { Text("Nombre del Técnico", color = Color(0xFF8E9AA8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color(0xFF263238),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF161C26),
                        unfocusedContainerColor = Color(0xFF161C26)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text("Nombre del Sitio / Cliente", color = Color(0xFF8E9AA8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color(0xFF263238),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF161C26),
                        unfocusedContainerColor = Color(0xFF161C26)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones de Campo (Opcional)", color = Color(0xFF8E9AA8)) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F0FF),
                        unfocusedBorderColor = Color(0xFF263238),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF161C26),
                        unfocusedContainerColor = Color(0xFF161C26)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Sección del panel de Firma
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Firma del Técnico (Dibujar en el recuadro)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF536371)
                        )
                        if (strokes.isNotEmpty() || currentStrokePoints.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    strokes.clear()
                                    currentStrokePoints.clear()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Limpiar Panel", color = Color(0xFFFF3366), fontSize = 11.sp)
                            }
                        }
                    }

                    // Canvas interactivo para la firma
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStrokePoints.clear()
                                        currentStrokePoints.add(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStrokePoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        strokes.add(SignatureStroke(currentStrokePoints.toList()))
                                        currentStrokePoints.clear()
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Dibujar trazos guardados
                            strokes.forEach { stroke ->
                                if (stroke.points.size > 1) {
                                    for (i in 1 until stroke.points.size) {
                                        drawLine(
                                            color = Color.Black,
                                            start = stroke.points[i - 1],
                                            end = stroke.points[i],
                                            strokeWidth = 3.5f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            // Dibujar trazo actual en arrastre
                            if (currentStrokePoints.size > 1) {
                                for (i in 1 until currentStrokePoints.size) {
                                    drawLine(
                                        color = Color.Black,
                                        start = currentStrokePoints[i - 1],
                                        end = currentStrokePoints[i],
                                        strokeWidth = 3.5f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        if (strokes.isEmpty() && currentStrokePoints.isEmpty()) {
                            Text(
                                text = "Firmar Aquí",
                                fontSize = 13.sp,
                                color = Color.LightGray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF263238)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (technician.isBlank() || site.isBlank()) {
                                return@Button
                            }
                            // Convertir trazos a un Bitmap de firma antes de enviar
                            val signatureBitmap = if (strokes.isNotEmpty()) {
                                strokesToBitmap(strokes, 280, 100)
                            } else null
                            onSubmit(technician, site, notes, signatureBitmap)
                        },
                        enabled = technician.isNotBlank() && site.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00F0FF),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF161C26),
                            disabledContentColor = Color(0xFF536371)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GENERAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo interactivo para previsualizar y compartir el PDF generado.
 * Lee las páginas del documento local usando la clase PdfRenderer nativa de Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }

    // Cargar páginas del PDF en segundo plano usando corrutinas
    LaunchedEffect(file) {
        scope.launch(Dispatchers.IO) {
            val bitmaps = mutableListOf<Bitmap>()
            try {
                val parcelFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(parcelFd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Renderizar con un escalado sutil para visualización nítida (1.5x)
                    val width = (page.width * 1.5f).toInt()
                    val height = (page.height * 1.5f).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)
                }
                renderer.close()
                parcelFd.close()
                withContext(Dispatchers.Main) {
                    pageBitmaps = bitmaps
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    renderError = e.message
                    isLoading = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Reporte de Red WiFi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141C)),
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Text("Cerrar", color = Color(0xFFFF3366), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            },
            bottomBar = {
                // Barra inferior con botón llamativo para compartir
                Surface(
                    color = Color(0xFF0F141C),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Button(
                        onClick = { sharePdf(context, file) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF66), // Green for action
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(48.dp)
                    ) {
                        Text("COMPARTIR REPORTE (PDF)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            },
            containerColor = Color(0xFF0B0E14)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF00F0FF))
                } else if (renderError != null) {
                    Text(
                        text = "Error al renderizar el preview: $renderError",
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageBitmaps) { bitmap ->
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                    .border(1.dp, Color(0xFF263238), RoundedCornerShape(4.dp))
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Página del Reporte PDF",
                                    modifier = Modifier.fillMaxSize().background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convierte los trazos de firma del usuario a un Bitmap de Android limpio.
 */
private fun strokesToBitmap(strokes: List<SignatureStroke>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    strokes.forEach { stroke ->
        if (stroke.points.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x, stroke.points[i].y)
            }
            canvas.drawPath(path, paint)
        }
    }

    return bitmap
}

/**
 * Lanza un Intent de Android para compartir el archivo PDF generado externamente.
 * Utiliza FileProvider para otorgar acceso de lectura de manera segura.
 */
private fun sharePdf(context: Context, file: File) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Compartir Reporte de Diagnóstico"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
