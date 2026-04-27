'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActionArea from '@mui/material/CardActionArea';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Divider from '@mui/material/Divider';
import PeopleIcon from '@mui/icons-material/People';
import FolderIcon from '@mui/icons-material/Folder';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import LogoutIcon from '@mui/icons-material/Logout';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AppLayout from '@/components/layout/AppLayout';
import { clearTokens, getAccessToken, getIdToken, getRefreshToken } from '@/lib/auth';
import { CLIENT_ID, END_SESSION_URL, INTROSPECT_URL, LOGOUT_ENDPOINT } from '@/lib/config';

interface UserInfo {
  sub: string;
  preferredUsername: string;
  roles: string[];
  exp: number;
}

/*
 * Dashboard — área protegida exibida após autenticação bem-sucedida.
 * Proteção client-side: redireciona para / se não houver token.
 */
export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser] = useState<UserInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) {
      router.push('/');
      return;
    }

    fetch(INTROSPECT_URL, { headers: { Authorization: `Bearer ${token}` } })
      .then((res) => {
        if (!res.ok) throw new Error(`Introspect falhou: ${res.status}`);
        return res.json() as Promise<UserInfo>;
      })
      .then(setUser)
      .catch((err: unknown) =>
        setError(err instanceof Error ? err.message : 'Erro ao carregar perfil.'),
      )
      .finally(() => setLoading(false));
  }, [router]);

  function handleLogout() {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      fetch(LOGOUT_ENDPOINT, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${getAccessToken()}`,
        },
        body: JSON.stringify({ refresh_token: refreshToken }),
      }).catch(() => {});
    }
    const idToken = getIdToken();
    clearTokens();
    const params = new URLSearchParams({
      post_logout_redirect_uri: 'http://localhost:3000',
      client_id: CLIENT_ID,
      ...(idToken ? { id_token_hint: idToken } : {}),
    });
    window.location.href = `${END_SESSION_URL}?${params}`;
  }

  const expDate = user ? new Date(user.exp * 1000).toLocaleString('pt-BR') : '';
  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 12) return 'Bom dia';
    if (h < 18) return 'Boa tarde';
    return 'Boa noite';
  })();

  return (
    <AppLayout>
      {/* Welcome banner */}
      <Box
        sx={{
          mb: 3,
          p: { xs: 2.5, sm: 3 },
          borderRadius: 3,
          background: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
          color: '#fff',
          position: 'relative',
          overflow: 'hidden',
          '&::after': {
            content: '""',
            position: 'absolute',
            top: -40,
            right: -40,
            width: 180,
            height: 180,
            borderRadius: '50%',
            bgcolor: 'rgba(255,255,255,0.08)',
          },
        }}
      >
        {loading ? (
          <>
            <Skeleton
              variant="text"
              width={220}
              height={36}
              sx={{ bgcolor: 'rgba(255,255,255,0.2)' }}
            />
            <Skeleton
              variant="text"
              width={160}
              height={20}
              sx={{ bgcolor: 'rgba(255,255,255,0.15)', mt: 0.5 }}
            />
          </>
        ) : (
          <>
            <Typography variant="h5" fontWeight={700}>
              {greeting}, {user?.preferredUsername ?? 'usuário'}! 👋
            </Typography>
            <Typography sx={{ mt: 0.5, opacity: 0.85, fontSize: '0.9375rem' }}>
              Bem-vindo ao ProjectSaaS. Aqui está seu resumo de hoje.
            </Typography>
          </>
        )}
      </Box>

      {error && (
        <Alert severity="error" data-testid="dashboard-error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Quick-access cards */}
      <Grid container spacing={2.5} sx={{ mb: 3 }} component="div">
        {[
          {
            icon: <PeopleIcon />,
            label: 'Usuários',
            desc: 'Gerencie usuários do sistema',
            href: '/users',
            color: '#6366f1',
            active: true,
          },
          {
            icon: <FolderIcon />,
            label: 'Projetos',
            desc: 'Em breve',
            href: '#',
            color: '#10b981',
            active: false,
          },
          {
            icon: <TrendingUpIcon />,
            label: 'Relatórios',
            desc: 'Em breve',
            href: '#',
            color: '#f59e0b',
            active: false,
          },
        ].map((item) => (
          <Grid item xs={12} sm={4} key={item.label}>
            <Card sx={{ height: '100%', opacity: item.active ? 1 : 0.55 }}>
              <CardActionArea
                component={item.active ? Link : 'div'}
                {...(item.active && { href: item.href })}
                sx={{
                  p: 2.5,
                  height: '100%',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                }}
              >
                <Box
                  sx={{
                    width: 44,
                    height: 44,
                    borderRadius: '12px',
                    bgcolor: `${item.color}1A`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: item.color,
                    mb: 1.5,
                  }}
                >
                  {item.icon}
                </Box>
                <Typography variant="subtitle1" fontWeight={700}>
                  {item.label}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                  {item.desc}
                </Typography>
                {item.active && (
                  <Box
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 0.5,
                      mt: 1.5,
                      color: item.color,
                      fontSize: '0.8125rem',
                      fontWeight: 600,
                    }}
                  >
                    Acessar <ArrowForwardIcon sx={{ fontSize: 14 }} />
                  </Box>
                )}
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Profile card — data-testid attrs preserved for Selenium */}
      <Card sx={{ maxWidth: 520 }}>
        <CardContent sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2.5 }}>
            <Avatar
              sx={{
                width: 52,
                height: 52,
                bgcolor: 'primary.main',
                fontSize: '1.25rem',
                fontWeight: 700,
              }}
            >
              {user?.preferredUsername?.slice(0, 2).toUpperCase() ?? '??'}
            </Avatar>
            <Box>
              <Typography variant="subtitle1" fontWeight={700}>
                {loading ? <Skeleton width={120} /> : user?.preferredUsername}
              </Typography>
              <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mt: 0.5 }}>
                {loading ? (
                  <Skeleton width={60} height={24} variant="rounded" />
                ) : (
                  user?.roles.map((r) => (
                    <Chip
                      key={r}
                      label={r}
                      size="small"
                      color={r === 'ADMIN' ? 'primary' : 'default'}
                      sx={{ fontWeight: 700, fontSize: '0.6875rem' }}
                    />
                  ))
                )}
              </Box>
            </Box>
          </Box>

          <Divider sx={{ mb: 2 }} />

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2" color="text.secondary">
                Usuário
              </Typography>
              <Typography variant="body2" fontWeight={600} data-testid="username-display">
                {loading ? <Skeleton width={100} /> : user?.preferredUsername}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2" color="text.secondary">
                Perfil
              </Typography>
              <Typography variant="body2" fontWeight={600} data-testid="roles-display">
                {loading ? <Skeleton width={80} /> : user?.roles.join(', ')}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body2" color="text.secondary">
                Token expira em
              </Typography>
              <Typography variant="body2" fontWeight={600} data-testid="expiry-display">
                {loading ? <Skeleton width={130} /> : expDate}
              </Typography>
            </Box>
          </Box>

          <Divider sx={{ my: 2 }} />

          <Button
            variant="outlined"
            color="error"
            size="small"
            startIcon={<LogoutIcon />}
            onClick={handleLogout}
            data-testid="logout-button"
          >
            Sair da conta
          </Button>
        </CardContent>
      </Card>
    </AppLayout>
  );
}
