'use client';

import { useEffect, useState } from 'react';
import { z } from 'zod';
import { toast } from 'sonner';
import { Loader2, Pencil, Trash2, Plus } from 'lucide-react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const createContentSchema = z.object({
  type: z.string().min(1, 'Tipo requerido').max(64),
  title: z.string().min(1, 'Título requerido').max(512),
  body: z.string().min(1, 'Cuerpo requerido'),
  published: z.boolean().optional(),
  sortOrder: z.coerce.number().int().optional(),
});

const updateContentSchema = z
  .object({
    type: z.string().min(1).max(64).optional(),
    title: z.string().min(1).max(512).optional(),
    body: z.string().optional(),
    published: z.boolean().optional(),
    sortOrder: z.coerce.number().int().optional(),
  })
  .refine((d) => Object.keys(d).filter((k) => d[k as keyof typeof d] !== undefined).length > 0, {
    message: 'Indica al menos un campo a actualizar',
  });

type ContentItem = {
  id: string;
  type: string;
  title: string;
  body: string;
  published: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('es-CL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function AdminContentPage() {
  const [items, setItems] = useState<ContentItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [filterType, setFilterType] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<ContentItem | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [formType, setFormType] = useState('prayer');
  const [formTitle, setFormTitle] = useState('');
  const [formBody, setFormBody] = useState('');
  const [formPublished, setFormPublished] = useState(false);
  const [formSortOrder, setFormSortOrder] = useState(0);

  const load = () => {
    const q = filterType ? `?type=${encodeURIComponent(filterType)}` : '';
    fetch(`/api/admin/content${q}`)
      .then((res) => {
        if (!res.ok) throw new Error(res.statusText);
        return res.json();
      })
      .then((data) => setItems(Array.isArray(data) ? data : []))
      .catch((e) => setError(e.message || 'Error al cargar'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [filterType]);

  const openCreate = () => {
    setEditing(null);
    setFormType('prayer');
    setFormTitle('');
    setFormBody('');
    setFormPublished(false);
    setFormSortOrder(0);
    setShowForm(true);
  };

  const openEdit = (item: ContentItem) => {
    setEditing(item);
    setFormType(item.type);
    setFormTitle(item.title);
    setFormBody(item.body);
    setFormPublished(item.published);
    setFormSortOrder(item.sortOrder);
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditing(null);
  };

  const submitForm = async () => {
    if (editing) {
      const parsed = updateContentSchema.safeParse({
        type: formType,
        title: formTitle,
        body: formBody,
        published: formPublished,
        sortOrder: formSortOrder,
      });
      if (!parsed.success) {
        toast.error(parsed.error.errors.map((e) => e.message).join('. '));
        return;
      }
      setSubmitting(true);
      try {
        const res = await fetch(`/api/admin/content/${editing.id}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(parsed.data),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          toast.error(data?.error?.message || 'Error al actualizar');
          setSubmitting(false);
          return;
        }
        setItems((prev) => prev.map((i) => (i.id === editing.id ? { ...i, ...data } : i)));
        toast.success('Contenido actualizado');
        closeForm();
      } catch {
        toast.error('Error de conexión');
      }
      setSubmitting(false);
    } else {
      const parsed = createContentSchema.safeParse({
        type: formType,
        title: formTitle,
        body: formBody,
        published: formPublished,
        sortOrder: formSortOrder,
      });
      if (!parsed.success) {
        toast.error(parsed.error.errors.map((e) => e.message).join('. '));
        return;
      }
      setSubmitting(true);
      try {
        const res = await fetch('/api/admin/content', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(parsed.data),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          toast.error(data?.error?.message || 'Error al crear');
          setSubmitting(false);
          return;
        }
        setItems((prev) => [data, ...prev]);
        toast.success('Contenido creado');
        closeForm();
      } catch {
        toast.error('Error de conexión');
      }
      setSubmitting(false);
    }
  };

  const deleteItem = async (id: string) => {
    if (!confirm('¿Eliminar este contenido? No se puede deshacer.')) return;
    setDeletingId(id);
    try {
      const res = await fetch(`/api/admin/content/${id}`, { method: 'DELETE' });
      if (res.status === 204) {
        setItems((prev) => prev.filter((i) => i.id !== id));
        toast.success('Contenido eliminado');
      } else {
        toast.error('Error al eliminar');
      }
    } finally {
      setDeletingId(null);
    }
  };

  const types = Array.from(new Set(items.map((i) => i.type))).sort();

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        <Loader2 className="size-6 animate-spin" />
      </div>
    );
  }
  if (error) {
    return (
      <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-destructive">
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-foreground">
            Gestor de contenido
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Oraciones y avisos que la app móvil consume. Crear, editar, publicar o eliminar.
          </p>
        </div>
        <div className="flex items-center gap-4">
          <Select value={filterType || 'all'} onValueChange={(v) => setFilterType(v === 'all' ? '' : v)}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Tipo" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos los tipos</SelectItem>
              {types.map((t) => (
                <SelectItem key={t} value={t}>
                  {t}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            Nuevo contenido
          </Button>
        </div>
      </div>

      <div className="rounded-md border border-border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tipo</TableHead>
              <TableHead>Título</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead>Orden</TableHead>
              <TableHead>Actualizado</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id}>
                <TableCell className="font-medium">{item.type}</TableCell>
                <TableCell className="max-w-xs truncate">{item.title}</TableCell>
                <TableCell>
                  <Badge variant={item.published ? 'default' : 'secondary'}>
                    {item.published ? 'Publicado' : 'Borrador'}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">{item.sortOrder}</TableCell>
                <TableCell className="text-muted-foreground text-sm">
                  {formatDate(item.updatedAt)}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => openEdit(item)}
                  >
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => deleteItem(item.id)}
                    disabled={deletingId === item.id}
                    className="text-destructive hover:text-destructive"
                  >
                    {deletingId === item.id ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Trash2 className="size-4" />
                    )}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No hay contenidos. Crea uno con «Nuevo contenido».
        </p>
      )}

      <Dialog open={showForm} onOpenChange={(open) => !open && closeForm()}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar contenido' : 'Nuevo contenido'}</DialogTitle>
            <DialogDescription>
              Las oraciones y avisos se muestran en la app según tipo y orden.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="form-type">Tipo</Label>
              <Input
                id="form-type"
                value={formType}
                onChange={(e) => setFormType(e.target.value)}
                placeholder="prayer, announcement…"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="form-title">Título</Label>
              <Input
                id="form-title"
                value={formTitle}
                onChange={(e) => setFormTitle(e.target.value)}
                placeholder="Título del contenido"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="form-body">Cuerpo</Label>
              <Textarea
                id="form-body"
                value={formBody}
                onChange={(e) => setFormBody(e.target.value)}
                rows={4}
                placeholder="Texto que verán los usuarios"
              />
            </div>
            <div className="flex items-center gap-6">
              <div className="flex items-center gap-2">
                <Checkbox
                  id="form-published"
                  checked={formPublished}
                  onCheckedChange={(c) => setFormPublished(!!c)}
                />
                <Label htmlFor="form-published">Publicado</Label>
              </div>
              <div className="flex items-center gap-2">
                <Label htmlFor="form-sort">Orden</Label>
                <Input
                  id="form-sort"
                  type="number"
                  value={formSortOrder}
                  onChange={(e) => setFormSortOrder(Number(e.target.value))}
                  className="w-20"
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeForm}>
              Cancelar
            </Button>
            <Button onClick={submitForm} disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  Guardando…
                </>
              ) : editing ? (
                'Guardar'
              ) : (
                'Crear'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
