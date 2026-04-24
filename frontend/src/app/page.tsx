"use client";

import { AUTH_URL, CLIENT_ID, REDIRECT_URI } from "@/lib/config";
import { generateCodeChallenge, generateCodeVerifier } from "@/lib/pkce";

/*
 * Landing page — único botão que inicia o fluxo OAuth2 Authorization Code + PKCE.
 *
 * O click handler:
 *   1. Gera code_verifier (128 bytes aleatórios) + code_challenge (SHA-256 do verifier)
 *   2. Persiste o verifier no sessionStorage — precisamos dele no /callback para
 *      trocar o authorization code pelos tokens.
 *   3. Redireciona o browser para o Keycloak com os parâmetros PKCE corretos.
 *
 * O Keycloak exibe sua tela de login nativa. Após autenticação, redireciona para
 * REDIRECT_URI (/callback) com ?code=<authorization_code>.
 */
export default function HomePage() {
  async function handleLogin() {
    const verifier   = await generateCodeVerifier();
    const challenge  = await generateCodeChallenge(verifier);

    sessionStorage.setItem("pkce_verifier", verifier);

    const params = new URLSearchParams({
      client_id:             CLIENT_ID,
      redirect_uri:          REDIRECT_URI,
      response_type:         "code",
      scope:                 "openid profile",
      code_challenge:        challenge,
      code_challenge_method: "S256",
    });

    window.location.href = `${AUTH_URL}?${params}`;
  }

  return (
    <div className="card">
      <h1>Study App</h1>
      <button
        className="btn btn-primary"
        data-testid="login-button"
        onClick={handleLogin}
      >
        Entrar com Keycloak
      </button>
    </div>
  );
}
