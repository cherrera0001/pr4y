import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'PR4Y — Búnker Digital de Oración',
    short_name: 'PR4Y',
    description:
      'Cuaderno de oración personal con cifrado E2EE (Zero-Knowledge). Solo tú y Dios.',
    start_url: '/app',
    display: 'standalone',
    background_color: '#0a0a0a',
    theme_color: '#0ea5e9',
    orientation: 'portrait',
    categories: ['lifestyle', 'social'],
    icons: [
      {
        src: '/icons/icon-192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-maskable-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  };
}
