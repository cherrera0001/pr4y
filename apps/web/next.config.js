/** @type {import('next').NextConfig} */
const nextConfig = {
  eslint: { ignoreDuringBuilds: true },
  typescript: { ignoreBuildErrors: true },
  trailingSlash: false,
  reactStrictMode: true,
  // Panel /admin usa middleware y API routes; no usar output: 'export'
  // Vercel: Project Settings → Root Directory = "apps/web" para evitar 404 en monorepo
  async headers() {
    return [
      // Cabeceras de seguridad globales (VULN-005)
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Strict-Transport-Security', value: 'max-age=31536000; includeSubDomains; preload' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'geolocation=(), microphone=(self), camera=(), payment=()' },
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com",
              "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
              "font-src 'self' https://fonts.gstatic.com",
              "img-src 'self' data: https:",
              "connect-src 'self' wss://*.pr4y.cl wss://pr4yapi-production.up.railway.app https://pr4yapi-production.up.railway.app https://accounts.google.com stun:stun.l.google.com:19302 stun:stun1.l.google.com:19302",
              "frame-src https://accounts.google.com",
              "base-uri 'self'",
              "form-action 'self'",
            ].join('; '),
          },
        ],
      },
      // Digital Asset Links: uso compartido de credenciales Google (app Android + pr4y.cl)
      {
        source: '/.well-known/assetlinks.json',
        headers: [{ key: 'Content-Type', value: 'application/json' }],
      },
      {
        source: '/admin/login',
        headers: [
          { key: 'Cross-Origin-Opener-Policy', value: 'same-origin-allow-popups' },
          { key: 'Cache-Control', value: 'no-store, no-cache, must-revalidate' },
        ],
      },
      {
        source: '/admin/:path*',
        headers: [
          { key: 'Cross-Origin-Opener-Policy', value: 'same-origin-allow-popups' },
          { key: 'Cache-Control', value: 'no-store, no-cache, must-revalidate' },
        ],
      },
    ];
  },
  async rewrites() {
    const backendUrl = process.env.API_BACKEND_URL || process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
    return [
      // Proxy WebSocket: /ws → backend /ws (Socket.io)
      {
        source: '/ws/:path*',
        destination: `${backendUrl}/ws/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
