'use client';

import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { AreaChart, BarChart, Grid } from '@tremor/react';

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
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch('/api/admin/stats?days=14')
      .then((res) => {
        if (!res.ok) throw new Error(res.statusText);
        return res.json();
      })
      .then((data) => {
        if (!cancelled) setStats(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'Error al cargar estadísticas');
      });
    return () => { cancelled = true; };
  }, []);

  if (error) {
    return (
      <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-destructive">
        {error}
      </div>
    );
  }
  if (!stats) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        <Loader2 className="size-6 animate-spin" />
      </div>
    );
  }

  const chartData = stats.byDay.map((d) => ({
    fecha: d.day.slice(5),
    'Usuarios activos (DAU)': d.usersActive,
    'Bytes subidos': Number(d.bytesPushed),
    'Bytes bajados': Number(d.bytesPulled),
  }));

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Dashboard</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Datos en tiempo real: conteo de usuarios (public.users), registros, sincronizaciones y almacenamiento E2EE en Railway.
        </p>
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
          <CardDescription>Últimos 14 días</CardDescription>
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
          <CardDescription>Bytes subidos y bajados</CardDescription>
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
