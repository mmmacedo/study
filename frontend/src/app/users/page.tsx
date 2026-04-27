'use client';

import { useEffect, useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import Snackbar from '@mui/material/Snackbar';
import Divider from '@mui/material/Divider';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import PeopleIcon from '@mui/icons-material/People';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import AppLayout from '@/components/layout/AppLayout';
import { useUsers } from '@/hooks/useUsers';
import { clearTokens, getAccessToken } from '@/lib/auth';
import type { CreateUserPayload, ApiError, User } from '@/types/user';

/* ── helpers ── */

const AVATAR_PALETTE = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', '#ef4444'];

function avatarColor(name: string): string {
  return AVATAR_PALETTE[name.charCodeAt(0) % AVATAR_PALETTE.length];
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day:   '2-digit',
    month: 'short',
    year:  'numeric',
  });
}

/* ── stat card ── */

interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: number;
  color: string;
  loading: boolean;
}

function StatCard({ icon, label, value, color, loading }: StatCardProps) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ p: 2.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {label}
            </Typography>
            <Typography variant="h4" fontWeight={700} sx={{ mt: 0.5, lineHeight: 1 }}>
              {loading ? <Skeleton width={48} /> : value}
            </Typography>
          </Box>
          <Box
            sx={{
              width: 46, height: 46,
              borderRadius: '12px',
              bgcolor: `${color}1A`,
              color,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

/* ── create user dialog ── */

interface CreateDialogProps {
  open:     boolean;
  onClose:  () => void;
  onCreate: (payload: CreateUserPayload) => Promise<ApiError | undefined>;
}

function CreateUserDialog({ open, onClose, onCreate }: CreateDialogProps) {
  const [name,     setName]     = useState('');
  const [email,    setEmail]    = useState('');
  const [fieldErr, setFieldErr] = useState<Record<string, string>>({});
  const [apiErr,   setApiErr]   = useState('');
  const [busy,     setBusy]     = useState(false);

  function reset() { setName(''); setEmail(''); setFieldErr({}); setApiErr(''); }
  function handleClose() { reset(); onClose(); }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!name.trim() || name.trim().length < 2) errs.name = 'Nome deve ter pelo menos 2 caracteres';
    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errs.email = 'Email inválido';
    if (Object.keys(errs).length) { setFieldErr(errs); return; }

    setBusy(true);
    setFieldErr({});
    setApiErr('');
    const err = await onCreate({ name: name.trim(), email: email.trim() });
    setBusy(false);
    if (err) {
      if (err.errors) setFieldErr(err.errors);
      else setApiErr(err.detail ?? err.title);
    } else {
      reset();
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>Novo Usuário</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Preencha os dados para criar um novo usuário. O perfil padrão é USER.
          </Typography>
          {apiErr && <Alert severity="error" sx={{ mb: 2 }}>{apiErr}</Alert>}
          <TextField
            label="Nome completo"
            fullWidth
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={Boolean(fieldErr.name)}
            helperText={fieldErr.name}
            sx={{ mb: 2 }}
            disabled={busy}
          />
          <TextField
            label="Email"
            type="email"
            fullWidth
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={Boolean(fieldErr.email)}
            helperText={fieldErr.email}
            disabled={busy}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={handleClose} disabled={busy} color="inherit">
            Cancelar
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={busy}
            startIcon={busy ? undefined : <AddIcon />}
          >
            {busy ? 'Criando…' : 'Criar usuário'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/* ── confirm deactivate dialog ── */

interface ConfirmDialogProps {
  user:      User | null;
  onClose:   () => void;
  onConfirm: () => Promise<void>;
}

function ConfirmDeactivateDialog({ user, onClose, onConfirm }: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false);

  async function handleConfirm() {
    setBusy(true);
    await onConfirm();
    setBusy(false);
  }

  return (
    <Dialog open={Boolean(user)} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Desativar usuário</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary">
          O usuário <strong>{user?.name}</strong> ({user?.email}) será desativado e não poderá mais
          acessar o sistema. Essa ação pode ser revertida.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        <Button onClick={onClose} disabled={busy} color="inherit">Cancelar</Button>
        <Button variant="contained" color="error" disabled={busy} onClick={handleConfirm}>
          {busy ? 'Desativando…' : 'Desativar'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/* ── skeleton rows ── */

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell><Skeleton variant="circular" width={36} height={36} /></TableCell>
          <TableCell><Skeleton width={160} /><Skeleton width={120} height={14} /></TableCell>
          <TableCell><Skeleton width={60} height={24} variant="rounded" /></TableCell>
          <TableCell><Skeleton width={60} height={24} variant="rounded" /></TableCell>
          <TableCell><Skeleton width={80} /></TableCell>
          <TableCell><Skeleton variant="circular" width={32} height={32} /></TableCell>
        </TableRow>
      ))}
    </>
  );
}

/* ── main page ── */

export default function UsersPage() {
  const router = useRouter();
  const { users, loading, error, authError, clearAuthError, createUser, deactivateUser } = useUsers();

  const [createOpen,  setCreateOpen]  = useState(false);
  const [targetUser,  setTargetUser]  = useState<User | null>(null);
  const [search,      setSearch]      = useState('');
  const [page,        setPage]        = useState(0);
  const rowsPerPage = 10;
  const [snack, setSnack] = useState<{ msg: string; ok: boolean } | null>(null);

  useEffect(() => {
    if (!getAccessToken()) router.push('/');
  }, [router]);

  useEffect(() => {
    if (authError === 'expired') {
      clearAuthError();
      clearTokens();
      router.push('/');
    }
  }, [authError, clearAuthError, router]);

  const filtered = useMemo(
    () => users.filter((u) => {
      const q = search.toLowerCase();
      return u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q);
    }),
    [users, search],
  );

  const paginated = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  const stats = useMemo(
    () => ({
      total:  users.length,
      active: users.filter((u) => u.active).length,
      admins: users.filter((u) => u.role === 'ADMIN').length,
    }),
    [users],
  );

  async function handleCreate(payload: CreateUserPayload) {
    const err = await createUser(payload);
    if (!err) {
      setCreateOpen(false);
      setSnack({ msg: 'Usuário criado com sucesso!', ok: true });
      return undefined;
    }
    if (err.status === 401) {
      clearTokens();
      router.push('/');
      return err;
    }
    if (err.status === 403) {
      setCreateOpen(false);
      setSnack({ msg: 'Permissão insuficiente. Esta ação requer role ADMIN.', ok: false });
      return err;
    }
    return err;
  }

  async function handleDeactivate() {
    if (!targetUser) return;
    const err = await deactivateUser(targetUser.id);
    setTargetUser(null);
    if (err?.status === 401) {
      clearTokens();
      router.push('/');
      return;
    }
    if (err?.status === 403) {
      setSnack({ msg: 'Permissão insuficiente. Esta ação requer role ADMIN.', ok: false });
      return;
    }
    setSnack(err
      ? { msg: err.detail ?? 'Erro ao desativar usuário.', ok: false }
      : { msg: `Usuário ${targetUser.name} foi desativado.`, ok: true },
    );
  }

  return (
    <AppLayout>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: { sm: 'center' }, justifyContent: 'space-between', flexDirection: { xs: 'column', sm: 'row' }, gap: 2, mb: 3 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Usuários</Typography>
          <Typography variant="body2" color="text.secondary">
            Gerencie os usuários do sistema
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          sx={{ alignSelf: { xs: 'flex-start', sm: 'auto' } }}
        >
          Novo usuário
        </Button>
      </Box>

      {/* Stats */}
      <Grid container spacing={2} sx={{ mb: 3 }} component="div">
        <Grid item xs={12} sm={4}>
          <StatCard icon={<PeopleIcon />}              label="Total"   value={stats.total}  color="#6366f1" loading={loading} />
        </Grid>
        <Grid item xs={12} sm={4}>
          <StatCard icon={<CheckCircleIcon />}         label="Ativos"  value={stats.active} color="#22c55e" loading={loading} />
        </Grid>
        <Grid item xs={12} sm={4}>
          <StatCard icon={<AdminPanelSettingsIcon />}  label="Admins"  value={stats.admins} color="#f59e0b" loading={loading} />
        </Grid>
      </Grid>

      {/* Table card */}
      <Card>
        {/* Search bar */}
        <Box sx={{ px: 2.5, py: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
          <TextField
            placeholder="Buscar por nome ou email…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                </InputAdornment>
              ),
            }}
            sx={{ width: { xs: '100%', sm: 320 } }}
          />
        </Box>

        {error && (
          <Alert severity="error" sx={{ m: 2 }}>{error}</Alert>
        )}

        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 52 }} />
                <TableCell>Usuário</TableCell>
                <TableCell>Perfil</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Criado em</TableCell>
                <TableCell align="right" sx={{ width: 60 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableSkeleton />
              ) : filtered.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6}>
                    <Box sx={{ py: 6, textAlign: 'center' }}>
                      <PeopleIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                      <Typography color="text.secondary">
                        {search ? 'Nenhum usuário encontrado para esta busca.' : 'Nenhum usuário cadastrado ainda.'}
                      </Typography>
                    </Box>
                  </TableCell>
                </TableRow>
              ) : (
                paginated.map((u) => (
                  <TableRow key={u.id}>
                    {/* Avatar */}
                    <TableCell sx={{ py: 1.5 }}>
                      <Avatar
                        sx={{
                          width: 36, height: 36,
                          bgcolor: avatarColor(u.name),
                          fontSize: '0.8125rem',
                          fontWeight: 700,
                        }}
                      >
                        {u.name.slice(0, 2).toUpperCase()}
                      </Avatar>
                    </TableCell>

                    {/* Name + Email */}
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{u.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{u.email}</Typography>
                    </TableCell>

                    {/* Role */}
                    <TableCell>
                      <Chip
                        label={u.role}
                        size="small"
                        color={u.role === 'ADMIN' ? 'primary' : 'default'}
                        variant={u.role === 'ADMIN' ? 'filled' : 'outlined'}
                      />
                    </TableCell>

                    {/* Status */}
                    <TableCell>
                      <Chip
                        label={u.active ? 'Ativo' : 'Inativo'}
                        size="small"
                        color={u.active ? 'success' : 'error'}
                        variant="outlined"
                      />
                    </TableCell>

                    {/* Created */}
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {formatDate(u.createdAt)}
                      </Typography>
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="right">
                      {u.active && (
                        <Tooltip title="Desativar usuário">
                          <IconButton
                            size="small"
                            onClick={() => setTargetUser(u)}
                            sx={{ color: 'text.secondary', '&:hover': { color: 'error.main' } }}
                          >
                            <PersonRemoveIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {!loading && filtered.length > rowsPerPage && (
          <>
            <Divider />
            <TablePagination
              component="div"
              count={filtered.length}
              page={page}
              rowsPerPage={rowsPerPage}
              rowsPerPageOptions={[rowsPerPage]}
              onPageChange={(_, p) => setPage(p)}
              labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`}
            />
          </>
        )}
      </Card>

      {/* Dialogs */}
      <CreateUserDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreate={handleCreate}
      />
      <ConfirmDeactivateDialog
        user={targetUser}
        onClose={() => setTargetUser(null)}
        onConfirm={handleDeactivate}
      />

      {/* Feedback snackbar */}
      <Snackbar
        open={Boolean(snack)}
        autoHideDuration={4000}
        onClose={() => setSnack(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snack?.ok ? 'success' : 'error'}
          onClose={() => setSnack(null)}
          sx={{ minWidth: 280 }}
        >
          {snack?.msg}
        </Alert>
      </Snackbar>
    </AppLayout>
  );
}
