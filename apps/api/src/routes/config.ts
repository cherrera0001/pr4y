import { FastifyInstance } from 'fastify';

const defaultRateLimit = { max: 300, timeWindow: '1 minute' as const };

/**
 * Configuración pública. Consumo separado:
 * - googleWebClientId: solo para la versión web (Vercel usa NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID). Backend acepta tokens con audience Web.
 * - googleAndroidClientId: solo para la app Android (serverClientId en login). Backend acepta tokens con audience Android.
 */
export default async function configRoutes(server: FastifyInstance) {
  server.get(
    '/config',
    { config: { rateLimit: defaultRateLimit } },
    async (_, reply) => {
      const googleWebClientId = process.env.GOOGLE_WEB_CLIENT_ID?.trim() ?? '';
      const googleAndroidClientId = process.env.GOOGLE_ANDROID_CLIENT_ID?.trim() ?? '';
      return reply.send({ googleWebClientId, googleAndroidClientId });
    }
  );
}
