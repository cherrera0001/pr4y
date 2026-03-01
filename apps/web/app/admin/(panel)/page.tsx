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

interface StatsDetail {
  lastSyncActivity: string;
  recordsByTypeByDay: Array<{ day: string; type: string; count: number }>;
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
  const [detail, setDetail] = useState<StatsDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadStats = useCallback(() => {
    setError(null);
    setLoading(true);
    const daysParam = days;
    Promise.all([
      fetch(`/api/admin/stats?days=${daysParam}`, { credentials: 'same-origin' }),
      fetch(`/api/admin/stats/detail?days=${daysParam}`, { credentials: 'same-origin' }),
    ])
      .then(([statsRes, detailRes]) => {
        if (statsRes.status === 401 || detailRes.status === 401) {
          router.replace('/admin/login');
          return { stats: null, detail: null };
        }
        if (statsRes.status === 403 || detailRes.status === 403) {
          router.replace('/admin/login?error=admin_required');
          return { stats: null, detail: null };
        }
        if (!statsRes.ok) throw new Error(statsRes.status === 503 ? 'API no configurada' : statsRes.statusText);
        return Promise.all([statsRes.json(), detailRes.ok ? detailRes.json() : Promise.resolve(null)]).then(
          ([statsData, detailData]) => ({ stats: statsData, detail: detailData })
        );
      })
      .then(({ stats: statsData, detail: detailData }) => {
        if (statsData) {
          setStats(statsData);
          setDetail(detailData);
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
          <h1 className="text-2xl font-bold text-foreground">Dashboard de salud</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            DAU, tráfico de sincronización y pulso del sistema. Selecciona el período:
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
              title={`Ver últimos ${d} días`}
            >
              {d} días
            </Button>
          ))}
          <Button variant="ghost" size="sm" onClick={loadStats} disabled={loading} title="Actualizar">
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {detail?.lastSyncActivity && (
        <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
          <CardHeader className="pb-2">
            <CardDescription>Última actividad de sincronización</CardDescription>
            <CardTitle className="text-xl">{detail.lastSyncActivity}</CardTitle>
          </CardHeader>
        </Card>
      )}

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

      {detail?.recordsByTypeByDay && detail.recordsByTypeByDay.length > 0 && (
        <Card className="glass-card border-slate-700/50 shadow-xl shadow-black/10">
          <CardHeader>
            <CardTitle>Registros por tipo y día</CardTitle>
            <CardDescription>Volumen agregado por tipo (oración, diario, etc.) en los últimos {days} días</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border">
                    <th className="text-left py-2 font-medium">Día</th>
                    <th className="text-left py-2 font-medium">Tipo</th>
                    <th className="text-right py-2 font-medium">Cantidad</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.recordsByTypeByDay.slice(-30).reverse().map((r, i) => (
                    <tr key={`${r.day}-${r.type}-${i}`} className="border-b border-border/50">
                      <td className="py-1.5">{r.day.slice(5)}</td>
                      <td className="py-1.5">{r.type}</td>
                      <td className="text-right py-1.5">{r.count.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
