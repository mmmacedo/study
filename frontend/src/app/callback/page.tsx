'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState, Suspense } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import { CLIENT_ID, REDIRECT_URI, TOKEN_URL } from '@/lib/config';
import { saveTokens } from '@/lib/auth';

function FullPageCenter({ children }: { children: React.ReactNode }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        bgcolor: 'background.default',
        px: 2,
      }}
    >
      {children}
    </Box>
  );
}

/*
 * Callback page — recebe o authorization code do Keycloak e o troca pelos tokens.
 *
 * Fluxo:
 *   1. Keycloak redireciona para /callback?code=<code>&session_state=...
 *   2. Lemos o code da URL e o code_verifier do sessionStorage
 *   3. POST ao Keycloak token endpoint com PKCE (code + verifier)
 *   4. Keycloak verifica: SHA-256(verifier) == challenge enviado na autorização
 *   5. Sucesso → salva tokens → redireciona para /dashboard
 *   6. Erro → exibe Alert (data-testid="callback-error")
 */
function CallbackHandler() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get('code');
    const verifier = sessionStorage.getItem('pkce_verifier');

    if (!code || !verifier) {
      setError('Parâmetros de autenticação ausentes. Tente novamente.');
      return;
    }

    sessionStorage.removeItem('pkce_verifier');

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      code,
      code_verifier: verifier,
    });

    fetch(TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    })
      .then((res) => {
        if (!res.ok) throw new Error(`Token exchange failed: ${res.status}`);
        return res.json();
      })
      .then((data) => {
        saveTokens(data.access_token, data.refresh_token, data.id_token);
        router.push('/dashboard');
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Erro ao autenticar.');
      });
  }, [searchParams, router]);

  if (error) {
    return (
      <FullPageCenter>
        <Card sx={{ maxWidth: 420, width: '100%' }}>
          <CardContent sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom fontWeight={700}>
              Erro de autenticação
            </Typography>
            <Alert severity="error" data-testid="callback-error" sx={{ mb: 2 }}>
              {error}
            </Alert>
            <Button variant="outlined" fullWidth onClick={() => router.push('/')}>
              Tentar novamente
            </Button>
          </CardContent>
        </Card>
      </FullPageCenter>
    );
  }

  return (
    <FullPageCenter>
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
        <CircularProgress size={36} />
        <Typography color="text.secondary" variant="body2">
          Autenticando…
        </Typography>
      </Box>
    </FullPageCenter>
  );
}

export default function CallbackPage() {
  return (
    <Suspense
      fallback={
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
          }}
        >
          <CircularProgress size={36} />
        </Box>
      }
    >
      <CallbackHandler />
    </Suspense>
  );
}
