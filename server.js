/**
 * AirBeam P2P - WebRTC Signaling Server
 * 
 * Production-ready WebSocket signaling server for peer-to-peer file transfer.
 * Handles room creation, 6-digit session code pairing, and SDP/ICE relay.
 */

const http = require('http');
const path = require('path');
const os = require('os');
const express = require('express');
const { WebSocketServer, WebSocket } = require('ws');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Fallback route for SPA
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });

/**
 * Active rooms map:
 * code -> {
 *   sender: WebSocket | null,
 *   receiver: WebSocket | null,
 *   createdAt: number
 * }
 */
const rooms = new Map();

/**
 * Generate a cryptographically sensible unique 6-digit code
 */
function generateSessionCode() {
  let code;
  let attempts = 0;
  do {
    code = Math.floor(100000 + Math.random() * 900000).toString();
    attempts++;
  } while (rooms.has(code) && attempts < 100);
  return code;
}

/**
 * Safely send JSON to a WebSocket client
 */
function sendTo(ws, message) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

// WebSocket connection lifecycle
wss.on('connection', (ws, req) => {
  ws.isAlive = true;
  ws.roomCode = null;
  ws.role = null; // 'sender' | 'receiver'

  ws.on('pong', () => {
    ws.isAlive = true;
  });

  ws.on('message', (data) => {
    try {
      const message = JSON.parse(data.toString());

      switch (message.type) {
        case 'create-room': {
          // If already in a room, leave it
          leaveCurrentRoom(ws);

          const code = generateSessionCode();
          rooms.set(code, {
            sender: ws,
            receiver: null,
            createdAt: Date.now()
          });

          ws.roomCode = code;
          ws.role = 'sender';

          sendTo(ws, {
            type: 'room-created',
            code
          });
          console.log(`[Room Created] Code: ${code} (Sender connected)`);
          break;
        }

        case 'join-room': {
          const { code } = message;
          if (!code || !rooms.has(code)) {
            sendTo(ws, {
              type: 'error',
              message: 'Invalid session code. Room does not exist or has expired.'
            });
            return;
          }

          const room = rooms.get(code);

          // If sender disconnected or room already has active receiver
          if (!room.sender || room.sender.readyState !== WebSocket.OPEN) {
            rooms.delete(code);
            sendTo(ws, {
              type: 'error',
              message: 'Host/Sender is no longer online. Please request a new code.'
            });
            return;
          }

          if (room.receiver && room.receiver.readyState === WebSocket.OPEN && room.receiver !== ws) {
            sendTo(ws, {
              type: 'error',
              message: 'Session is full. Another receiver is already connected.'
            });
            return;
          }

          // Leave previous room if any
          leaveCurrentRoom(ws);

          room.receiver = ws;
          ws.roomCode = code;
          ws.role = 'receiver';

          // Notify receiver they joined
          sendTo(ws, {
            type: 'room-joined',
            code
          });

          // Notify sender that receiver connected, prompting Sender to initiate WebRTC offer
          sendTo(room.sender, {
            type: 'peer-joined',
            code
          });

          console.log(`[Room Paired] Code: ${code} (Receiver joined, signaling started)`);
          break;
        }

        case 'offer': {
          // Relay SDP offer from Sender to Receiver
          const room = rooms.get(ws.roomCode);
          if (room && ws.role === 'sender' && room.receiver) {
            sendTo(room.receiver, {
              type: 'offer',
              offer: message.offer
            });
          }
          break;
        }

        case 'answer': {
          // Relay SDP answer from Receiver to Sender
          const room = rooms.get(ws.roomCode);
          if (room && ws.role === 'receiver' && room.sender) {
            sendTo(room.sender, {
              type: 'answer',
              answer: message.answer
            });
          }
          break;
        }

        case 'ice-candidate': {
          // Relay ICE candidate to the opposing peer
          const room = rooms.get(ws.roomCode);
          if (!room) return;

          const target = ws.role === 'sender' ? room.receiver : room.sender;
          if (target) {
            sendTo(target, {
              type: 'ice-candidate',
              candidate: message.candidate
            });
          }
          break;
        }

        case 'leave-room': {
          leaveCurrentRoom(ws);
          break;
        }

        default:
          console.warn(`[Unknown Message]`, message);
      }
    } catch (err) {
      console.error(`[Message Error]`, err);
      sendTo(ws, {
        type: 'error',
        message: 'Malformed message received.'
      });
    }
  });

  ws.on('close', () => {
    leaveCurrentRoom(ws);
  });

  ws.on('error', (err) => {
    console.error(`[Socket Error]`, err);
    leaveCurrentRoom(ws);
  });
});

/**
 * Remove socket from current room and notify remote peer
 */
function leaveCurrentRoom(ws) {
  if (!ws.roomCode) return;

  const code = ws.roomCode;
  const room = rooms.get(code);

  if (room) {
    if (ws.role === 'sender') {
      // Sender left: notify receiver and close room
      if (room.receiver && room.receiver.readyState === WebSocket.OPEN) {
        sendTo(room.receiver, {
          type: 'peer-disconnected',
          message: 'Sender has disconnected.'
        });
        room.receiver.roomCode = null;
        room.receiver.role = null;
      }
      rooms.delete(code);
      console.log(`[Room Closed] Code: ${code} (Sender disconnected)`);
    } else if (ws.role === 'receiver') {
      // Receiver left: notify sender, keep room open for new receiver
      room.receiver = null;
      if (room.sender && room.sender.readyState === WebSocket.OPEN) {
        sendTo(room.sender, {
          type: 'peer-disconnected',
          message: 'Receiver has disconnected. Waiting for new connection...'
        });
      }
      console.log(`[Receiver Left] Room: ${code} (Waiting for re-pair)`);
    }
  }

  ws.roomCode = null;
  ws.role = null;
}

// Keep-alive heartbeat (every 30 seconds to bypass mobile carrier NAT timeouts)
const interval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) {
      leaveCurrentRoom(ws);
      return ws.terminate();
    }
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

// Cleanup stale rooms (older than 2 hours)
setInterval(() => {
  const now = Date.now();
  for (const [code, room] of rooms.entries()) {
    if (now - room.createdAt > 2 * 60 * 60 * 1000) {
      if (room.sender) sendTo(room.sender, { type: 'error', message: 'Room session expired.' });
      if (room.receiver) sendTo(room.receiver, { type: 'error', message: 'Room session expired.' });
      rooms.delete(code);
    }
  }
}, 15 * 60 * 1000);

wss.on('close', () => {
  clearInterval(interval);
});

// Helper to get local network IP address
function getLocalIp() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return 'localhost';
}

server.listen(PORT, '0.0.0.0', () => {
  const localIp = getLocalIp();
  console.log(`====================================================`);
  console.log(`🚀 AirBeam P2P WebRTC Signaling Server Running!`);
  console.log(`📡 Local URL:       http://localhost:${PORT}`);
  console.log(`📱 LAN Mobile URL:  http://${localIp}:${PORT}`);
  console.log(`⚡ WebSocket URL:   ws://${localIp}:${PORT}`);
  console.log(`====================================================`);
});
