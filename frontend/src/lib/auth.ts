/*
 * Camada de acesso aos tokens armazenados no sessionStorage.
 *
 * Por que sessionStorage e não localStorage?
 *   sessionStorage é isolado por aba e limpo automaticamente quando a aba fecha.
 *   localStorage persiste indefinidamente — um token de longa duração em
 *   localStorage é alvo de ataques XSS (qualquer script na página pode lê-lo).
 *   Para um SPA de estudo, sessionStorage oferece um equilíbrio razoável entre
 *   usabilidade e segurança.
 *
 * Alternativa mais segura (produção): HttpOnly cookies gerenciados server-side
 * (ex: Next.js route handlers ou NextAuth.js).
 */

const ACCESS_KEY = 'access_token';
const REFRESH_KEY = 'refresh_token';
const ID_KEY = 'id_token';

export function saveTokens(access: string, refresh: string, id?: string): void {
  sessionStorage.setItem(ACCESS_KEY, access);
  sessionStorage.setItem(REFRESH_KEY, refresh);
  if (id) sessionStorage.setItem(ID_KEY, id);
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY);
}

export function getIdToken(): string | null {
  return sessionStorage.getItem(ID_KEY);
}

export function clearTokens(): void {
  sessionStorage.removeItem(ACCESS_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
  sessionStorage.removeItem(ID_KEY);
}
