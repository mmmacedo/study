"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, Suspense } from "react";
import { CLIENT_ID, REDIRECT_URI, TOKEN_URL } from "@/lib/config";
import { saveTokens } from "@/lib/auth";

/*
 * Callback page — recebe o authorization code do Keycloak e o troca pelos tokens.
 *
 * Fluxo:
 *   1. Keycloak redireciona para /callback?code=<code>&session_state=...
 *   2. Lemos o code da URL e o code_verifier do sessionStorage
 *   3. POST ao Keycloak token endpoint com PKCE (code + verifier)
 *   4. Keycloak verifica: SHA-256(verifier) == challenge enviado no passo anterior
 *   5. Sucesso → salva tokens → redireciona para /dashboard
 *   6. Erro → exibe mensagem (data-testid="callback-error")
 *
 * Por que "use client"?
 *   useSearchParams só funciona em client components. O authorization code está
 *   na URL e precisa ser lido no browser — não no servidor.
 */
function CallbackHandler() {
  const router       = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code     = searchParams.get("code");
    const verifier = sessionStorage.getItem("pkce_verifier");

    if (!code || !verifier) {
      setError("Parâmetros de autenticação ausentes. Tente novamente.");
      return;
    }

    sessionStorage.removeItem("pkce_verifier");

    const body = new URLSearchParams({
      grant_type:    "authorization_code",
      client_id:     CLIENT_ID,
      redirect_uri:  REDIRECT_URI,
      code,
      code_verifier: verifier,
    });

    fetch(TOKEN_URL, {
      method:  "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body:    body.toString(),
    })
      .then((res) => {
        if (!res.ok) throw new Error(`Token exchange failed: ${res.status}`);
        return res.json();
      })
      .then((data) => {
        saveTokens(data.access_token, data.refresh_token, data.id_token);
        router.push("/dashboard");
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Erro ao autenticar.");
      });
  }, [searchParams, router]);

  if (error) {
    return (
      <div className="card">
        <div className="error" data-testid="callback-error">{error}</div>
        <button className="btn btn-secondary" onClick={() => router.push("/")}>
          Voltar
        </button>
      </div>
    );
  }

  return <div className="spinner">Autenticando…</div>;
}

export default function CallbackPage() {
  return (
    <Suspense fallback={<div className="spinner">Carregando…</div>}>
      <CallbackHandler />
    </Suspense>
  );
}
