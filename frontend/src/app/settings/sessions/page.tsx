"use client";

import { useCallback, useEffect, useState } from "react";
import { SessionDto, listSessions, revokeSession } from "@/services/authApi";
import { useAuth } from "@/hooks/useAuth";
import { ApiError } from "@/services/apiClient";

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

// Best-effort, not a real user-agent parser: enough to distinguish "Chrome on Windows" from
// "Safari on iPhone" for a device list, not to fingerprint precisely.
function describeDevice(userAgent: string | null): string {
  if (!userAgent) return "Unknown device";
  const browser = /edg\//i.test(userAgent)
    ? "Edge"
    : /chrome\//i.test(userAgent)
      ? "Chrome"
      : /firefox\//i.test(userAgent)
        ? "Firefox"
        : /safari\//i.test(userAgent)
          ? "Safari"
          : "Browser";
  const os = /windows/i.test(userAgent)
    ? "Windows"
    : /mac os/i.test(userAgent)
      ? "macOS"
      : /android/i.test(userAgent)
        ? "Android"
        : /iphone|ipad/i.test(userAgent)
          ? "iOS"
          : /linux/i.test(userAgent)
            ? "Linux"
            : "an unknown OS";
  return `${browser} on ${os}`;
}

export default function SessionsPage() {
  const { hydrated, isAuthenticated } = useAuth();
  const [sessions, setSessions] = useState<SessionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await listSessions();
      setSessions(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load your sessions");
    }
  }, []);

  useEffect(() => {
    if (!hydrated || !isAuthenticated) return;
    // Standard fetch-on-mount: load()'s setState calls happen after its first await, not
    // synchronously in this effect body, despite what the rule's static analysis assumes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [hydrated, isAuthenticated, load]);

  async function handleRevoke(id: string) {
    setRevokingId(id);
    try {
      await revokeSession(id);
      setSessions((prev) => (prev ? prev.filter((s) => s.id !== id) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not revoke that session");
    } finally {
      setRevokingId(null);
    }
  }

  if (hydrated && !isAuthenticated) {
    return <p className="text-slate-600">Please log in to view your active sessions.</p>;
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold text-slate-900">Active sessions</h1>
      <p className="mt-1 text-sm text-slate-500">
        Every device currently signed in to your account. Revoke any you don&apos;t recognize.
      </p>

      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}
      {sessions === null && !error && <p className="mt-4 text-slate-500">Loading...</p>}

      <div className="mt-6 space-y-3">
        {sessions?.map((session) => (
          <div
            key={session.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-4"
          >
            <div>
              <p className="font-medium text-slate-900">{describeDevice(session.userAgent)}</p>
              <p className="text-sm text-slate-500">
                {session.ipAddress ?? "Unknown IP"} &middot; last active {formatWhen(session.lastUsedAt)}
              </p>
              <p className="text-xs text-slate-400">Signed in {formatWhen(session.createdAt)}</p>
            </div>
            <button
              onClick={() => handleRevoke(session.id)}
              disabled={revokingId === session.id}
              className="rounded border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
            >
              {revokingId === session.id ? "Revoking..." : "Revoke"}
            </button>
          </div>
        ))}
        {sessions && sessions.length === 0 && <p className="text-slate-500">No active sessions.</p>}
      </div>
    </div>
  );
}
