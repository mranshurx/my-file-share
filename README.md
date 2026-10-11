# 🛰️ AirBeam P2P - WebRTC Long-Distance File Transfer

> A production-ready, peer-to-peer (P2P) WebRTC file transfer application designed to transfer files across the internet over kilometers with zero cloud storage costs, carrier-grade NAT traversal, dynamic QR code pairing, and automatic backpressure management.

---

## 🏗️ Architecture & Technology Stack

1. **Frontend**: Single-Page Web Application (`public/index.html` + `public/app.js`)
   - **Tailwind CSS (CDN)**: Sleek, responsive dark-mode mobile UI.
   - **WebRTC DataChannel (`RTCDataChannel`)**: End-to-end encrypted direct peer connection labeled `"file-transfer"`.
   - **Public Google STUN Servers**: `stun:stun.l.google.com:19302` for NAT traversal across cellular (4G/5G) and home Wi-Fi networks.
   - **Safe 16KB ArrayBuffer Chunking**: Prevents browser memory spikes and buffer overrun during large files (photos, 4K videos, zip files).
   - **Backpressure Management**: Monitors `dataChannel.bufferedAmount` with a 64KB threshold and pauses chunk slicing until `bufferedamountlow` fires.
   - **Dynamic QR Pairing & Camera Scanner**: Instant pairing using `qrcode.js` and `html5-qrcode`.

2. **Backend Signaling Server (`server.js`)**:
   - **Node.js + Express**: Serves frontend static assets.
   - **`ws` (WebSockets)**: Handles room creation, 6-digit session codes, and SDP offer/answer/ICE candidate exchange.
   - **Carrier Keep-Alive**: Heartbeat pings every 30 seconds to bypass aggressive mobile carrier NAT timeouts.

---

## 🚀 Quick Start (Local & Remote Testing)

### 1. Prerequisites
Ensure you have **Node.js (v18+)** installed.

```bash
node -v
npm -v
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Run the Signaling Server
```bash
npm start
```

The console will display:
```text
🚀 AirBeam P2P WebRTC Signaling Server Running!
📡 Local URL:       http://localhost:3000
📱 LAN Mobile URL:  http://192.168.1.X:3000
⚡ WebSocket URL:   ws://192.168.1.X:3000
```

---

## 📱 Testing Between Two Devices

### A. Testing on the Same Wi-Fi / Local Network
1. On Device 1 (e.g. Sender PC or Phone): Open `http://<your-lan-ip>:3000`.
2. Notice the 6-digit code and QR code.
3. On Device 2 (e.g. Receiver Phone): Open `http://<your-lan-ip>:3000`.
4. Tap **Receive Files** and scan the QR code or type the 6-digit code.
5. Once connected, drop files on Device 1 and watch them transfer directly to Device 2!

### B. Testing Across the Internet (Over Kilometers / 4G / 5G)
To test across the public internet between cellular carriers, host the signaling server or expose it via an HTTPS tunnel (WebRTC requires HTTPS or localhost for camera permissions):

#### Option 1: Cloudflare Tunnel (Free & Instant)
```bash
npx cloudflared tunnel --url http://localhost:3000
```

#### Option 2: Ngrok
```bash
npx ngrok http 3000
```
Open the generated HTTPS URL on both phones anywhere in the world! Google STUN will resolve public IP candidates and establish direct P2P transfer over cellular networks.

---

## 📦 Project Structure

```text
├── package.json         # Node.js dependencies (express, ws, cors)
├── server.js            # Node.js + WebSocket signaling server
├── public/
│   ├── index.html       # Responsive dark-mode Tailwind UI
│   └── app.js           # WebRTC engine, 16KB chunking & backpressure
└── README.md            # Documentation & setup instructions
```

---

## 🐙 Push to GitHub

To make your repository on GitHub:

```bash
git init
git add .
git commit -m "feat: complete production-ready P2P WebRTC file transfer system"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main
```
