package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AirBeamApp()
      }
    }
  }
}

@Composable
fun AirBeamApp() {
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
          icon = { Icon(Icons.Default.Radio, contentDescription = "P2P WebRTC Client") },
          label = { Text("P2P Client", fontSize = 11.sp) },
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
          icon = { Icon(Icons.Default.Code, contentDescription = "Server & Guide") },
          label = { Text("Server Guide", fontSize = 11.sp) },
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
        0 -> WebRtcWebViewScreen()
        1 -> ServerGuideScreen(context)
      }
    }
  }
}

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
            // Automatically grant WebRTC camera and media permissions in WebView
            request?.grant(request.resources)
          }
        }

        loadUrl("file:///android_asset/index.html")
      }
    },
    modifier = Modifier.fillMaxSize()
  )
}

@Composable
fun ServerGuideScreen(context: Context) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header Card
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(
                Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF6366F1)))
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              "AirBeam P2P Architecture",
              fontWeight = FontWeight.Bold,
              color = Color.White,
              fontSize = 16.sp
            )
            Text(
              "Long-Distance Peer-to-Peer Transfer",
              color = Color(0xFF38BDF8),
              fontSize = 12.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          "Complete project files (server.js, package.json, public/index.html, public/app.js) are ready in your repository to run directly or push to GitHub.",
          color = Color(0xFF94A3B8),
          fontSize = 12.sp,
          lineHeight = 18.sp
        )
      }
    }

    // Terminal Commands Card
    CodeSnippetCard(
      title = "1. Install Dependencies",
      command = "npm install",
      context = context
    )

    CodeSnippetCard(
      title = "2. Start Signaling Server",
      command = "npm start",
      context = context
    )

    CodeSnippetCard(
      title = "3. Expose over Internet (Free HTTPS)",
      command = "npx cloudflared tunnel --url http://localhost:3000",
      context = context
    )

    CodeSnippetCard(
      title = "4. Push to GitHub",
      command = "git init && git add . && git commit -m \"AirBeam P2P WebRTC\" && git branch -M main",
      context = context
    )

    // Specifications Info
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WebRTC Pipeline Specs", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
        SpecRow("STUN Server", "stun.l.google.com:19302")
        SpecRow("Chunk Pipeline", "16 KB ArrayBuffers")
        SpecRow("Backpressure Threshold", "64 KB (bufferedamountlow)")
        SpecRow("Pairing Protocol", "6-Digit Dynamic Code + QR")
      }
    }
  }
}

@Composable
fun CodeSnippetCard(title: String, command: String, context: Context) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
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
            val clip = ClipData.newPlainText("Command", command)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.size(24.dp)
        ) {
          Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy command",
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(14.dp)
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF030712), RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 8.dp)
      ) {
        Text(
          command,
          color = Color(0xFF38BDF8),
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp
        )
      }
    }
  }
}

@Composable
fun SpecRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, color = Color(0xFF64748B), fontSize = 11.sp)
    Text(value, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
  }
}
