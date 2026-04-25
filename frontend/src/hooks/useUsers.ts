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

// res.json() throws "Unexpected end of JSON input" when the body is empty
// (e.g. 401/403 from the gateway with no payload). Parse safely.
async function safeJson(res: Response): Promise<ApiError> {
  const text = await res.text();
  if (!text.trim()) {
    return { type: '', title: 'Erro', status: res.status, detail: `HTTP ${res.status}` };
  }
  try {
    return JSON.parse(text) as ApiError;
  } catch {
    return { type: '', title: 'Erro', status: res.status, detail: text };
  }
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
    return safeJson(res);
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
    return safeJson(res);
  }

  return { users, loading, error, refetch: fetchUsers, createUser, deactivateUser };
}
