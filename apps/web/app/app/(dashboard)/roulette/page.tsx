'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { toast } from 'sonner';
import {
  Loader2,
  Phone,
  PhoneOff,
  UserPlus,
  Flag,
  Mic,
  MicOff,
} from 'lucide-react';
import {
  connectSocket,
  disconnectSocket,
  joinQueue,
  leaveQueue,
  sendSignal,
  endPrayer,
  type MatchedPayload,
  type SignalPayload,
  type RouletteState,
} from '@/lib/roulette-socket';
import { authFetch } from '@/lib/auth-client';

// STUN servers (públicos). TURN se agrega cuando esté configurado.
const ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
];

export default function RoulettePage() {
  const [state, setState] = useState<RouletteState>('idle');
  const [roomId, setRoomId] = useState<string | null>(null);
  const [role, setRole] = useState<'offerer' | 'answerer' | null>(null);
  const [timer, setTimer] = useState(0);
  const [muted, setMuted] = useState(false);
  const [queueCount, setQueueCount] = useState(0);

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanup();
    };
  }, []);

  const cleanup = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    if (pcRef.current) {
      pcRef.current.close();
      pcRef.current = null;
    }
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((t) => t.stop());
      localStreamRef.current = null;
    }
    disconnectSocket();
    setState('idle');
    setRoomId(null);
    setRole(null);
    setTimer(0);
  }, []);

  const startMatching = useCallback(async () => {
    setState('connecting');
    try {
      const sock = connectSocket();

      sock.on('queue_status', (data: { status: string }) => {
        if (data.status === 'waiting') setState('waiting');
        if (data.status === 'left') setState('idle');
      });

      sock.on('matched', async (data: MatchedPayload) => {
        setState('matched');
        setRoomId(data.roomId);
        setRole(data.role);
        await setupWebRTC(data);
      });

      sock.on('signal', (data: SignalPayload) => {
        handleSignal(data);
      });

      sock.on('prayer_ended', () => {
        toast.info('La sesión ha terminado');
        cleanup();
      });

      sock.on('partner_disconnected', () => {
        toast.info('Tu compañero se desconectó');
        cleanup();
      });

      sock.on('error', (data: { message: string }) => {
        toast.error(data.message);
      });

      sock.on('connect_error', () => {
        toast.error('Error de conexión');
        cleanup();
      });

      joinQueue();
    } catch (err) {
      toast.error('Error al conectar');
      setState('idle');
    }
  }, [cleanup]);

  const setupWebRTC = useCallback(async (matchData: MatchedPayload) => {
    try {
      // Obtener audio
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      localStreamRef.current = stream;

      const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
      pcRef.current = pc;

      // Agregar tracks locales
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));

      // Recibir tracks remotos
      pc.ontrack = (event) => {
        if (remoteAudioRef.current) {
          remoteAudioRef.current.srcObject = event.streams[0];
        }
      };

      // ICE candidates
      pc.onicecandidate = (event) => {
        if (event.candidate) {
          sendSignal(matchData.roomId, 'ice-candidate', event.candidate.toJSON());
        }
      };

      pc.onconnectionstatechange = () => {
        if (pc.connectionState === 'connected') {
          setState('in_prayer');
          // Iniciar timer
          timerRef.current = setInterval(() => {
            setTimer((prev) => prev + 1);
          }, 1000);
        }
        if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
          toast.error('Conexión perdida');
          cleanup();
        }
      };

      // Si soy offerer, creo la oferta
      if (matchData.role === 'offerer') {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        sendSignal(matchData.roomId, 'offer', offer);
      }
    } catch (err) {
      toast.error('Error al acceder al micrófono');
      cleanup();
    }
  }, [cleanup]);

  const handleSignal = useCallback(async (data: SignalPayload) => {
    const pc = pcRef.current;
    if (!pc) return;

    if (data.type === 'offer') {
      await pc.setRemoteDescription(new RTCSessionDescription(data.payload as RTCSessionDescriptionInit));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      sendSignal(data.roomId, 'answer', answer);
    } else if (data.type === 'answer') {
      await pc.setRemoteDescription(new RTCSessionDescription(data.payload as RTCSessionDescriptionInit));
    } else if (data.type === 'ice-candidate') {
      await pc.addIceCandidate(new RTCIceCandidate(data.payload as RTCIceCandidateInit));
    }
  }, []);

  const handleEndPrayer = useCallback(() => {
    if (roomId) endPrayer(roomId);
    cleanup();
  }, [roomId, cleanup]);

  const handleCancel = useCallback(() => {
    leaveQueue();
    cleanup();
  }, [cleanup]);

  const toggleMute = useCallback(() => {
    if (localStreamRef.current) {
      const audioTrack = localStreamRef.current.getAudioTracks()[0];
      if (audioTrack) {
        audioTrack.enabled = !audioTrack.enabled;
        setMuted(!audioTrack.enabled);
      }
    }
  }, []);

  const handleAddPartner = useCallback(async () => {
    if (!roomId) return;
    try {
      const res = await authFetch(`/rooms/${roomId}/summary`);
      if (!res.ok) return;
      const summary = await res.json();
      if (summary.partnerId) {
        const addRes = await authFetch(`/partners/${summary.partnerId}`, { method: 'POST' });
        if (addRes.ok) {
          toast.success('Compañero de oración agregado');
        }
      }
    } catch {
      toast.error('Error al agregar compañero');
    }
  }, [roomId]);

  const handleReport = useCallback(async () => {
    if (!roomId) return;
    try {
      const res = await authFetch(`/rooms/${roomId}/report`, {
        method: 'POST',
        body: JSON.stringify({ reason: 'inappropriate' }),
      });
      if (res.ok) {
        toast.success('Reporte enviado');
        handleEndPrayer();
      }
    } catch {
      toast.error('Error al reportar');
    }
  }, [roomId, handleEndPrayer]);

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-foreground">Prayer Roulette</h1>

      {/* Audio element para recibir audio remoto */}
      <audio ref={remoteAudioRef} autoPlay playsInline className="hidden" />

      {state === 'idle' && (
        <Card className="bg-card/50">
          <CardContent className="flex flex-col items-center py-12 space-y-6">
            <div className="size-24 rounded-full bg-primary/10 flex items-center justify-center">
              <Phone className="size-10 text-primary" />
            </div>
            <div className="text-center space-y-2">
              <h2 className="text-lg font-semibold text-foreground">
                Ora con alguien
              </h2>
              <p className="text-sm text-muted-foreground max-w-xs">
                Te conectamos con otra persona para orar juntos en tiempo real.
                Solo audio, totalmente anónimo.
              </p>
            </div>
            <Button onClick={startMatching} size="lg" className="w-full max-w-xs">
              <Phone className="size-4" />
              Buscar compañero
            </Button>
          </CardContent>
        </Card>
      )}

      {(state === 'connecting' || state === 'waiting') && (
        <Card className="bg-card/50">
          <CardContent className="flex flex-col items-center py-12 space-y-6">
            <div className="size-24 rounded-full bg-primary/10 flex items-center justify-center animate-pulse">
              <Loader2 className="size-10 text-primary animate-spin" />
            </div>
            <div className="text-center space-y-2">
              <h2 className="text-lg font-semibold text-foreground">
                {state === 'connecting' ? 'Conectando…' : 'Buscando compañero…'}
              </h2>
              <p className="text-sm text-muted-foreground">
                Espera mientras encontramos a alguien para orar contigo.
              </p>
            </div>
            <Button variant="outline" onClick={handleCancel} className="w-full max-w-xs">
              Cancelar
            </Button>
          </CardContent>
        </Card>
      )}

      {state === 'matched' && (
        <Card className="bg-card/50">
          <CardContent className="flex flex-col items-center py-12 space-y-4">
            <Loader2 className="size-8 text-primary animate-spin" />
            <p className="text-foreground font-medium">Conectando audio…</p>
          </CardContent>
        </Card>
      )}

      {state === 'in_prayer' && (
        <Card className="bg-card/50">
          <CardContent className="flex flex-col items-center py-8 space-y-6">
            <div className="text-center space-y-1">
              <p className="text-sm text-muted-foreground">Orando juntos</p>
              <p className="text-4xl font-mono font-bold text-primary">
                {formatTime(timer)}
              </p>
            </div>

            <div className="w-full h-1 bg-primary/20 rounded-full overflow-hidden">
              <div
                className="h-full bg-primary rounded-full transition-all"
                style={{ width: `${Math.min(100, (timer / 300) * 100)}%` }}
              />
            </div>

            <p className="text-sm text-muted-foreground italic">
              Orando en silencio…
            </p>

            <div className="flex gap-3">
              <Button
                variant={muted ? 'destructive' : 'outline'}
                size="icon"
                onClick={toggleMute}
                className="size-12 rounded-full"
              >
                {muted ? <MicOff className="size-5" /> : <Mic className="size-5" />}
              </Button>

              <Button
                variant="destructive"
                size="icon"
                onClick={handleEndPrayer}
                className="size-12 rounded-full"
              >
                <PhoneOff className="size-5" />
              </Button>
            </div>

            <div className="flex gap-2 pt-4">
              <Button variant="ghost" size="sm" onClick={handleAddPartner}>
                <UserPlus className="size-4" />
                Agregar compañero
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleReport}
                className="text-destructive hover:text-destructive"
              >
                <Flag className="size-4" />
                Reportar
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
