'use client';

import { useEffect, useState, useMemo } from 'react';
import { Search, UserX, UserCheck, ChevronLeft, ChevronRight, KeyRound } from 'lucide-react';
import { toast } from 'sonner';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

const PAGE_SIZE = 10;

interface AdminUserRow {
  id: string;
  email: string;
  role: string;
  status: string;
  createdAt: string;
  lastLoginAt: string | null;
  hasDek: boolean;
  recordCount: number;
}

function anonymizeId(id: string): string {
  if (!id || id.length <= 8) return '••••••••';
  return `${id.slice(0, 8)}…`;
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('es-CL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('all');
  const [page, setPage] = useState(0);
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch('/api/admin/users?limit=200&offset=0')
      .then((res) => {
        if (!res.ok) throw new Error(res.statusText);
        const count = parseInt(res.headers.get('X-Total-Count') ?? '0', 10);
        if (!cancelled) setTotal(count);
        return res.json();
      })
      .then((data) => {
        if (!cancelled) setUsers(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'Error al cargar usuarios');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => {
    let list = users;
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      list = list.filter(
        (u) =>
          u.email.toLowerCase().includes(q) || u.id.toLowerCase().includes(q)
      );
    }
    if (roleFilter !== 'all') {
      list = list.filter((u) => u.role === roleFilter);
    }
    return list;
  }, [users, search, roleFilter]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const paginated = useMemo(() => {
    const start = currentPage * PAGE_SIZE;
    return filtered.slice(start, start + PAGE_SIZE);
  }, [filtered, currentPage]);

  const setStatus = async (userId: string, status: 'active' | 'banned') => {
    setUpdatingId(userId);
    try {
      const res = await fetch(`/api/admin/users/${userId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        toast.error(data?.error?.message || 'Error al actualizar');
        setUpdatingId(null);
        return;
      }
      setUsers((prev) =>
        prev.map((u) => (u.id === userId ? { ...u, ...data } : u))
      );
      toast.success(status === 'banned' ? 'Usuario suspendido' : 'Usuario activado');
    } catch {
      toast.error('Error de conexión');
    }
    setUpdatingId(null);
  };

  const handleResetPassword = () => {
    toast.info('Resetear contraseña estará disponible próximamente.');
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        Cargando usuarios…
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

  const roles = Array.from(new Set(users.map((u) => u.role))).sort();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Usuarios</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Mantenedor de usuarios. Búsqueda por email, filtro por rol, paginación y acciones. {total} en total.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-4 rounded-lg border border-slate-700/50 bg-slate-900/40 backdrop-blur-xl p-4">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            type="search"
            placeholder="Buscar por email o ID…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="pl-9"
            aria-label="Buscar por email"
          />
        </div>
        <Select
          value={roleFilter}
          onValueChange={(v) => {
            setRoleFilter(v);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Rol" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Todos los roles</SelectItem>
            {roles.map((r) => (
              <SelectItem key={r} value={r}>
                {r === 'admin' || r === 'super_admin' ? 'ADMIN' : 'USER'}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border border-slate-700/50 overflow-hidden bg-slate-900/30 backdrop-blur-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Fecha de registro</TableHead>
              <TableHead>Rol</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginated.map((u) => (
              <TableRow key={u.id}>
                <TableCell className="font-mono text-muted-foreground">
                  {anonymizeId(u.id)}
                </TableCell>
                <TableCell className="font-medium">{u.email}</TableCell>
                <TableCell className="text-muted-foreground">
                  {formatDate(u.createdAt)}
                </TableCell>
                <TableCell>
                  <Badge
                    variant={
                      u.role === 'super_admin' || u.role === 'admin'
                        ? 'default'
                        : 'secondary'
                    }
                  >
                    {u.role === 'admin' || u.role === 'super_admin' ? 'ADMIN' : 'USER'}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Badge
                    variant={u.status === 'banned' ? 'destructive' : 'success'}
                  >
                    {u.status === 'banned' ? 'Suspendido' : 'Activo'}
                  </Badge>
                </TableCell>
                <TableCell className="text-right flex items-center justify-end gap-2">
                  {u.status === 'active' ? (
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => setStatus(u.id, 'banned')}
                      disabled={updatingId === u.id}
                    >
                      <UserX className="size-4" />
                      Suspender
                    </Button>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setStatus(u.id, 'active')}
                      disabled={updatingId === u.id}
                    >
                      <UserCheck className="size-4" />
                      Activar
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleResetPassword}
                    title="Resetear contraseña (próximamente)"
                  >
                    <KeyRound className="size-4" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {filtered.length > PAGE_SIZE && (
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            Mostrando {currentPage * PAGE_SIZE + 1}–{Math.min((currentPage + 1) * PAGE_SIZE, filtered.length)} de {filtered.length}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={currentPage === 0}
            >
              <ChevronLeft className="size-4" />
              Anterior
            </Button>
            <span className="text-sm text-muted-foreground">
              Página {currentPage + 1} de {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={currentPage >= totalPages - 1}
            >
              Siguiente
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      )}

      {filtered.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No hay usuarios que coincidan con los filtros.
        </p>
      )}
    </div>
  );
}
