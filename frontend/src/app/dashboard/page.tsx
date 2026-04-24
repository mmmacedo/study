"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearTokens, getAccessToken, getIdToken, getRefreshToken } from "@/lib/auth";
import { CLIENT_ID, END_SESSION_URL, INTROSPECT_URL, LOGOUT_ENDPOINT } from "@/lib/config";

interface UserInfo {
  sub:               string;
  preferredUsername: string;
  roles:             string[];
  exp:               number;
}

/*
 * Dashboard — área protegida exibida após autenticação bem-sucedida.
 *
 * Proteção client-side:
 *   Verifica o token no sessionStorage. Sem token → redireciona para /.
 *   Nota: proteção real exigiria validação server-side (Next.js middleware ou route handler).
 *   Para este projeto de estudo, client-side é suficiente.
 *
 * Dados exibidos via /api/auth/introspect (proxy → api-gateway → auth-service):
 *   O auth-service decodifica o JWT localmente (sem chamada extra ao Keycloak)
 *   e retorna as claims: sub, preferredUsername, roles, exp.
 */
export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser]     = useState<UserInfo | null>(null);
  const [error, setError]   = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      router.push("/");
      return;
    }

    fetch(INTROSPECT_URL, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error(`Introspect falhou: ${res.status}`);
        return res.json() as Promise<UserInfo>;
      })
      .then((data) => setUser(data))
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : "Erro ao carregar perfil.");
      })
      .finally(() => setLoading(false));
  }, [router]);

  async function handleLogout() {
    const refreshToken = getRefreshToken();

    // 1. Revoga o refresh_token no auth-service (best-effort, fire-and-forget).
    //    Não usamos await: o redirect end_session não deve ser bloqueado pelo
    //    tempo de resposta do auth-service → Keycloak. A revogação é auxiliar;
    //    o encerramento real da sessão SSO ocorre via end_session redirect.
    if (refreshToken) {
      fetch(LOGOUT_ENDPOINT, {
        method:  "POST",
        headers: { "Content-Type": "application/json",
                   Authorization: `Bearer ${getAccessToken()}` },
        body: JSON.stringify({ refresh_token: refreshToken }),
      }).catch(() => {/* logout best-effort */});
    }

    const idToken = getIdToken();
    clearTokens();

    // 2. Redireciona o browser para o end_session do Keycloak.
    //    Sem este passo, o cookie SSO do Keycloak permanece ativo:
    //    na próxima visita o Keycloak faz auto-login silencioso sem mostrar o form.
    //    client_id + post_logout_redirect_uri: abordagem principal (Keycloak 26.x RP-Initiated Logout).
    //    id_token_hint: fornecido quando disponível para validação extra.
    //    Sem client_id ou id_token_hint, Keycloak 26.x exibe página de confirmação
    //    e não redireciona automaticamente para post_logout_redirect_uri.
    const params = new URLSearchParams({
      post_logout_redirect_uri: "http://localhost:3000",
      client_id: CLIENT_ID,
      ...(idToken ? { id_token_hint: idToken } : {}),
    });
    window.location.href = `${END_SESSION_URL}?${params}`;
  }

  if (loading) return <div className="spinner">Carregando…</div>;

  if (error) {
    return (
      <div className="card">
        <div className="error" data-testid="dashboard-error">{error}</div>
        <button className="btn btn-secondary" onClick={() => router.push("/")}>
          Voltar
        </button>
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="card">
      <h2>Bem-vindo</h2>

      <div className="info-row">
        <span className="info-label">Usuário</span>
        <span className="info-value" data-testid="username-display">
          {user.preferredUsername}
        </span>
      </div>

      <div className="info-row">
        <span className="info-label">Roles</span>
        <span className="info-value" data-testid="roles-display">
          {user.roles.join(", ")}
        </span>
      </div>

      <div className="info-row">
        <span className="info-label">Expira em</span>
        <span className="info-value" data-testid="expiry-display">
          {new Date(user.exp * 1000).toLocaleString("pt-BR")}
        </span>
      </div>

      <button
        className="btn btn-danger"
        data-testid="logout-button"
        onClick={handleLogout}
      >
        Sair
      </button>
    </div>
  );
}
