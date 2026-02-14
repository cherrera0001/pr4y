/** @type {import('next').NextConfig} */
const nextConfig = {
  eslint: { ignoreDuringBuilds: true },
  typescript: { ignoreBuildErrors: true },
  trailingSlash: false,
  reactStrictMode: true,
  // Panel /admin usa middleware y API routes; no usar output: 'export'
  // Vercel: Project Settings → Root Directory = "apps/web" para evitar 404 en monorepo
};

module.exports = nextConfig;
