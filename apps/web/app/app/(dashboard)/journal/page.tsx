'use client';

import { useCallback, useEffect, useState } from 'react';
import { authFetch } from '@/lib/auth-client';
import { getDek } from '@/lib/vault';
import { encryptPayload, decryptPayload } from '@/lib/crypto';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { BookOpen, Loader2, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

interface SyncRecord {
  recordId: string;
  type: string;
  version: number;
  encryptedPayloadB64: string;
  clientUpdatedAt: string;
  deleted: boolean;
  status: string;
}

interface JournalEntry {
  id: string;
  content: string;
  updatedAt: string;
}

export default function JournalPage() {
  const [entries, setEntries] = useState<JournalEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [showNew, setShowNew] = useState(false);
  const [newContent, setNewContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const loadEntries = useCallback(async () => {
    const dek = getDek();
    if (!dek) return;

    try {
      // Pull all records, filter journal type
      let allRecords: SyncRecord[] = [];
      let cursor = '';
      let hasMore = true;

      while (hasMore) {
        const url = cursor
          ? `/sync/pull?cursor=${encodeURIComponent(cursor)}&limit=500`
          : '/sync/pull?limit=500';
        const res = await authFetch(url);
        if (!res.ok) break;
        const data = await res.json();
        allRecords = allRecords.concat(data.records || []);
        cursor = data.nextCursor || '';
        hasMore = !!cursor && (data.records?.length ?? 0) > 0;
      }

      const journalRecords = allRecords.filter(
        (r) => r.type === 'journal' && !r.deleted
      );

      const decrypted = await Promise.all(
        journalRecords.map(async (r) => {
          try {
            const payload = await decryptPayload<{ content?: string }>(
              r.encryptedPayloadB64,
              dek
            );
            return {
              id: r.recordId,
              content: payload.content || '',
              updatedAt: r.clientUpdatedAt,
            };
          } catch {
            return {
              id: r.recordId,
              content: '[No se pudo descifrar]',
              updatedAt: r.clientUpdatedAt,
            };
          }
        })
      );

      // Sort by date descending
      decrypted.sort(
        (a, b) =>
          new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
      );
      setEntries(decrypted);
    } catch {
      toast.error('Error al cargar el diario');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  const handleCreate = async () => {
    if (!newContent.trim()) return;
    const dek = getDek();
    if (!dek) {
      toast.error('El búnker está cerrado. Recarga la página.');
      return;
    }

    setSaving(true);
    try {
      const recordId = crypto.randomUUID();
      const now = new Date().toISOString();
      const encryptedPayloadB64 = await encryptPayload(
        { content: newContent.trim() },
        dek
      );

      const res = await authFetch('/sync/push', {
        method: 'POST',
        body: JSON.stringify({
          records: [
            {
              recordId,
              type: 'journal',
              version: 1,
              encryptedPayloadB64,
              clientUpdatedAt: now,
              deleted: false,
            },
          ],
        }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        toast.error(body?.error?.message || 'Error al guardar');
        setSaving(false);
        return;
      }

      setEntries((prev) => [
        { id: recordId, content: newContent.trim(), updatedAt: now },
        ...prev,
      ]);
      setNewContent('');
      setShowNew(false);
      toast.success('Entrada guardada');
    } catch {
      toast.error('Error al guardar');
    }
    setSaving(false);
  };

  const handleDelete = async (id: string) => {
    const dek = getDek();
    if (!dek) return;

    setDeletingId(id);
    try {
      const now = new Date().toISOString();
      // Push with deleted: true, still needs valid encryptedPayloadB64
      const encryptedPayloadB64 = await encryptPayload({ content: '' }, dek);

      const res = await authFetch('/sync/push', {
        method: 'POST',
        body: JSON.stringify({
          records: [
            {
              recordId: id,
              type: 'journal',
              version: 999,
              encryptedPayloadB64,
              clientUpdatedAt: now,
              deleted: true,
            },
          ],
        }),
      });

      if (res.ok) {
        setEntries((prev) => prev.filter((e) => e.id !== id));
        toast.success('Entrada eliminada');
      } else {
        toast.error('Error al eliminar');
      }
    } catch {
      toast.error('Error al eliminar');
    }
    setDeletingId(null);
  };

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">Diario</h1>
        <Button onClick={() => setShowNew(true)} size="sm">
          <Plus className="size-4" />
          Nueva entrada
        </Button>
      </div>

      {entries.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <BookOpen className="size-16 text-primary/30" />
          <p className="mt-4 text-lg font-medium text-foreground">
            Tu diario está vacío
          </p>
          <p className="mt-2 text-sm text-muted-foreground max-w-xs">
            Escribe tu primera reflexión o gratitud.
          </p>
          <Button onClick={() => setShowNew(true)} className="mt-6">
            <Plus className="size-4" />
            Nueva entrada
          </Button>
        </div>
      ) : (
        <div className="space-y-3">
          {entries.map((entry) => (
            <Card key={entry.id} className="bg-card/50 group">
              <CardContent className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <p className="text-xs text-muted-foreground mb-1">
                      {formatDate(entry.updatedAt)}
                    </p>
                    <p className="text-sm text-foreground whitespace-pre-wrap line-clamp-4">
                      {entry.content}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="opacity-0 group-hover:opacity-100 transition-opacity text-destructive hover:text-destructive shrink-0"
                    onClick={() => handleDelete(entry.id)}
                    disabled={deletingId === entry.id}
                  >
                    {deletingId === entry.id ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Trash2 className="size-4" />
                    )}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* New entry dialog */}
      <Dialog open={showNew} onOpenChange={(open) => !open && setShowNew(false)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Nueva entrada</DialogTitle>
          </DialogHeader>
          <Textarea
            value={newContent}
            onChange={(e) => setNewContent(e.target.value)}
            placeholder="Escribe tu reflexión, gratitud u oración…"
            rows={6}
            className="min-h-[150px]"
            autoFocus
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowNew(false)}>
              Cancelar
            </Button>
            <Button
              onClick={handleCreate}
              disabled={saving || !newContent.trim()}
            >
              {saving ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  Guardando…
                </>
              ) : (
                'Guardar'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('es-CL', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}
