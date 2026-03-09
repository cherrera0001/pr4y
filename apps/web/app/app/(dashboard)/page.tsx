'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/components/auth-provider';
import { authFetch } from '@/lib/auth-client';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { BookOpen, Plus, Star, Loader2 } from 'lucide-react';

interface FaithStats {
  totalRecords: number;
  totalAnswered: number;
  totalInProcess: number;
  totalPending: number;
  streakDays: number;
  longestStreakDays: number;
}

export default function AppHomePage() {
  const { user } = useAuth();
  const [stats, setStats] = useState<FaithStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    authFetch('/user/faith-stats')
      .then(async (res) => {
        if (res.ok) setStats(await res.json());
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">
          Hola{user?.email ? `, ${user.email.split('@')[0]}` : ''}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Tu búnker de oración privado.
        </p>
      </div>

      {/* Stats */}
      {loading ? (
        <div className="flex justify-center py-8">
          <Loader2 className="size-6 animate-spin text-muted-foreground" />
        </div>
      ) : stats ? (
        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Pedidos" value={stats.totalRecords} />
          <StatCard label="Respondidos" value={stats.totalAnswered} />
          <StatCard label="Racha actual" value={`${stats.streakDays}d`} />
          <StatCard label="Mejor racha" value={`${stats.longestStreakDays}d`} />
        </div>
      ) : null}

      {/* Quick actions */}
      <div className="grid gap-3">
        <Button asChild size="lg" className="w-full justify-start gap-3">
          <Link href="/app/journal">
            <BookOpen className="size-5" />
            Abrir diario
          </Link>
        </Button>
        <Button
          asChild
          variant="outline"
          size="lg"
          className="w-full justify-start gap-3"
        >
          <Link href="/app/victorias">
            <Star className="size-5" />
            Mis victorias
          </Link>
        </Button>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <Card className="bg-card/50">
      <CardContent className="p-4">
        <p className="text-2xl font-bold text-foreground">{value}</p>
        <p className="text-xs text-muted-foreground">{label}</p>
      </CardContent>
    </Card>
  );
}
