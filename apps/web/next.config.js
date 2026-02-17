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
      {
        source: '/admin/:path*',
        headers: [
          {
            key: 'Cross-Origin-Opener-Policy',
            value: 'same-origin-allow-popups',
          },
        ],
      },
    ];
  },
};

module.exports = nextConfig;
