export const metadata = {
  title: 'PR4Y',
  description: 'Cuaderno de oración personal',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
