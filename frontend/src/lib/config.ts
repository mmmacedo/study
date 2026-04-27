const KEYCLOAK_BASE = process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? 'http://localhost:8180';
const REALM = 'study';

export const CLIENT_ID = 'study-api';
export const REDIRECT_URI = 'http://localhost:3000/callback';

export const AUTH_URL = `${KEYCLOAK_BASE}/realms/${REALM}/protocol/openid-connect/auth`;
export const TOKEN_URL = `${KEYCLOAK_BASE}/realms/${REALM}/protocol/openid-connect/token`;

// Chamados via rewrite do Next.js → api-gateway → auth-service
export const INTROSPECT_URL = '/api/auth/introspect';
export const LOGOUT_ENDPOINT = '/api/auth/logout';

// Keycloak end_session: redireciona o browser para limpar o cookie SSO.
// Sem este redirect, a sessão SSO persiste e o Keycloak faz auto-login silencioso.
export const END_SESSION_URL = `${KEYCLOAK_BASE}/realms/${REALM}/protocol/openid-connect/logout`;
