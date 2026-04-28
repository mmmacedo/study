import type { NextConfig } from "next";
import withPWAInit from "next-pwa";

// next-pwa usa Workbox por baixo. Em dev o SW é desabilitado para não
// servir assets cacheados durante o desenvolvimento.
const withPWA = withPWAInit({
  dest: "public",   // gerado em public/sw.js e public/workbox-*.js
  disable: process.env.NODE_ENV === "development",
  // NetworkFirst para rotas de API — nunca servir tokens expirados do cache.
  // CacheFirst para assets estáticos (_next/static/) — imutáveis por hash.
  runtimeCaching: [
    {
      urlPattern: /^\/api\//,
      handler: "NetworkFirst",
      options: { cacheName: "api-cache", networkTimeoutSeconds: 10 },
    },
  ],
});

const nextConfig: NextConfig = {
  /*
   * Rewrites: o browser fala apenas com localhost:3000.
   * O Next.js faz proxy para o api-gateway em localhost:8080.
   *
   * Por que rewrite em vez de chamar o gateway diretamente do browser?
   *   O browser chamaria localhost:8080 (porta diferente de 3000) — CORS bloquearia
   *   a resposta se o gateway não tiver o header Access-Control-Allow-Origin correto.
   *   Com rewrite, o request vai para :3000/api/auth/introspect → Next.js encaminha
   *   para :8080/auth/introspect server-side, sem CORS.
   *
   * A troca do authorization code (callback) chama o Keycloak diretamente — o Keycloak
   * já tem webOrigins: ["*"] no realm study, então CORS está liberado para essa chamada.
   */
  async rewrites() {
    return [
      {
        source: "/api/auth/:path*",
        destination: "http://localhost:8080/auth/:path*",
      },
      // Proxy user-service via api-gateway (evita CORS do browser → :8080)
      {
        source: "/api/users/:path*",
        destination: "http://localhost:8080/api/users/:path*",
      },
    ];
  },
};

export default withPWA(nextConfig);
