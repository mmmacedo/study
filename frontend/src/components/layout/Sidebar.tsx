'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import Box from '@mui/material/Box';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PeopleIcon from '@mui/icons-material/People';
import FolderIcon from '@mui/icons-material/Folder';
import BarChartIcon from '@mui/icons-material/BarChart';
import SettingsIcon from '@mui/icons-material/Settings';
import BoltIcon from '@mui/icons-material/Bolt';

export const SIDEBAR_WIDTH = 240;

const SIDEBAR_BG    = '#0f172a';
const ACTIVE_BG     = 'rgba(99,102,241,0.18)';
const ACTIVE_BORDER = '#6366f1';
const TEXT_DIM      = 'rgba(255,255,255,0.55)';
const TEXT_ACTIVE   = '#ffffff';

interface NavItem {
  label: string;
  icon: React.ReactNode;
  href: string;
  soon?: boolean;
}

const primaryNav: NavItem[] = [
  { label: 'Dashboard', icon: <DashboardIcon fontSize="small" />, href: '/dashboard' },
  { label: 'Usuários',  icon: <PeopleIcon   fontSize="small" />, href: '/users' },
];

const comingSoonNav: NavItem[] = [
  { label: 'Projetos',   icon: <FolderIcon    fontSize="small" />, href: '/projects',  soon: true },
  { label: 'Relatórios', icon: <BarChartIcon  fontSize="small" />, href: '/reports',   soon: true },
];

const bottomNav: NavItem[] = [
  { label: 'Configurações', icon: <SettingsIcon fontSize="small" />, href: '/settings', soon: true },
];

function NavRow({ item, active }: { item: NavItem; active: boolean }) {
  return (
    <ListItem disablePadding sx={{ mb: 0.25 }}>
      <ListItemButton
        component={item.soon ? 'div' : Link}
        {...(!item.soon && { href: item.href })}
        disabled={item.soon}
        sx={{
          borderRadius: '8px',
          mx: 1,
          py: 0.9,
          pl: 1.5,
          color: active ? TEXT_ACTIVE : TEXT_DIM,
          bgcolor: active ? ACTIVE_BG : 'transparent',
          borderLeft: `3px solid ${active ? ACTIVE_BORDER : 'transparent'}`,
          '&:hover:not(.Mui-disabled)': {
            bgcolor: active ? ACTIVE_BG : 'rgba(255,255,255,0.05)',
            color: TEXT_ACTIVE,
          },
          '&.Mui-disabled': { opacity: 0.45, cursor: 'not-allowed', pointerEvents: 'all' },
          transition: 'all 0.14s ease',
        }}
      >
        <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>{item.icon}</ListItemIcon>
        <ListItemText
          primary={item.label}
          primaryTypographyProps={{
            fontSize: '0.875rem',
            fontWeight: active ? 600 : 400,
            lineHeight: 1.4,
          }}
        />
        {item.soon && (
          <Box
            component="span"
            sx={{
              bgcolor: 'rgba(255,255,255,0.08)',
              color: 'rgba(255,255,255,0.4)',
              fontSize: '0.625rem',
              fontWeight: 700,
              letterSpacing: '0.06em',
              px: 0.75,
              py: 0.3,
              borderRadius: '4px',
              textTransform: 'uppercase',
              lineHeight: 1,
            }}
          >
            Soon
          </Box>
        )}
      </ListItemButton>
    </ListItem>
  );
}

function SectionLabel({ children }: { children: string }) {
  return (
    <Typography
      variant="caption"
      sx={{
        display: 'block',
        px: 2.5,
        pt: 2,
        pb: 0.5,
        color: 'rgba(255,255,255,0.3)',
        fontWeight: 700,
        textTransform: 'uppercase',
        letterSpacing: '0.08em',
        fontSize: '0.6875rem',
      }}
    >
      {children}
    </Typography>
  );
}

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <Box
      sx={{
        width: SIDEBAR_WIDTH,
        height: '100vh',
        bgcolor: SIDEBAR_BG,
        display: 'flex',
        flexDirection: 'column',
        position: 'fixed',
        top: 0,
        left: 0,
        overflowY: 'auto',
        overflowX: 'hidden',
        borderRight: '1px solid rgba(255,255,255,0.05)',
        '&::-webkit-scrollbar': { width: 4 },
        '&::-webkit-scrollbar-track': { bgcolor: 'transparent' },
        '&::-webkit-scrollbar-thumb': { bgcolor: 'rgba(255,255,255,0.1)', borderRadius: 2 },
      }}
    >
      {/* Brand */}
      <Box sx={{ px: 2.5, py: 2.5, display: 'flex', alignItems: 'center', gap: 1.25 }}>
        <Box
          sx={{
            width: 34,
            height: 34,
            bgcolor: '#6366f1',
            borderRadius: '9px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            boxShadow: '0 0 0 1px rgba(99,102,241,0.4), 0 4px 12px rgba(99,102,241,0.3)',
          }}
        >
          <BoltIcon sx={{ color: '#fff', fontSize: 18 }} />
        </Box>
        <Box>
          <Typography
            sx={{
              fontWeight: 800,
              color: '#ffffff',
              fontSize: '0.9375rem',
              lineHeight: 1.2,
              letterSpacing: '-0.025em',
            }}
          >
            ProjectSaaS
          </Typography>
          <Typography sx={{ fontSize: '0.6875rem', color: 'rgba(255,255,255,0.35)', lineHeight: 1 }}>
            Gestão de Projetos
          </Typography>
        </Box>
      </Box>

      <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', mx: 2 }} />

      {/* Primary nav */}
      <Box sx={{ flex: 1, pt: 0.5 }}>
        <SectionLabel>Menu</SectionLabel>
        <List sx={{ px: 0.5 }} disablePadding>
          {primaryNav.map((item) => (
            <NavRow key={item.href} item={item} active={pathname === item.href} />
          ))}
        </List>

        <SectionLabel>Em breve</SectionLabel>
        <List sx={{ px: 0.5 }} disablePadding>
          {comingSoonNav.map((item) => (
            <NavRow key={item.href} item={item} active={false} />
          ))}
        </List>
      </Box>

      {/* Bottom nav */}
      <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', mx: 2 }} />
      <List sx={{ px: 0.5, py: 1 }} disablePadding>
        {bottomNav.map((item) => (
          <NavRow key={item.href} item={item} active={false} />
        ))}
      </List>
    </Box>
  );
}
