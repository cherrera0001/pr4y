/**
 * Socket.io client para Prayer Roulette.
 *
 * Singleton: una sola conexión WebSocket por sesión.
 * Auth vía JWT en handshake.
 */

import { io, Socket } from 'socket.io-client';
import { getAccessToken } from './auth-client';

let socket: Socket | null = null;

export type RouletteState = 'idle' | 'connecting' | 'waiting' | 'matched' | 'in_prayer' | 'ended';

export interface MatchedPayload {
  roomId: string;
  role: 'offerer' | 'answerer';
  peerId: string;
}

export interface SignalPayload {
  roomId: string;
  type: string;
  payload: unknown;
  from: string;
}

export function getSocket(): Socket | null {
  return socket;
}

export function connectSocket(): Socket {
  if (socket?.connected) return socket;

  const token = getAccessToken();
  if (!token) throw new Error('No auth token');

  // Conectar via Next.js rewrite proxy (/ws → backend)
  const wsUrl = window.location.origin;
  socket = io(wsUrl, {
    path: '/ws',
    auth: { token },
    transports: ['websocket', 'polling'],
    reconnection: true,
    reconnectionDelay: 1000,
    reconnectionAttempts: 5,
  });

  return socket;
}

export function disconnectSocket() {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
}

export function joinQueue(filters?: { language?: string }) {
  if (!socket?.connected) throw new Error('Socket not connected');
  socket.emit('join_queue', { filters });
}

export function leaveQueue() {
  if (!socket?.connected) return;
  socket.emit('leave_queue');
}

export function sendSignal(roomId: string, type: string, payload: unknown) {
  if (!socket?.connected) return;
  socket.emit('signal', { roomId, type, payload });
}

export function endPrayer(roomId: string) {
  if (!socket?.connected) return;
  socket.emit('end_prayer', { roomId });
}
