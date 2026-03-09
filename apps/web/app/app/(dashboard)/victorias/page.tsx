'use client';

import { useEffect, useState } from 'react';
import { authFetch } from '@/lib/auth-client';
import { Card, CardContent } from '@/components/ui/card';
import { Loader2, Star } from 'lucide-react';

interface Answer {
  id: string;
  testimony: string | null;
  answeredAt: string;
  record?: { clientUpdatedAt?: string } | null;
}

interface VictoriasData {
  answeredCount: number;
  answers: Answer[];
}

export default function VictoriasPage() {
  const [data, setData] = useState<VictoriasData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    authFetch('/answers')
      .then(async (res) => {
        if (res.ok) {
          const body = await res.json();
          setData(body);
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Mis Victorias</h1>
        {data && (
          <p className="mt-1 text-sm text-muted-foreground">
            {data.answeredCount} oración(es) respondida(s)
          </p>
        )}
      </div>

      {!data?.answers?.length ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Star className="size-16 text-primary/30" />
          <p className="mt-4 text-lg font-medium text-foreground">
            Aún sin victorias
          </p>
          <p className="mt-2 text-sm text-muted-foreground max-w-xs">
            Cuando marques un pedido como respondido, aparecerá aquí.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {data.answers.map((answer) => (
            <Card key={answer.id} className="bg-card/50">
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">
                  Respondido{' '}
                  {new Date(answer.answeredAt).toLocaleDateString('es-CL')}
                </p>
                {answer.testimony && (
                  <p className="mt-2 text-sm text-foreground">
                    {answer.testimony}
                  </p>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
