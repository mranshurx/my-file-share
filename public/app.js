/**
 * AirBeam P2P - WebRTC Peer-to-Peer Transfer Engine
 * 
 * Features:
 * - Public Google STUN servers for long-distance internet NAT traversal
 * - 6-Digit pairing with dynamic QR Code generation and camera scanner
 * - 16KB ArrayBuffer chunking pipeline with backpressure (bufferedAmount) control
 * - Real-time MB/s speed meter, ETA, and progress calculation
 * - Chunk reassembly into Blob, in-browser preview, and auto-download
 */

(function () {
  'use strict';

  // Constants
  const CHUNK_SIZE = 16384; // 16 KB ArrayBuffer chunk size
  const BUFFER_HIGH_WATERMARK = 64 * 1024; // 64 KB threshold for backpressure
  const RTC_CONFIG = {
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' },
      { urls: 'stun:stun3.l.google.com:19302' },
      { urls: 'stun:stun4.l.google.com:19302' }
    ]
  };

  // State
  let ws = null;
  let pc = null;
  let dataChannel = null;
  let role = 'sender'; // 'sender' | 'receiver'
  let currentRoomCode = null;
  let qrCodeInstance = null;
  let html5QrScanner = null;
  let isTransferCancelled = false;

  // Sender State
  let fileQueue = []; // Array of File objects
  let isSending = false;

  // Receiver State
  let incomingFileMeta = null;
  let incomingChunks = [];
  let incomingBytesReceived = 0;
  let incomingStartTime = 0;
  let incomingLastSampleTime = 0;
  let incomingLastSampleBytes = 0;
  let incomingSpeedBps = 0;
  let receivedFiles = []; // Reassembled file records

  // Pending ICE Candidates (if candidate arrives before remote description)
  let pendingCandidates = [];

  // DOM Elements
  const elStatusDot = document.getElementById('statusDot');
  const elStatusText = document.getElementById('statusText');
  const elTabSender = document.getElementById('tabSender');
  const elTabReceiver = document.getElementById('tabReceiver');
  const elSenderView = document.getElementById('senderView');
  const elReceiverView = document.getElementById('receiverView');

  // Sender DOM Elements
  const elSessionCodeDisplay = document.getElementById('sessionCodeDisplay');
  const elBtnCopyCode = document.getElementById('btnCopyCode');
  const elCopyBtnText = document.getElementById('copyBtnText');
  const elBtnRefreshCode = document.getElementById('btnRefreshCode');
  const elSenderPeerDot = document.getElementById('senderPeerDot');
  const elSenderPeerStatus = document.getElementById('senderPeerStatus');
  const elSenderChannelState = document.getElementById('senderChannelState');
  const elDropZone = document.getElementById('dropZone');
  const elFileInput = document.getElementById('fileInput');
  const elQueueCard = document.getElementById('queueCard');
  const elQueueCountBadge = document.getElementById('queueCountBadge');
  const elQueueTotalSizeBadge = document.getElementById('queueTotalSizeBadge');
  const elQueueList = document.getElementById('queueList');
  const elBtnClearQueue = document.getElementById('btnClearQueue');
  const elBtnStartTransfer = document.getElementById('btnStartTransfer');
  const elSenderTransferCard = document.getElementById('senderTransferCard');
  const elSenderActiveFileName = document.getElementById('senderActiveFileName');
  const elSenderActiveFileDetails = document.getElementById('senderActiveFileDetails');
  const elSenderProgressBar = document.getElementById('senderProgressBar');
  const elSenderProgressPercent = document.getElementById('senderProgressPercent');
  const elSenderChunkStatus = document.getElementById('senderChunkStatus');
  const elSenderSpeed = document.getElementById('senderSpeed');
  const elSenderEta = document.getElementById('senderEta');
  const elSenderBufferStatus = document.getElementById('senderBufferStatus');
  const elBtnCancelTransfer = document.getElementById('btnCancelTransfer');

  // Receiver DOM Elements
  const elReceiverConnectCard = document.getElementById('receiverConnectCard');
  const elDigitInputs = document.querySelectorAll('.code-input-char');
  const elBtnConnectCode = document.getElementById('btnConnectCode');
  const elBtnOpenScanner = document.getElementById('btnOpenScanner');
  const elScannerContainer = document.getElementById('scannerContainer');
  const elBtnCloseScanner = document.getElementById('btnCloseScanner');
  const elReceiverActiveBanner = document.getElementById('receiverActiveBanner');
  const elReceiverRoomBadge = document.getElementById('receiverRoomBadge');
  const elBtnDisconnectReceiver = document.getElementById('btnDisconnectReceiver');
  const elReceiverTransferCard = document.getElementById('receiverTransferCard');
  const elReceiverActiveFileName = document.getElementById('receiverActiveFileName');
  const elReceiverActiveFileDetails = document.getElementById('receiverActiveFileDetails');
  const elToggleAutoDownload = document.getElementById('toggleAutoDownload');
  const elReceiverProgressBar = document.getElementById('receiverProgressBar');
  const elReceiverProgressPercent = document.getElementById('receiverProgressPercent');
  const elReceiverChunkStatus = document.getElementById('receiverChunkStatus');
  const elReceiverSpeed = document.getElementById('receiverSpeed');
  const elReceiverEta = document.getElementById('receiverEta');
  const elReceivedFilesGrid = document.getElementById('receivedFilesGrid');
  const elReceivedCountBadge = document.getElementById('receivedCountBadge');
  const elReceivedEmptyPlaceholder = document.getElementById('receivedEmptyPlaceholder');

  // Diagnostics Modal DOM Elements
  const elBtnDiagnostics = document.getElementById('btnDiagnostics');
  const elDiagModal = document.getElementById('diagModal');
  const elBtnCloseDiag = document.getElementById('btnCloseDiag');
  const elBtnCloseDiagBtn = document.getElementById('btnCloseDiagBtn');
  const elDiagWsState = document.getElementById('diagWsState');
  const elDiagIceState = document.getElementById('diagIceState');
  const elDiagSignalingState = document.getElementById('diagSignalingState');
  const elDiagChannelState = document.getElementById('diagChannelState');

  // Toast
  const elToast = document.getElementById('toast');
  const elToastMsg = document.getElementById('toastMsg');
  const elToastIcon = document.getElementById('toastIcon');

  /**
   * Helper: Format bytes to human readable string (KB, MB, GB)
   */
  function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  }

  /**
   * Helper: Format duration in seconds to mm:ss or hh:mm:ss
   */
  function formatTime(seconds) {
    if (!isFinite(seconds) || seconds < 0) return '--:--';
    const s = Math.floor(seconds);
    const hrs = Math.floor(s / 3600);
    const mins = Math.floor((s % 3600) / 60);
    const secs = s % 60;
    if (hrs > 0) {
      return `${hrs}:${mins < 10 ? '0' : ''}${mins}:${secs < 10 ? '0' : ''}${secs}`;
    }
    return `${mins < 10 ? '0' : ''}${mins}:${secs < 10 ? '0' : ''}${secs}`;
  }

  /**
   * Toast notification helper
   */
  let toastTimer = null;
  function showToast(message, type = 'info') {
    elToastMsg.textContent = message;
    if (type === 'error') {
      elToastIcon.className = 'w-4 h-4 text-rose-400 shrink-0';
    } else if (type === 'success') {
      elToastIcon.className = 'w-4 h-4 text-emerald-400 shrink-0';
    } else {
      elToastIcon.className = 'w-4 h-4 text-sky-400 shrink-0';
    }

    elToast.classList.remove('translate-y-24', 'opacity-0');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      elToast.classList.add('translate-y-24', 'opacity-0');
    }, 3500);
  }

  /**
   * Initialize WebSockets Signaling
   */
  function initSignaling() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}`;

    elStatusDot.className = 'w-2.5 h-2.5 rounded-full bg-amber-500 animate-pulse';
    elStatusText.textContent = 'Connecting Server...';
    elDiagWsState.textContent = 'CONNECTING';
    elDiagWsState.className = 'text-amber-400';

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log('[WebSocket] Connected to signaling server');
      elStatusDot.className = 'w-2.5 h-2.5 rounded-full bg-emerald-500';
      elStatusText.textContent = 'Server Online';
      elDiagWsState.textContent = 'OPEN';
      elDiagWsState.className = 'text-emerald-400';

      // If user joined via URL query parameter '?join=123456'
      const urlParams = new URLSearchParams(window.location.search);
      const joinCode = urlParams.get('join');
      if (joinCode && /^\d{6}$/.test(joinCode)) {
        switchMode('receiver');
        setDigitInputs(joinCode);
        joinRoom(joinCode);
      } else if (role === 'sender') {
        createSenderRoom();
      }
    };

    ws.onmessage = async (event) => {
      try {
        const msg = JSON.parse(event.data);
        handleSignalingMessage(msg);
      } catch (err) {
        console.error('[WebSocket] Failed to parse message', err);
      }
    };

    ws.onclose = () => {
      console.warn('[WebSocket] Disconnected from server');
      elStatusDot.className = 'w-2.5 h-2.5 rounded-full bg-rose-500';
      elStatusText.textContent = 'Disconnected';
      elDiagWsState.textContent = 'CLOSED';
      elDiagWsState.className = 'text-rose-400';
      // Attempt reconnect after 3 seconds
      setTimeout(initSignaling, 3000);
    };

    ws.onerror = (err) => {
      console.error('[WebSocket] Error', err);
      elDiagWsState.textContent = 'ERROR';
      elDiagWsState.className = 'text-rose-400';
    };
  }

  function sendSignaling(msg) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }

  /**
   * Handle incoming messages from Signaling Server
   */
  async function handleSignalingMessage(msg) {
    switch (msg.type) {
      case 'room-created': {
        currentRoomCode = msg.code;
        elSessionCodeDisplay.textContent = currentRoomCode;
        renderQRCode(currentRoomCode);
        elSenderPeerDot.className = 'w-2.5 h-2.5 rounded-full bg-amber-500 animate-pulse';
        elSenderPeerStatus.textContent = 'Waiting for receiver phone to pair...';
        elSenderChannelState.textContent = 'Channel: waiting';
        break;
      }

      case 'room-joined': {
        currentRoomCode = msg.code;
        elReceiverRoomBadge.textContent = `Room: #${currentRoomCode}`;
        elReceiverConnectCard.classList.add('hidden');
        elReceiverActiveBanner.classList.remove('hidden');
        showToast(`Connected to Room #${currentRoomCode}`, 'success');
        break;
      }

      case 'peer-joined': {
        // Receiver connected to sender! Sender creates WebRTC Peer Connection & DataChannel
        showToast('Receiver paired! Establishing WebRTC connection...', 'info');
        elSenderPeerDot.className = 'w-2.5 h-2.5 rounded-full bg-sky-500 animate-pulse';
        elSenderPeerStatus.textContent = 'Pairing WebRTC via STUN...';
        await setupSenderPeerConnection();
        break;
      }

      case 'offer': {
        // Receiver received SDP Offer from Sender
        await setupReceiverPeerConnection(msg.offer);
        break;
      }

      case 'answer': {
        // Sender received SDP Answer from Receiver
        if (pc) {
          await pc.setRemoteDescription(new RTCSessionDescription(msg.answer));
          drainPendingCandidates();
        }
        break;
      }

      case 'ice-candidate': {
        // Received ICE candidate from remote peer
        if (msg.candidate) {
          const candidate = new RTCIceCandidate(msg.candidate);
          if (pc && pc.remoteDescription && pc.remoteDescription.type) {
            try {
              await pc.addIceCandidate(candidate);
            } catch (err) {
              console.error('[ICE] Error adding candidate', err);
            }
          } else {
            pendingCandidates.push(candidate);
          }
        }
        break;
      }

      case 'peer-disconnected': {
        showToast(msg.message || 'Remote peer disconnected', 'error');
        resetPeerState();
        break;
      }

      case 'error': {
        showToast(msg.message, 'error');
        break;
      }
    }
  }

  function drainPendingCandidates() {
    while (pendingCandidates.length > 0) {
      const cand = pendingCandidates.shift();
      if (pc) pc.addIceCandidate(cand).catch(console.error);
    }
  }

  /**
   * Sender: Create Room on Signaling Server
   */
  function createSenderRoom() {
    sendSignaling({ type: 'create-room' });
  }

  /**
   * Receiver: Join Room
   */
  function joinRoom(code) {
    if (!code || code.length !== 6) {
      showToast('Please enter a valid 6-digit code', 'error');
      return;
    }
    sendSignaling({ type: 'join-room', code });
  }

  /**
   * Render dynamic QR Code for Sender
   */
  function renderQRCode(code) {
    const qrContainer = document.getElementById('qrcode');
    qrContainer.innerHTML = '';
    const pairUrl = `${window.location.origin}${window.location.pathname}?join=${code}`;
    
    qrCodeInstance = new QRCode(qrContainer, {
      text: pairUrl,
      width: 136,
      height: 136,
      colorDark: '#0284c7',
      colorLight: '#ffffff',
      correctLevel: QRCode.CorrectLevel.M
    });
  }

  /**
   * Sender: Initialize RTCPeerConnection and create RTCDataChannel
   */
  async function setupSenderPeerConnection() {
    closePeerConnection();

    pc = new RTCPeerConnection(RTC_CONFIG);
    attachPcDiagnostics(pc);

    // Sender creates the RTCDataChannel named "file-transfer"
    dataChannel = pc.createDataChannel('file-transfer', {
      ordered: true
    });
    dataChannel.binaryType = 'arraybuffer';
    dataChannel.bufferedAmountLowThreshold = BUFFER_HIGH_WATERMARK;

    setupDataChannelEvents(dataChannel);

    // ICE Candidate handler
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignaling({
          type: 'ice-candidate',
          candidate: event.candidate
        });
      }
    };

    // Create SDP Offer
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);

    sendSignaling({
      type: 'offer',
      offer: pc.localDescription
    });
  }

  /**
   * Receiver: Initialize RTCPeerConnection upon receiving offer
   */
  async function setupReceiverPeerConnection(offer) {
    closePeerConnection();

    pc = new RTCPeerConnection(RTC_CONFIG);
    attachPcDiagnostics(pc);

    // Receiver waits for data channel from Sender
    pc.ondatachannel = (event) => {
      dataChannel = event.channel;
      dataChannel.binaryType = 'arraybuffer';
      dataChannel.bufferedAmountLowThreshold = BUFFER_HIGH_WATERMARK;
      setupDataChannelEvents(dataChannel);
    };

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignaling({
          type: 'ice-candidate',
          candidate: event.candidate
        });
      }
    };

    await pc.setRemoteDescription(new RTCSessionDescription(offer));
    drainPendingCandidates();

    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);

    sendSignaling({
      type: 'answer',
      answer: pc.localDescription
    });
  }

  /**
   * Attach diagnostics listeners to RTCPeerConnection
   */
  function attachPcDiagnostics(peer) {
    peer.oniceconnectionstatechange = () => {
      elDiagIceState.textContent = peer.iceConnectionState;
      if (peer.iceConnectionState === 'connected' || peer.iceConnectionState === 'completed') {
        elDiagIceState.className = 'text-emerald-400';
      } else if (peer.iceConnectionState === 'disconnected' || peer.iceConnectionState === 'failed') {
        elDiagIceState.className = 'text-rose-400';
      }
    };

    peer.onsignalingstatechange = () => {
      elDiagSignalingState.textContent = peer.signalingState;
    };
  }

  /**
   * Bind event listeners to RTCDataChannel
   */
  function setupDataChannelEvents(channel) {
    channel.onopen = () => {
      console.log('[DataChannel] Opened successfully!');
      elDiagChannelState.textContent = 'OPEN';
      elDiagChannelState.className = 'text-emerald-400';
      elStatusDot.className = 'w-2.5 h-2.5 rounded-full bg-emerald-500';
      elStatusText.textContent = 'P2P Connected';

      if (role === 'sender') {
        elSenderPeerDot.className = 'w-2.5 h-2.5 rounded-full bg-emerald-500';
        elSenderPeerStatus.textContent = 'Receiver connected via WebRTC!';
        elSenderChannelState.textContent = 'Channel: active';
        elBtnStartTransfer.disabled = fileQueue.length === 0;
      }
      showToast('P2P Direct Channel Active! Ready to transfer.', 'success');
    };

    channel.onclose = () => {
      console.log('[DataChannel] Closed');
      elDiagChannelState.textContent = 'CLOSED';
      elDiagChannelState.className = 'text-slate-400';
      if (role === 'sender') {
        elSenderPeerDot.className = 'w-2.5 h-2.5 rounded-full bg-amber-500';
        elSenderPeerStatus.textContent = 'Waiting for receiver...';
        elSenderChannelState.textContent = 'Channel: closed';
      }
    };

    channel.onerror = (err) => {
      console.error('[DataChannel] Error', err);
      showToast('Data channel encountered an error', 'error');
    };

    // Receiver incoming message handler
    channel.onmessage = (event) => {
      handleDataChannelMessage(event);
    };
  }

  /**
   * Close and clean up current RTCPeerConnection
   */
  function closePeerConnection() {
    if (dataChannel) {
      try { dataChannel.close(); } catch (_) {}
      dataChannel = null;
    }
    if (pc) {
      try { pc.close(); } catch (_) {}
      pc = null;
    }
    pendingCandidates = [];
  }

  function resetPeerState() {
    closePeerConnection();
    if (role === 'sender') {
      elSenderPeerDot.className = 'w-2.5 h-2.5 rounded-full bg-amber-500 animate-pulse';
      elSenderPeerStatus.textContent = 'Waiting for receiver phone to pair...';
      elSenderChannelState.textContent = 'Channel: inactive';
      elBtnStartTransfer.disabled = true;
    } else {
      elReceiverActiveBanner.classList.add('hidden');
      elReceiverConnectCard.classList.remove('hidden');
    }
    elStatusText.textContent = 'Server Online';
    elStatusDot.className = 'w-2.5 h-2.5 rounded-full bg-emerald-500';
  }

  /**
   * Backpressure Controller:
   * Await bufferedamountlow if the WebRTC buffer exceeds 64KB
   */
  function waitForBackpressure(channel) {
    if (!channel || channel.readyState !== 'open') {
      return Promise.reject(new Error('Channel closed'));
    }

    if (channel.bufferedAmount <= BUFFER_HIGH_WATERMARK) {
      elSenderBufferStatus.textContent = 'Normal';
      elSenderBufferStatus.className = 'text-base font-bold text-emerald-400 font-mono mt-0.5';
      return Promise.resolve();
    }

    // Buffer is full: apply backpressure throttle
    elSenderBufferStatus.textContent = 'Throttling';
    elSenderBufferStatus.className = 'text-base font-bold text-amber-400 font-mono mt-0.5';

    return new Promise((resolve) => {
      const onLow = () => {
        channel.removeEventListener('bufferedamountlow', onLow);
        resolve();
      };
      channel.addEventListener('bufferedamountlow', onLow);
    });
  }

  /**
   * SENDER: Transmit File Queue over WebRTC Data Channel
   */
  async function startQueueTransfer() {
    if (!dataChannel || dataChannel.readyState !== 'open') {
      showToast('WebRTC channel not connected yet. Pair with receiver first.', 'error');
      return;
    }

    if (fileQueue.length === 0) {
      showToast('Queue is empty. Select files first.', 'info');
      return;
    }

    if (isSending) return;
    isSending = true;
    isTransferCancelled = false;

    elSenderTransferCard.classList.remove('hidden');
    elBtnStartTransfer.disabled = true;
    elBtnClearQueue.disabled = true;

    for (let i = 0; i < fileQueue.length; i++) {
      if (isTransferCancelled) break;

      const file = fileQueue[i];
      updateQueueItemStatus(i, 'sending');
      await sendSingleFile(file, i);
      updateQueueItemStatus(i, 'completed');
    }

    isSending = false;
    elBtnClearQueue.disabled = false;
    elBtnStartTransfer.disabled = fileQueue.length === 0;

    if (!isTransferCancelled) {
      showToast('All files transferred successfully!', 'success');
      setTimeout(() => {
        elSenderTransferCard.classList.add('hidden');
      }, 4000);
    }
  }

  /**
   * SENDER: Stream a single file in 16KB chunks with backpressure
   */
  async function sendSingleFile(file, queueIndex) {
    elSenderActiveFileName.textContent = file.name;
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    const fileId = `file-${Date.now()}-${queueIndex}`;

    // 1. Send File Metadata Header
    const metaMessage = JSON.stringify({
      type: 'file-meta',
      id: fileId,
      name: file.name,
      size: file.size,
      mimeType: file.type || 'application/octet-stream',
      totalChunks: totalChunks
    });

    dataChannel.send(metaMessage);

    let offset = 0;
    let chunkIndex = 0;
    let bytesSent = 0;
    const startTime = performance.now();
    let lastSampleTime = startTime;
    let lastSampleBytes = 0;

    while (offset < file.size) {
      if (isTransferCancelled) {
        dataChannel.send(JSON.stringify({ type: 'file-cancel', id: fileId }));
        return;
      }

      // Check Backpressure
      await waitForBackpressure(dataChannel);

      const slice = file.slice(offset, offset + CHUNK_SIZE);
      const arrayBuffer = await slice.arrayBuffer();

      dataChannel.send(arrayBuffer);

      offset += CHUNK_SIZE;
      chunkIndex++;
      bytesSent += arrayBuffer.byteLength;

      // Real-time speed and ETA calculations every ~250ms
      const now = performance.now();
      const elapsedSinceSample = (now - lastSampleTime) / 1000;
      if (elapsedSinceSample >= 0.25 || bytesSent >= file.size) {
        const bytesSinceSample = bytesSent - lastSampleBytes;
        const speedBps = bytesSinceSample / elapsedSinceSample; // Bytes per sec
        const remainingBytes = Math.max(0, file.size - bytesSent);
        const etaSeconds = speedBps > 0 ? remainingBytes / speedBps : 0;

        // UI Updates
        const percent = Math.min(100, Math.round((bytesSent / file.size) * 100));
        elSenderProgressBar.style.width = `${percent}%`;
        elSenderProgressPercent.textContent = `${percent}%`;
        elSenderActiveFileDetails.textContent = `${formatBytes(bytesSent)} / ${formatBytes(file.size)} (${percent}%)`;
        elSenderChunkStatus.textContent = `Chunk ${chunkIndex} / ${totalChunks}`;
        elSenderSpeed.textContent = `${(speedBps / (1024 * 1024)).toFixed(2)} MB/s`;
        elSenderEta.textContent = formatTime(etaSeconds);

        lastSampleTime = now;
        lastSampleBytes = bytesSent;
      }
    }

    // 2. Send File End Marker
    dataChannel.send(JSON.stringify({
      type: 'file-end',
      id: fileId
    }));

    // 3. Await acknowledgment from Receiver before advancing to next file
    await new Promise((resolve) => {
      const onAck = (event) => {
        if (typeof event.data === 'string') {
          try {
            const data = JSON.parse(event.data);
            if (data.type === 'file-ack' && data.id === fileId) {
              dataChannel.removeEventListener('message', onAck);
              resolve();
            }
          } catch (_) {}
        }
      };
      dataChannel.addEventListener('message', onAck);
      // Fallback timeout in case of packet loss
      setTimeout(() => {
        dataChannel.removeEventListener('message', onAck);
        resolve();
      }, 3000);
    });
  }

  /**
   * RECEIVER: Handle incoming data from Data Channel
   */
  function handleDataChannelMessage(event) {
    // 1. Text Protocol Messages (Metadata / Control)
    if (typeof event.data === 'string') {
      try {
        const msg = JSON.parse(event.data);
        handleControlMessage(msg);
      } catch (err) {
        console.error('[Receiver] Error parsing control message', err);
      }
      return;
    }

    // 2. Binary ArrayBuffer Chunks
    if (event.data instanceof ArrayBuffer) {
      if (!incomingFileMeta) return;

      incomingChunks.push(event.data);
      incomingBytesReceived += event.data.byteLength;

      // Real-time speed and progress calculation
      const now = performance.now();
      const elapsed = (now - incomingLastSampleTime) / 1000;
      if (elapsed >= 0.25 || incomingBytesReceived >= incomingFileMeta.size) {
        const deltaBytes = incomingBytesReceived - incomingLastSampleBytes;
        incomingSpeedBps = deltaBytes / elapsed;
        const remainingBytes = Math.max(0, incomingFileMeta.size - incomingBytesReceived);
        const etaSeconds = incomingSpeedBps > 0 ? remainingBytes / incomingSpeedBps : 0;

        const percent = Math.min(100, Math.round((incomingBytesReceived / incomingFileMeta.size) * 100));
        elReceiverProgressBar.style.width = `${percent}%`;
        elReceiverProgressPercent.textContent = `${percent}%`;
        elReceiverActiveFileDetails.textContent = `${formatBytes(incomingBytesReceived)} / ${formatBytes(incomingFileMeta.size)} (${percent}%)`;
        elReceiverChunkStatus.textContent = `Chunk ${incomingChunks.length} / ${incomingFileMeta.totalChunks}`;
        elReceiverSpeed.textContent = `${(incomingSpeedBps / (1024 * 1024)).toFixed(2)} MB/s`;
        elReceiverEta.textContent = formatTime(etaSeconds);

        incomingLastSampleTime = now;
        incomingLastSampleBytes = incomingBytesReceived;
      }
    }
  }

  function handleControlMessage(msg) {
    switch (msg.type) {
      case 'file-meta': {
        incomingFileMeta = msg;
        incomingChunks = [];
        incomingBytesReceived = 0;
        incomingStartTime = performance.now();
        incomingLastSampleTime = incomingStartTime;
        incomingLastSampleBytes = 0;

        elReceiverTransferCard.classList.remove('hidden');
        elReceiverActiveFileName.textContent = msg.name;
        elReceiverProgressBar.style.width = '0%';
        elReceiverProgressPercent.textContent = '0%';
        elReceiverActiveFileDetails.textContent = `0 KB / ${formatBytes(msg.size)} (0%)`;
        elReceiverChunkStatus.textContent = `Chunk 0 / ${msg.totalChunks}`;
        elReceiverSpeed.textContent = '0.0 MB/s';
        elReceiverEta.textContent = '--:--';
        break;
      }

      case 'file-end': {
        if (!incomingFileMeta) return;

        // Reassemble incoming chunks into a unified Blob
        const blob = new Blob(incomingChunks, { type: incomingFileMeta.mimeType });
        const downloadUrl = URL.createObjectURL(blob);
        const receivedRecord = {
          id: incomingFileMeta.id,
          name: incomingFileMeta.name,
          size: incomingFileMeta.size,
          mimeType: incomingFileMeta.mimeType,
          blob: blob,
          url: downloadUrl,
          timestamp: new Date().toLocaleTimeString()
        };

        receivedFiles.unshift(receivedRecord);
        renderReceivedFiles();

        // Automatic browser download trigger
        if (elToggleAutoDownload.checked) {
          triggerFileDownload(receivedRecord);
        }

        // Send ACK back to Sender so they can proceed
        if (dataChannel && dataChannel.readyState === 'open') {
          dataChannel.send(JSON.stringify({
            type: 'file-ack',
            id: incomingFileMeta.id,
            status: 'ok'
          }));
        }

        showToast(`Received: ${incomingFileMeta.name}`, 'success');

        // Reset incoming state
        incomingFileMeta = null;
        incomingChunks = [];
        setTimeout(() => {
          elReceiverTransferCard.classList.add('hidden');
        }, 3000);
        break;
      }

      case 'file-cancel': {
        showToast('Transfer was cancelled by sender', 'error');
        incomingFileMeta = null;
        incomingChunks = [];
        elReceiverTransferCard.classList.add('hidden');
        break;
      }
    }
  }

  /**
   * Trigger native browser download
   */
  function triggerFileDownload(record) {
    const a = document.createElement('a');
    a.href = record.url;
    a.download = record.name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  /**
   * Render Received Files Gallery & Download Cards
   */
  function renderReceivedFiles() {
    elReceivedCountBadge.textContent = `${receivedFiles.length} items`;
    if (receivedFiles.length === 0) {
      elReceivedEmptyPlaceholder.classList.remove('hidden');
      elReceivedFilesGrid.innerHTML = '';
      return;
    }

    elReceivedEmptyPlaceholder.classList.add('hidden');
    elReceivedFilesGrid.innerHTML = '';

    receivedFiles.forEach((file) => {
      const card = document.createElement('div');
      card.className = 'p-3.5 bg-slate-950 rounded-2xl border border-slate-800 flex items-center justify-between gap-3 hover:border-slate-700 transition';

      const isImage = file.mimeType.startsWith('image/');
      const isVideo = file.mimeType.startsWith('video/');
      const isAudio = file.mimeType.startsWith('audio/');

      let previewHtml = '';
      if (isImage) {
        previewHtml = `<img src="${file.url}" class="w-12 h-12 rounded-xl object-cover border border-slate-800" alt="Preview" />`;
      } else if (isVideo) {
        previewHtml = `<div class="w-12 h-12 rounded-xl bg-purple-500/10 text-purple-400 flex items-center justify-center shrink-0"><i data-lucide="video" class="w-6 h-6"></i></div>`;
      } else if (isAudio) {
        previewHtml = `<div class="w-12 h-12 rounded-xl bg-amber-500/10 text-amber-400 flex items-center justify-center shrink-0"><i data-lucide="music" class="w-6 h-6"></i></div>`;
      } else {
        previewHtml = `<div class="w-12 h-12 rounded-xl bg-sky-500/10 text-sky-400 flex items-center justify-center shrink-0"><i data-lucide="file" class="w-6 h-6"></i></div>`;
      }

      card.innerHTML = `
        <div class="flex items-center gap-3 min-w-0">
          ${previewHtml}
          <div class="min-w-0">
            <h4 class="font-medium text-xs text-white truncate max-w-[180px] sm:max-w-xs">${file.name}</h4>
            <div class="flex items-center gap-2 text-[10px] text-slate-400 font-mono mt-0.5">
              <span>${formatBytes(file.size)}</span>
              <span>•</span>
              <span>${file.timestamp}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2 shrink-0">
          <button data-id="${file.id}" class="btn-download-item p-2 sm:px-3 sm:py-1.5 rounded-xl bg-sky-500 hover:bg-sky-400 text-white text-xs font-medium flex items-center gap-1.5 transition shadow-md shadow-sky-500/20">
            <i data-lucide="download" class="w-3.5 h-3.5"></i>
            <span class="hidden sm:inline">Save</span>
          </button>
        </div>
      `;

      card.querySelector('.btn-download-item').addEventListener('click', () => {
        triggerFileDownload(file);
      });

      elReceivedFilesGrid.appendChild(card);
    });

    if (window.lucide) lucide.createIcons();
  }

  /**
   * File Queue Management (Sender)
   */
  function addFilesToQueue(files) {
    if (!files || files.length === 0) return;
    for (let i = 0; i < files.length; i++) {
      fileQueue.push(files[i]);
    }
    renderQueue();
  }

  function renderQueue() {
    if (fileQueue.length === 0) {
      elQueueCard.classList.add('hidden');
      elBtnStartTransfer.disabled = true;
      return;
    }

    elQueueCard.classList.remove('hidden');
    elQueueCountBadge.textContent = `${fileQueue.length} ${fileQueue.length === 1 ? 'file' : 'files'}`;
    const totalBytes = fileQueue.reduce((acc, f) => acc + f.size, 0);
    elQueueTotalSizeBadge.textContent = `(${formatBytes(totalBytes)})`;
    elBtnStartTransfer.disabled = !dataChannel || dataChannel.readyState !== 'open';

    elQueueList.innerHTML = '';
    fileQueue.forEach((file, index) => {
      const item = document.createElement('div');
      item.id = `queue-item-${index}`;
      item.className = 'p-3 bg-slate-950 rounded-xl border border-slate-800/80 flex items-center justify-between text-xs';
      item.innerHTML = `
        <div class="flex items-center gap-2.5 min-w-0">
          <i data-lucide="file" class="w-4 h-4 text-sky-400 shrink-0"></i>
          <span class="font-medium text-slate-200 truncate max-w-[180px] sm:max-w-xs">${file.name}</span>
          <span class="text-slate-500 font-mono text-[11px] shrink-0">(${formatBytes(file.size)})</span>
        </div>
        <div class="flex items-center gap-2 shrink-0">
          <span id="queue-status-${index}" class="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-400">Ready</span>
          <button data-index="${index}" class="btn-remove-queue text-slate-500 hover:text-rose-400 p-1">
            <i data-lucide="trash-2" class="w-3.5 h-3.5"></i>
          </button>
        </div>
      `;

      item.querySelector('.btn-remove-queue').addEventListener('click', (e) => {
        if (isSending) return;
        const idx = parseInt(e.currentTarget.getAttribute('data-index'), 10);
        fileQueue.splice(idx, 1);
        renderQueue();
      });

      elQueueList.appendChild(item);
    });

    if (window.lucide) lucide.createIcons();
  }

  function updateQueueItemStatus(index, status) {
    const el = document.getElementById(`queue-status-${index}`);
    if (!el) return;
    if (status === 'sending') {
      el.textContent = 'Transferring...';
      el.className = 'text-[10px] font-mono px-2 py-0.5 rounded bg-sky-500/20 text-sky-400 animate-pulse';
    } else if (status === 'completed') {
      el.textContent = 'Sent';
      el.className = 'text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400';
    } else if (status === 'error') {
      el.textContent = 'Failed';
      el.className = 'text-[10px] font-mono px-2 py-0.5 rounded bg-rose-500/20 text-rose-400';
    }
  }

  /**
   * UI Navigation & Tab Switching
   */
  function switchMode(newMode) {
    role = newMode;
    if (newMode === 'sender') {
      elTabSender.className = 'flex items-center justify-center gap-2 py-3 px-4 rounded-xl font-semibold text-sm transition-all duration-200 bg-sky-500 text-white shadow-lg shadow-sky-500/25';
      elTabReceiver.className = 'flex items-center justify-center gap-2 py-3 px-4 rounded-xl font-semibold text-sm transition-all duration-200 text-slate-400 hover:text-slate-200';
      elSenderView.classList.remove('hidden');
      elReceiverView.classList.add('hidden');
      stopCameraScanner();
      if (!currentRoomCode && ws && ws.readyState === WebSocket.OPEN) {
        createSenderRoom();
      }
    } else {
      elTabReceiver.className = 'flex items-center justify-center gap-2 py-3 px-4 rounded-xl font-semibold text-sm transition-all duration-200 bg-sky-500 text-white shadow-lg shadow-sky-500/25';
      elTabSender.className = 'flex items-center justify-center gap-2 py-3 px-4 rounded-xl font-semibold text-sm transition-all duration-200 text-slate-400 hover:text-slate-200';
      elReceiverView.classList.remove('hidden');
      elSenderView.classList.add('hidden');
      elDigitInputs[0].focus();
    }
  }

  /**
   * Code digit input behavior (Auto-advance & backspace)
   */
  function setupDigitInputs() {
    elDigitInputs.forEach((input, index) => {
      input.addEventListener('input', (e) => {
        const val = e.target.value.replace(/[^0-9]/g, '');
        e.target.value = val;
        if (val && index < elDigitInputs.length - 1) {
          elDigitInputs[index + 1].focus();
        }
        checkAutoSubmitCode();
      });

      input.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && !input.value && index > 0) {
          elDigitInputs[index - 1].focus();
        }
      });

      input.addEventListener('paste', (e) => {
        e.preventDefault();
        const pasted = e.clipboardData.getData('text').trim().replace(/[^0-9]/g, '');
        if (pasted.length >= 6) {
          setDigitInputs(pasted.substring(0, 6));
          checkAutoSubmitCode();
        }
      });
    });
  }

  function setDigitInputs(code) {
    const chars = code.split('');
    elDigitInputs.forEach((input, i) => {
      input.value = chars[i] || '';
    });
  }

  function getEnteredCode() {
    let code = '';
    elDigitInputs.forEach(i => code += i.value);
    return code;
  }

  function checkAutoSubmitCode() {
    const code = getEnteredCode();
    if (code.length === 6) {
      joinRoom(code);
    }
  }

  /**
   * HTML5 Camera QR Code Scanner for Mobile Receiver
   */
  async function startCameraScanner() {
    elScannerContainer.classList.remove('hidden');
    try {
      html5QrScanner = new Html5Qrcode('qrReader');
      await html5QrScanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 220, height: 220 } },
        (decodedText) => {
          stopCameraScanner();
          // Extract 6-digit code from URL or text
          let matched = null;
          try {
            const url = new URL(decodedText);
            matched = url.searchParams.get('join');
          } catch (_) {}

          if (!matched) {
            const digitMatch = decodedText.match(/\b\d{6}\b/);
            if (digitMatch) matched = digitMatch[0];
          }

          if (matched && matched.length === 6) {
            setDigitInputs(matched);
            joinRoom(matched);
          } else {
            showToast('QR code scanned, but no 6-digit session code found', 'error');
          }
        },
        () => {}
      );
    } catch (err) {
      console.error('[Scanner] Camera error', err);
      showToast('Camera permission denied or camera unavailable', 'error');
      elScannerContainer.classList.add('hidden');
    }
  }

  function stopCameraScanner() {
    if (html5QrScanner) {
      html5QrScanner.stop().then(() => {
        html5QrScanner.clear();
        html5QrScanner = null;
      }).catch(console.error);
    }
    elScannerContainer.classList.add('hidden');
  }

  /**
   * Global Event Listeners Setup
   */
  function setupEventListeners() {
    // Mode Switchers
    elTabSender.addEventListener('click', () => switchMode('sender'));
    elTabReceiver.addEventListener('click', () => switchMode('receiver'));

    // Sender Session Code Copy & Refresh
    elBtnCopyCode.addEventListener('click', () => {
      if (currentRoomCode) {
        navigator.clipboard.writeText(currentRoomCode).then(() => {
          elCopyBtnText.textContent = 'Copied!';
          setTimeout(() => { elCopyBtnText.textContent = 'Copy Code'; }, 2000);
        });
      }
    });

    elBtnRefreshCode.addEventListener('click', () => {
      createSenderRoom();
    });

    // File Drag & Drop
    elDropZone.addEventListener('dragover', (e) => {
      e.preventDefault();
      elDropZone.classList.add('border-sky-500', 'bg-slate-900/80');
    });

    elDropZone.addEventListener('dragleave', () => {
      elDropZone.classList.remove('border-sky-500', 'bg-slate-900/80');
    });

    elDropZone.addEventListener('drop', (e) => {
      e.preventDefault();
      elDropZone.classList.remove('border-sky-500', 'bg-slate-900/80');
      if (e.dataTransfer && e.dataTransfer.files) {
        addFilesToQueue(e.dataTransfer.files);
      }
    });

    elFileInput.addEventListener('change', (e) => {
      if (e.target.files) {
        addFilesToQueue(e.target.files);
      }
    });

    // Queue actions
    elBtnClearQueue.addEventListener('click', () => {
      if (isSending) return;
      fileQueue = [];
      renderQueue();
    });

    elBtnStartTransfer.addEventListener('click', () => {
      startQueueTransfer();
    });

    elBtnCancelTransfer.addEventListener('click', () => {
      isTransferCancelled = true;
      elSenderTransferCard.classList.add('hidden');
      showToast('Transfer stopped', 'info');
    });

    // Receiver actions
    elBtnConnectCode.addEventListener('click', () => {
      const code = getEnteredCode();
      joinRoom(code);
    });

    elBtnOpenScanner.addEventListener('click', () => {
      startCameraScanner();
    });

    elBtnCloseScanner.addEventListener('click', () => {
      stopCameraScanner();
    });

    elBtnDisconnectReceiver.addEventListener('click', () => {
      resetPeerState();
    });

    // Diagnostics modal
    elBtnDiagnostics.addEventListener('click', () => {
      elDiagModal.classList.remove('hidden');
    });
    elBtnCloseDiag.addEventListener('click', () => {
      elDiagModal.classList.add('hidden');
    });
    elBtnCloseDiagBtn.addEventListener('click', () => {
      elDiagModal.classList.add('hidden');
    });
    elDiagModal.addEventListener('click', (e) => {
      if (e.target === elDiagModal) elDiagModal.classList.add('hidden');
    });
  }

  // Initial Boot
  window.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) lucide.createIcons();
    setupDigitInputs();
    setupEventListeners();
    initSignaling();
  });

})();
