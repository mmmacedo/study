'use client';

import { useState, useEffect, useCallback } from 'react';
import type { User, CreateUserPayload, ApiError } from '@/types/user';
import { getAccessToken } from '@/lib/auth';

const BASE = '/api/users';

function authHeaders(): HeadersInit {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${getAccessToken()}`,
  };
}

export function useUsers() {
  const [users,   setUsers]   = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(BASE, { headers: authHeaders() });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setUsers(await res.json());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar usuários');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  async function createUser(payload: CreateUserPayload): Promise<ApiError | undefined> {
    const res = await fetch(BASE, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(payload),
    });
    if (res.status === 201) {
      await fetchUsers();
      return undefined;
    }
    return (await res.json()) as ApiError;
  }

  async function deactivateUser(id: string): Promise<ApiError | undefined> {
    const res = await fetch(`${BASE}/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    });
    if (res.status === 204) {
      setUsers((prev) => prev.filter((u) => u.id !== id));
      return undefined;
    }
    return (await res.json()) as ApiError;
  }

  return { users, loading, error, refetch: fetchUsers, createUser, deactivateUser };
}
