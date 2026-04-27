'use client';

import { useState, useEffect } from 'react';
import { usePathname } from 'next/navigation';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Avatar from '@mui/material/Avatar';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import MenuIcon from '@mui/icons-material/Menu';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import LogoutIcon from '@mui/icons-material/Logout';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import { clearTokens, getAccessToken, getIdToken, getRefreshToken } from '@/lib/auth';
import { CLIENT_ID, END_SESSION_URL, LOGOUT_ENDPOINT } from '@/lib/config';
import { SIDEBAR_WIDTH } from './Sidebar';

const ROUTE_TITLES: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/users': 'Usuários',
  '/projects': 'Projetos',
  '/reports': 'Relatórios',
  '/settings': 'Configurações',
};

function decodeUsername(token: string): string {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return (payload.preferred_username as string) ?? (payload.sub as string) ?? 'Usuário';
  } catch {
    return 'Usuário';
  }
}

interface NavbarProps {
  onToggleSidebar: () => void;
}

export default function Navbar({ onToggleSidebar }: NavbarProps) {
  const pathname = usePathname();
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [username, setUsername] = useState('');

  useEffect(() => {
    const token = getAccessToken();
    if (token) setUsername(decodeUsername(token));
  }, []);

  const title = ROUTE_TITLES[pathname] ?? 'Dashboard';
  const initials = username.slice(0, 2).toUpperCase() || '??';

  function handleLogout() {
    setMenuAnchor(null);
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

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        left: { md: SIDEBAR_WIDTH },
        width: { md: `calc(100% - ${SIDEBAR_WIDTH}px)` },
        bgcolor: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        zIndex: (t) => t.zIndex.drawer - 1,
        color: 'text.primary',
      }}
    >
      <Toolbar sx={{ px: { xs: 2, sm: 3 }, minHeight: { xs: 56, sm: 64 } }}>
        {/* Mobile hamburger */}
        <IconButton
          edge="start"
          onClick={onToggleSidebar}
          sx={{ mr: 1, display: { md: 'none' }, color: 'text.primary' }}
        >
          <MenuIcon />
        </IconButton>

        {/* Page title */}
        <Typography
          variant="h6"
          sx={{ flex: 1, fontWeight: 700, fontSize: { xs: '1rem', sm: '1.125rem' } }}
        >
          {title}
        </Typography>

        {/* Right actions */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Tooltip title="Notificações">
            <IconButton size="small" sx={{ color: 'text.secondary' }}>
              <NotificationsNoneIcon fontSize="small" />
            </IconButton>
          </Tooltip>

          <Tooltip title="Sua conta">
            <IconButton
              data-testid="user-avatar-button"
              onClick={(e) => setMenuAnchor(e.currentTarget)}
              sx={{ ml: 0.5, p: 0.5 }}
            >
              <Avatar
                sx={{
                  width: 34,
                  height: 34,
                  bgcolor: 'primary.main',
                  fontSize: '0.8125rem',
                  fontWeight: 700,
                }}
              >
                {initials}
              </Avatar>
            </IconButton>
          </Tooltip>
        </Box>

        <Menu
          anchorEl={menuAnchor}
          open={Boolean(menuAnchor)}
          onClose={() => setMenuAnchor(null)}
          transformOrigin={{ horizontal: 'right', vertical: 'top' }}
          anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
          slotProps={{
            paper: {
              elevation: 2,
              sx: {
                mt: 1,
                minWidth: 210,
                borderRadius: 2,
                border: '1px solid',
                borderColor: 'divider',
                overflow: 'visible',
                '&::before': {
                  content: '""',
                  display: 'block',
                  position: 'absolute',
                  top: -6,
                  right: 18,
                  width: 12,
                  height: 12,
                  bgcolor: 'background.paper',
                  borderTop: '1px solid',
                  borderLeft: '1px solid',
                  borderColor: 'divider',
                  transform: 'rotate(45deg)',
                  zIndex: 0,
                },
              },
            },
          }}
        >
          <Box sx={{ px: 2, py: 1.5 }}>
            <Typography variant="subtitle2" fontWeight={700}>
              {username}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Usuário autenticado
            </Typography>
          </Box>
          <Divider />
          <MenuItem sx={{ py: 1, gap: 1.5, color: 'text.secondary' }} disabled>
            <ListItemIcon sx={{ minWidth: 0 }}>
              <PersonOutlineIcon fontSize="small" />
            </ListItemIcon>
            Meu perfil
          </MenuItem>
          <MenuItem onClick={handleLogout} sx={{ py: 1, gap: 1.5, color: 'error.main' }}>
            <ListItemIcon sx={{ minWidth: 0 }}>
              <LogoutIcon fontSize="small" sx={{ color: 'error.main' }} />
            </ListItemIcon>
            Sair
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
