'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Loader2, RefreshCw, LogIn } from 'lucide-react';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { AreaChart, BarChart, Grid } from '@tremor/react';

const DAYS_OPTIONS = [7, 14, 30] as const;

interface Stats {
  totalUsers: number;
  totalRecords: number;
  totalBlobBytes: string;
  syncsToday: number;
  bytesPushedToday: string;
  bytesPulledToday: string;
  byDay: Array<{
    day: string;
    usersActive: number;
    bytesPushed: string;
    bytesPulled: string;
  }>;
}

function formatBytes(n: string | number): string {
  const num = typeof n === 'string' ? BigInt(n) : BigInt(n);
  if (num < 1024n) return `${num} B`;
  if (num < 1024n * 1024n) return `${Number(num) / 1024} KB`;
  if (num < 1024n * 1024n * 1024n) return `${Number(num) / (1024 * 1024)} MB`;
  return `${Number(num) / (1024 * 1024 * 1024)} GB`;
}

export default function AdminDashboardPage() {
  const router = useRouter();
  const [days, setDays] = useState<number>(14);
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadStats = useCallback(() => {
    setError(null);
    setLoading(true);
    fetch(`/api/admin/stats?days=${days}`, { credentials: 'same-origin' })
      .then((res) => {
        if (res.status === 401) {
          router.replace('/admin/login');
          return null;
        }
        if (!res.ok) throw new Error(res.status === 503 ? 'API no configurada' : res.statusText);
        return res.json();
      })
      .then((data) => {
        if (data) {
          setStats(data);
          setError(null);
        }
      })
      .catch((err) => setError(err.message || 'Error al cargar estadísticas'))
      .finally(() => setLoading(false));
  }, [days, router]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  if (error) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-destructive flex flex-col gap-3">
          <p>{error}</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={loadStats}>
              <RefreshCw className="size-4 mr-2" />
              Reintentar
            </Button>
            <Button variant="ghost" size="sm" asChild>
              <Link href="/admin/login">
                <LogIn className="size-4 mr-2" />
                Ir al login
              </Link>
            </Button>
          </div>
        </div>
      </div>
    );
  }
  if (loading && !stats) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        <Loader2 className="size-6 animate-spin" />
      </div>
    );
  }
  if (!stats) return null;

  const chartData = stats.byDay.map((d) => ({
    fecha: d.day.slice(5),
    'Usuarios activos (DAU)': d.usersActive,
    'Bytes subidos': Number(d.bytesPushed),
    'Bytes bajados': Number(d.bytesPulled),
  }));

  return (
    <div className="space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Usuarios, registros, sincronizaciones y almacenamiento E2EE. Período:
          </p>
        </div>
        <div className="flex items-center gap-1">
          {DAYS_OPTIONS.map((d) => (
            <Button
              key={d}
              variant={days === d ? 'default' : 'outline'}
              size="sm"
              onClick={() => setDays(d)}
              disabled={loading}
            >
              {d} días
            </Button>
          ))}
          <Button variant="ghost" size="sm" onClick={loadStats} disabled={loading} title="Actualizar">
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      <Grid numItemsSm={2} numItemsLg={4} className="gap-4">
        <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
          <CardHeader className="pb-2">
            <CardDescription>Registros totales</CardDescription>
            <CardTitle className="text-2xl">{stats.totalRecords.toLocaleString()}</CardTitle>
          </CardHeader>
        </Card>
        <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
          <CardHeader className="pb-2">
            <CardDescription>Usuarios totales</CardDescription>
            <CardTitle className="text-2xl">{stats.totalUsers.toLocaleString()}</CardTitle>
          </CardHeader>
        </Card>
        <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
          <CardHeader className="pb-2">
            <CardDescription>Sincronizaciones hoy</CardDescription>
            <CardTitle className="text-2xl">{stats.syncsToday.toLocaleString()}</CardTitle>
          </CardHeader>
        </Card>
        <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
          <CardHeader className="pb-2">
            <CardDescription>Almacenamiento blobs (E2EE)</CardDescription>
            <CardTitle className="text-2xl">{formatBytes(stats.totalBlobBytes)}</CardTitle>
          </CardHeader>
        </Card>
      </Grid>

      <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
        <CardHeader>
          <CardTitle>Usuarios activos diarios (DAU)</CardTitle>
          <CardDescription>Últimos {days} días</CardDescription>
        </CardHeader>
        <CardContent>
          <AreaChart
            className="mt-4 h-72"
            data={chartData}
            index="fecha"
            categories={['Usuarios activos (DAU)']}
            colors={['blue']}
            valueFormatter={(v) => String(v)}
            showLegend={false}
          />
        </CardContent>
      </Card>

      <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
        <CardHeader>
          <CardTitle>Tráfico de sincronización por día</CardTitle>
          <CardDescription>Últimos {days} días — bytes subidos y bajados</CardDescription>
        </CardHeader>
        <CardContent>
          <BarChart
            className="mt-4 h-72"
            data={chartData}
            index="fecha"
            categories={['Bytes subidos', 'Bytes bajados']}
            colors={['emerald', 'sky']}
            valueFormatter={(v) => formatBytes(v)}
            stack
          />
        </CardContent>
      </Card>
    </div>
  );
}
