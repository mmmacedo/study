import { createTheme, type Shadows } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    primary: {
      main: '#6366f1',
      light: '#a5b4fc',
      dark: '#4338ca',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#10b981',
      contrastText: '#ffffff',
    },
    error:   { main: '#ef4444' },
    warning: { main: '#f59e0b' },
    success: { main: '#22c55e' },
    background: {
      default: '#f1f5f9',
      paper:   '#ffffff',
    },
    text: {
      primary:   '#0f172a',
      secondary: '#64748b',
    },
    divider: '#e2e8f0',
  },
  typography: {
    fontFamily: '"Inter", "system-ui", -apple-system, "BlinkMacSystemFont", sans-serif',
    h4: { fontWeight: 700, lineHeight: 1.3 },
    h5: { fontWeight: 700, lineHeight: 1.35 },
    h6: { fontWeight: 600 },
    subtitle1: { fontWeight: 500 },
    subtitle2: { fontWeight: 600, fontSize: '0.8125rem' },
    body2:     { fontSize: '0.875rem' },
    caption:   { fontSize: '0.75rem' },
    button:    { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
  },
  shape: { borderRadius: 10 },
  shadows: [
    'none',
    '0 1px 2px rgba(0,0,0,0.04), 0 1px 3px rgba(0,0,0,0.06)',
    '0 1px 4px rgba(0,0,0,0.06), 0 2px 8px rgba(0,0,0,0.06)',
    '0 2px 8px rgba(0,0,0,0.08), 0 4px 16px rgba(0,0,0,0.06)',
    '0 4px 16px rgba(0,0,0,0.10), 0 8px 32px rgba(0,0,0,0.06)',
    ...Array(20).fill('0 4px 20px rgba(0,0,0,0.10), 0 8px 40px rgba(0,0,0,0.06)'),
  ] as Shadows,
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
        sizeMedium: { padding: '8px 18px' },
      },
    },
    MuiCard: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: { border: '1px solid #e2e8f0', borderRadius: 12 },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, fontSize: '0.75rem' },
      },
    },
    MuiTableHead: {
      styleOverrides: {
        root: { '& .MuiTableCell-head': { backgroundColor: '#f8fafc' } },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          fontWeight: 600,
          fontSize: '0.75rem',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          color: '#64748b',
          paddingTop: 10,
          paddingBottom: 10,
        },
        body: { fontSize: '0.875rem', borderColor: '#f1f5f9' },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          '&:last-child td': { border: 0 },
          '&:hover': { backgroundColor: '#f8fafc' },
          transition: 'background-color 0.1s ease',
        },
      },
    },
    MuiTextField: {
      defaultProps: { size: 'small' },
    },
    MuiDialogTitle: {
      styleOverrides: {
        root: { fontWeight: 700, fontSize: '1.0625rem', paddingBottom: 8 },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 8 },
      },
    },
    MuiInputBase: {
      styleOverrides: {
        root: { borderRadius: '8px !important' },
      },
    },
  },
});

export default theme;
