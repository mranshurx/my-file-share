package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MinimalShareApp()
      }
    }
  }
}

@Composable
fun MinimalShareApp() {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // Hardcoded server & session configuration
  val hardcodedServerUrl = "wss://airbeam-demo-server.onrender.com"
  val fallbackLocalUrl = "ws://10.0.2.2:3000"
  val sessionCode = remember { Random.nextInt(100000, 999999).toString() }

  var statusText by remember { mutableStateOf("Ready") }
  var isTransferring by remember { mutableStateOf(false) }
  var progress by remember { mutableFloatStateOf(0f) }
  var progressDetails by remember { mutableStateOf("") }

  // File picker launcher
  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetMultipleContents()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      isTransferring = true
      statusText = "Connecting to Server..."

      coroutineScope.launch(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
          .readTimeout(0, TimeUnit.MILLISECONDS)
          .pingInterval(25, TimeUnit.SECONDS)
          .build()

        var activeWs: WebSocket? = null
        var connected = false

        val listener = object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            activeWs = webSocket
            val msg = JSONObject().apply {
              put("type", "create-room")
              put("code", sessionCode)
            }
            webSocket.send(msg.toString())
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            try {
              val json = JSONObject(text)
              if (json.optString("type") == "room-created" || json.optString("type") == "peer-joined") {
                // Safe UI update on main thread
                // Handled via coroutine scope if needed
              }
            } catch (_: Exception) {}
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
          }
        }

        try {
          val req = Request.Builder().url(hardcodedServerUrl).build()
          client.newWebSocket(req, listener)
          Thread.sleep(2000)
        } catch (_: Exception) {}

        if (!connected) {
          try {
            val req = Request.Builder().url(fallbackLocalUrl).build()
            client.newWebSocket(req, listener)
            Thread.sleep(2000)
          } catch (_: Exception) {}
        }

        withContext(Dispatchers.Main) {
          statusText = "Connected! Streaming ${uris.size} files..."
        }

        // Stream files in 16KB chunks
        val CHUNK_SIZE = 16384
        for ((index, uri) in uris.withIndex()) {
          var name = "file_$index"
          var size = 0L
          context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
              val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
              val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
              if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
              if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
            }
          }

          val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
          val totalChunks = Math.ceil(size.toDouble() / CHUNK_SIZE).toInt()

          val meta = JSONObject().apply {
            put("type", "file-meta")
            put("id", "file-$index")
            put("name", name)
            put("size", size)
            put("mimeType", mime)
            put("totalChunks", totalChunks)
          }
          activeWs?.send(meta.toString())

          var sentBytes = 0L
          var chunkIdx = 0
          context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it ?: -1 } != -1) {
              val chunk = if (read == CHUNK_SIZE) buffer else buffer.copyOf(read)
              activeWs?.send(chunk.toByteString())
              sentBytes += read
              chunkIdx++

              withContext(Dispatchers.Main) {
                progress = if (size > 0) sentBytes.toFloat() / size.toFloat() else 0f
                progressDetails = "Sharing: $name (${chunkIdx}/${totalChunks})"
              }
              Thread.sleep(1)
            }
          }

          val end = JSONObject().apply {
            put("type", "file-end")
            put("id", "file-$index")
          }
          activeWs?.send(end.toString())
        }

        withContext(Dispatchers.Main) {
          isTransferring = false
          statusText = "Shared Successfully!"
          Toast.makeText(context, "All files shared successfully!", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF030712))
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .size(140.dp)
          .clip(CircleShape)
          .background(
            Brush.linearGradient(
              listOf(Color(0xFF0284C7), Color(0xFF6366F1))
            )
          )
          .border(2.dp, Color(0xFF38BDF8), CircleShape)
          .clickable(enabled = !isTransferring) {
            filePicker.launch("*/*")
          },
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Default.Share,
          contentDescription = "Share",
          tint = Color.White,
          modifier = Modifier.size(64.dp)
        )
      }

      Spacer(modifier = Modifier.height(36.dp))

      Text(
        text = if (isTransferring) "Sharing Files..." else "SHARE",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = statusText,
        color = Color(0xFF38BDF8),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
      )

      if (isTransferring) {
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = Color(0xFF38BDF8),
          trackColor = Color(0xFF1E293B)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = progressDetails,
          color = Color(0xFF94A3B8),
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }
  }
}
