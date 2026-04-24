/*
 * PKCE — Proof Key for Code Exchange (RFC 7636)
 *
 * Por que PKCE em vez de um client secret?
 *   O client secret não pode ser embutido em um SPA — qualquer usuário
 *   pode inspecionar o código e extraí-lo. PKCE substitui o secret por
 *   um par de valores: code_verifier (segredo efêmero, fica no browser)
 *   e code_challenge (hash do verifier, enviado ao servidor).
 *
 * Fluxo:
 *   1. Browser gera code_verifier aleatório e calcula code_challenge = SHA-256(verifier)
 *   2. Browser envia code_challenge ao authorization endpoint do Keycloak
 *   3. Keycloak emite um authorization code ligado a esse challenge
 *   4. Browser troca o code + code_verifier pelo token
 *   5. Keycloak verifica SHA-256(verifier) == challenge antes de emitir o token
 *
 * Se um atacante interceptar o authorization code (ex: via redirect URI malicioso),
 * ele não consegue trocar o code por tokens porque não tem o code_verifier original.
 */

/** Gera um code_verifier aleatório de 128 bytes, codificado em base64url. */
export async function generateCodeVerifier(): Promise<string> {
  const array = new Uint8Array(96); // 96 bytes → 128 chars base64url
  crypto.getRandomValues(array);
  return base64urlEncode(array);
}

/** Calcula SHA-256(verifier) e retorna em base64url — o code_challenge. */
export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return base64urlEncode(new Uint8Array(digest));
}

function base64urlEncode(bytes: Uint8Array): string {
  // btoa só aceita string binária; convertemos o Uint8Array para isso
  const binary = Array.from(bytes, (b) => String.fromCharCode(b)).join("");
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}
