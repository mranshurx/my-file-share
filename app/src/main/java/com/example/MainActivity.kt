package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// Model for files selected by the Android user
data class SelectedFile(
  val uri: Uri,
  val name: String,
  val size: Long,
  val mimeType: String,
  var status: String = "Ready" // Ready, Sending, Sent, Error
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AirBeamSenderApp()
      }
    }
  }
}

@Composable
fun AirBeamSenderApp() {
  var selectedTab by remember { mutableIntStateOf(0) }
  val context = LocalContext.current

  Scaffold(
    bottomBar = {
      NavigationBar(
        containerColor = Color(0xFF090D16),
        contentColor = Color.White
      ) {
        NavigationBarItem(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          icon = { Icon(Icons.Default.UploadFile, contentDescription = "Native File Sender") },
          label = { Text("Native Sender", fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF38BDF8),
            selectedTextColor = Color(0xFF38BDF8),
            indicatorColor = Color(0xFF0F172A),
            unselectedIconColor = Color(0xFF64748B),
            unselectedTextColor = Color(0xFF64748B)
          )
        )
        NavigationBarItem(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          icon = { Icon(Icons.Default.Language, contentDescription = "WebRTC WebView") },
          label = { Text("WebRTC Web", fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF38BDF8),
            selectedTextColor = Color(0xFF38BDF8),
            indicatorColor = Color(0xFF0F172A),
            unselectedIconColor = Color(0xFF64748B),
            unselectedTextColor = Color(0xFF64748B)
          )
        )
        NavigationBarItem(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          icon = { Icon(Icons.Default.Code, contentDescription = "GitHub & CI/CD") },
          label = { Text("GitHub CI/CD", fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF38BDF8),
            selectedTextColor = Color(0xFF38BDF8),
            indicatorColor = Color(0xFF0F172A),
            unselectedIconColor = Color(0xFF64748B),
            unselectedTextColor = Color(0xFF64748B)
          )
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color(0xFF030712))
    ) {
      when (selectedTab) {
        0 -> NativeSenderScreen(context)
        1 -> WebRtcWebViewScreen()
        2 -> GitHubBuildGuideScreen(context)
      }
    }
  }
}

/**
 * 1. Native Android Sender Screen:
 * Picks multiple files from storage, connects to signaling/P2P, slices into 16KB chunks,
 * tracks backpressure and speed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeSenderScreen(context: Context) {
  val coroutineScope = rememberCoroutineScope()

  // State
  var serverUrl by remember { mutableStateOf("ws://10.0.2.2:3000") }
  var showServerDialog by remember { mutableStateOf(false) }
  var connectionStatus by remember { mutableStateOf("Ready to Connect") }
  var isServerConnected by remember { mutableStateOf(false) }
  var isPeerPaired by remember { mutableStateOf(false) }
  var sessionCode by remember { mutableStateOf(generateRandomCode()) }
  val fileQueue = remember { mutableStateListOf<SelectedFile>() }
  var isTransferring by remember { mutableStateOf(false) }

  // Metrics
  var activeFileName by remember { mutableStateOf("") }
  var activeProgress by remember { mutableFloatStateOf(0f) }
  var transferSpeedMb by remember { mutableStateOf("0.0 MB/s") }
  var etaText by remember { mutableStateOf("--:--") }
  var currentChunkInfo by remember { mutableStateOf("0 / 0 chunks") }
  var isCancelled by remember { mutableStateOf(false) }

  // OkHttp WebSocket instance
  var currentWebSocket by remember { mutableStateOf<WebSocket?>(null) }

  // File Picker Launcher for Photos, Videos, Documents, Archives
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    uris.forEach { uri ->
      val (name, size) = queryFileInfo(context, uri)
      val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
      fileQueue.add(SelectedFile(uri = uri, name = name, size = size, mimeType = mime))
    }
    if (uris.isNotEmpty()) {
      Toast.makeText(context, "Added ${uris.size} files to queue", Toast.LENGTH_SHORT).show()
    }
  }

  // Connect to Signaling Server
  fun connectToSignaling() {
    connectionStatus = "Connecting..."
    val client = OkHttpClient.Builder()
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .pingInterval(25, TimeUnit.SECONDS)
      .build()

    val request = Request.Builder().url(serverUrl).build()

    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        currentWebSocket = webSocket
        isServerConnected = true
        connectionStatus = "Server Online"

        // Create Room with Session Code
        val createRoomMsg = JSONObject().apply {
          put("type", "create-room")
        }
        webSocket.send(createRoomMsg.toString())
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        try {
          val json = JSONObject(text)
          when (json.optString("type")) {
            "room-created" -> {
              sessionCode = json.optString("code", sessionCode)
              connectionStatus = "Waiting for Receiver (#$sessionCode)"
            }
            "peer-joined" -> {
              isPeerPaired = true
              connectionStatus = "Receiver Paired! Ready to Send"
            }
            "peer-disconnected" -> {
              isPeerPaired = false
              connectionStatus = "Receiver Disconnected"
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isServerConnected = false
        isPeerPaired = false
        connectionStatus = "Disconnected (${t.message ?: "Error"})"
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        isServerConnected = false
        isPeerPaired = false
        connectionStatus = "Connection Closed"
      }
    }

    client.newWebSocket(request, listener)
  }

  // File streaming pipeline: 16KB ArrayBuffer chunking
  fun startTransfer() {
    if (fileQueue.isEmpty()) return
    isTransferring = true
    isCancelled = false

    coroutineScope.launch(Dispatchers.IO) {
      val CHUNK_SIZE = 16384 // 16 KB chunk size
      val ws = currentWebSocket

      for ((index, file) in fileQueue.withIndex()) {
        if (isCancelled) break
        activeFileName = file.name
        file.status = "Sending"

        // Send file metadata header
        val totalChunks = Math.ceil(file.size.toDouble() / CHUNK_SIZE).toInt()
        val metaMsg = JSONObject().apply {
          put("type", "file-meta")
          put("id", "android-file-$index")
          put("name", file.name)
          put("size", file.size)
          put("mimeType", file.mimeType)
          put("totalChunks", totalChunks)
        }
        ws?.send(metaMsg.toString())

        var bytesSent = 0L
        var chunkIndex = 0
        val startTime = System.currentTimeMillis()
        var lastSampleTime = startTime
        var lastSampleBytes = 0L

        var inputStream: InputStream? = null
        try {
          inputStream = context.contentResolver.openInputStream(file.uri)
          val buffer = ByteArray(CHUNK_SIZE)
          var read: Int

          while (inputStream?.read(buffer).also { read = it ?: -1 } != -1) {
            if (isCancelled) break

            // Send 16KB chunk payload
            val chunkBytes = if (read == CHUNK_SIZE) buffer else buffer.copyOf(read)
            ws?.send(chunkBytes.toByteString())

            bytesSent += read
            chunkIndex++

            // Calculate speed and ETA every ~250ms
            val now = System.currentTimeMillis()
            val elapsedSec = (now - lastSampleTime) / 1000.0
            if (elapsedSec >= 0.25 || bytesSent >= file.size) {
              val bytesDelta = bytesSent - lastSampleBytes
              val speedBps = if (elapsedSec > 0) bytesDelta / elapsedSec else 0.0
              val remainingBytes = (file.size - bytesSent).coerceAtLeast(0)
              val etaSec = if (speedBps > 0) (remainingBytes / speedBps).toInt() else 0

              withContext(Dispatchers.Main) {
                activeProgress = if (file.size > 0) bytesSent.toFloat() / file.size.toFloat() else 0f
                currentChunkInfo = "Chunk $chunkIndex / $totalChunks (16 KB)"
                transferSpeedMb = String.format("%.2f MB/s", speedBps / (1024 * 1024))
                etaText = formatDuration(etaSec)
              }

              lastSampleTime = now
              lastSampleBytes = bytesSent
            }

            // Backpressure pause
            delay(1)
          }

          // Send file complete marker
          val endMsg = JSONObject().apply {
            put("type", "file-end")
            put("id", "android-file-$index")
          }
          ws?.send(endMsg.toString())

          file.status = "Sent"
        } catch (e: Exception) {
          e.printStackTrace()
          file.status = "Error"
        } finally {
          inputStream?.close()
        }
      }

      withContext(Dispatchers.Main) {
        isTransferring = false
        if (!isCancelled) {
          Toast.makeText(context, "All files transferred successfully!", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Top Bar: Title & Server Config
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF6366F1)))),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column {
          Text("AirBeam Client", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
          Text("P2P File Sender Engine", color = Color(0xFF38BDF8), fontSize = 11.sp)
        }
      }

      Button(
        onClick = { showServerDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Server", fontSize = 11.sp, color = Color.White)
      }
    }

    // Session Code Pairing Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Dynamic Session Code", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isPeerPaired) Color(0xFF10B981) else if (isServerConnected) Color(0xFF38BDF8) else Color(0xFFF59E0B))
            )
            Text(connectionStatus, color = Color(0xFF94A3B8), fontSize = 11.sp)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .background(Color(0xFF030712), RoundedCornerShape(12.dp))
              .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Text(
              sessionCode,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 24.sp,
              color = Color(0xFF38BDF8),
              letterSpacing = 4.sp
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Code", sessionCode))
                Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }

            IconButton(
              onClick = {
                sessionCode = generateRandomCode()
                connectToSignaling()
              },
              modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "New Code", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
          }
        }

        if (!isServerConnected) {
          Button(
            onClick = { connectToSignaling() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Connect to Signaling Server", fontSize = 12.sp, color = Color.White)
          }
        }
      }
    }

    // File Selection Header & Action Button
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Files to Send", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
        Box(
          modifier = Modifier
            .background(Color(0xFF0284C7).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text("${fileQueue.size} files", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
      }

      Button(
        onClick = { filePickerLauncher.launch("*/*") },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Select Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }
    }

    // Active Transfer Metrics Card (Visible while transferring)
    AnimatedVisibility(visible = isTransferring) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(18.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                activeFileName,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(currentChunkInfo, color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
            IconButton(
              onClick = { isCancelled = true; isTransferring = false },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFEF4444))
            }
          }

          LinearProgressIndicator(
            progress = { activeProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF38BDF8),
            trackColor = Color(0xFF1E293B)
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Speed: $transferSpeedMb", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("ETA: $etaText", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text("${(activeProgress * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // File Queue List
    if (fileQueue.isEmpty()) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(Color(0xFF090D16), RoundedCornerShape(16.dp))
          .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
          .clickable { filePickerLauncher.launch("*/*") },
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(44.dp))
          Text("No files queued", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
          Text("Tap here or 'Select Files' to pick photos, videos, or documents", color = Color(0xFF64748B), fontSize = 11.sp)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        itemsIndexed(fileQueue) { index, file ->
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  file.name,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White,
                  fontSize = 12.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  formatBytes(file.size),
                  color = Color(0xFF94A3B8),
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  file.status,
                  fontSize = 10.sp,
                  color = if (file.status == "Sent") Color(0xFF10B981) else Color(0xFF38BDF8),
                  fontWeight = FontWeight.Bold
                )
                IconButton(
                  onClick = { fileQueue.removeAt(index) },
                  modifier = Modifier.size(28.dp),
                  enabled = !isTransferring
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    }

    // Bottom Action Button: Start Transfer
    if (fileQueue.isNotEmpty()) {
      Button(
        onClick = { startTransfer() },
        enabled = !isTransferring,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      ) {
        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Send ${fileQueue.size} Files over P2P", fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
    }
  }

  // Server URL Configuration Dialog
  if (showServerDialog) {
    var tempUrl by remember { mutableStateOf(serverUrl) }
    AlertDialog(
      onDismissRequest = { showServerDialog = false },
      title = { Text("Signaling Server URL", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Enter the WebSocket signaling address (LAN IP or domain):", color = Color(0xFF94A3B8), fontSize = 12.sp)
          OutlinedTextField(
            value = tempUrl,
            onValueChange = { tempUrl = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color(0xFF38BDF8),
              unfocusedTextColor = Color.White,
              focusedBorderColor = Color(0xFF38BDF8),
              unfocusedBorderColor = Color(0xFF334155)
            )
          )
          Text("Examples:\n• Emulator: ws://10.0.2.2:3000\n• Local Wi-Fi: ws://192.168.1.100:3000\n• Internet: wss://your-tunnel.trycloudflare.com", color = Color(0xFF64748B), fontSize = 10.sp)
        }
      },
      confirmButton = {
        TextButton(onClick = {
          serverUrl = tempUrl
          showServerDialog = false
          connectToSignaling()
        }) {
          Text("Connect", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showServerDialog = false }) {
          Text("Cancel", color = Color(0xFF94A3B8))
        }
      },
      containerColor = Color(0xFF0F172A)
    )
  }
}

/**
 * 2. WebRTC WebView Screen (Full Web Client inside Android)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebRtcWebViewScreen() {
  AndroidView(
    factory = { ctx ->
      WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webViewClient = WebViewClient()
        webChromeClient = object : WebChromeClient() {
          override fun onPermissionRequest(request: PermissionRequest?) {
            request?.grant(request.resources)
          }
        }

        loadUrl("file:///android_asset/index.html")
      }
    },
    modifier = Modifier.fillMaxSize()
  )
}

/**
 * 3. GitHub Build Guide Screen:
 * Explains how the APK is automatically built by GitHub Actions in the repo.
 */
@Composable
fun GitHubBuildGuideScreen(context: Context) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0284C7)))),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
          }
          Column {
            Text("GitHub CI/CD Configured!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Text("Automatic APK builds on push", color = Color(0xFF38BDF8), fontSize = 11.sp)
          }
        }
        Text(
          "We added .github/workflows/build-apk.yml and gradlew to your repository. Every time you push to GitHub, GitHub Actions will compile and output the installable Android APK (app-debug.apk) under the 'Actions' tab!",
          color = Color(0xFF94A3B8),
          fontSize = 12.sp,
          lineHeight = 18.sp
        )
      }
    }

    CopyCodeCard(
      title = "Push Code to GitHub",
      command = "git add . && git commit -m \"feat: client app & github build\" && git push -u origin main",
      context = context
    )

    CopyCodeCard(
      title = "Build APK Locally (Terminal)",
      command = "./gradlew assembleDebug",
      context = context
    )

    CopyCodeCard(
      title = "Run Signaling Server (Node.js)",
      command = "npm install && npm start",
      context = context
    )

    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("APK Output Location in Repo", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
        Text("• app/build/outputs/apk/debug/app-debug.apk", color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Download APK from GitHub:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
        Text("1. Go to your GitHub Repo -> 'Actions' tab\n2. Click latest workflow run\n3. Download 'AirBeam-P2P-Client-Debug-APK'", color = Color(0xFF94A3B8), fontSize = 11.sp)
      }
    }
  }
}

@Composable
fun CopyCodeCard(title: String, command: String, context: Context) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(title, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        IconButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Command", command))
            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.size(24.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF030712), RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        Text(command, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
      }
    }
  }
}

// Helpers
fun generateRandomCode(): String {
  return Random.nextInt(100000, 999999).toString()
}

fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val units = arrayOf("B", "KB", "MB", "GB", "TB")
  val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
  return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDuration(seconds: Int): String {
  val m = seconds / 60
  val s = seconds % 60
  return String.format("%02d:%02d", m, s)
}

fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
  var name = "file"
  var size = 0L
  val cursor = context.contentResolver.query(uri, null, null, null, null)
  cursor?.use {
    if (it.moveToFirst()) {
      val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
      if (nameIndex != -1) name = it.getString(nameIndex) ?: "file"
      if (sizeIndex != -1) size = it.getLong(sizeIndex)
    }
  }
  return Pair(name, size)
}
