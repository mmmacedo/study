'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { generateCodeChallenge, generateCodeVerifier } from '@/lib/pkce';
import { getAccessToken } from '@/lib/auth';
import { AUTH_URL, CLIENT_ID, REDIRECT_URI } from '@/lib/config';

export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    if (getAccessToken()) {
      router.push('/dashboard');
      return;
    }

    (async () => {
      const verifier  = await generateCodeVerifier();
      const challenge = await generateCodeChallenge(verifier);
      sessionStorage.setItem('pkce_verifier', verifier);

      const params = new URLSearchParams({
        client_id:             CLIENT_ID,
        redirect_uri:          REDIRECT_URI,
        response_type:         'code',
        scope:                 'openid profile',
        code_challenge:        challenge,
        code_challenge_method: 'S256',
      });

      window.location.href = `${AUTH_URL}?${params}`;
    })();
  }, [router]);

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        bgcolor: 'background.default',
      }}
    >
      <CircularProgress size={32} />
    </Box>
  );
}
