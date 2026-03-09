import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Pedir oración — PR4Y',
  description:
    'Envía tu pedido de oración de forma anónima. Aparecerá en la Ruleta de la app para que otros oren por ti.',
};

export default function PedirOracionLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
