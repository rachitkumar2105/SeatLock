"use client";

import { useEffect, useState } from "react";
import { AdminUserDto, PlatformMetricsDto, cancelEvent, getMetrics, listAllEvents, listUsers, updateUserRole } from "@/services/adminApi";
import { EventDto } from "@/services/eventsApi";
import { Role } from "@/store/authStore";
import { useAuth } from "@/hooks/useAuth";
import { ApiError } from "@/services/apiClient";

const ROLES: Role[] = ["USER", "ORGANIZER", "ADMIN"];

const STATUS_STYLES: Record<EventDto["status"], string> = {
  DRAFT: "bg-slate-100 text-slate-600",
  PUBLISHED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export default function AdminDashboardPage() {
  const { hydrated, isAuthenticated, user } = useAuth();
  const [metrics, setMetrics] = useState<PlatformMetricsDto | null>(null);
  const [users, setUsers] = useState<AdminUserDto[] | null>(null);
  const [events, setEvents] = useState<EventDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const isAdmin = user?.role === "ADMIN";

  useEffect(() => {
    if (!hydrated || !isAdmin) return;
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, isAdmin]);

  async function load() {
    try {
      const [metricsData, usersPage, eventsPage] = await Promise.all([getMetrics(), listUsers(), listAllEvents()]);
      setMetrics(metricsData);
      setUsers(usersPage.content);
      setEvents(eventsPage.content);
    } catch {
      setError("Could not load admin data");
    }
  }

  async function handleRoleChange(userId: string, role: Role) {
    setBusyId(userId);
    try {
      const updated = await updateUserRole(userId, role);
      setUsers((prev) => (prev ? prev.map((u) => (u.id === updated.id ? updated : u)) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update role");
    } finally {
      setBusyId(null);
    }
  }

  async function handleCancelEvent(eventId: string) {
    setBusyId(eventId);
    try {
      const updated = await cancelEvent(eventId);
      setEvents((prev) => (prev ? prev.map((e) => (e.id === updated.id ? updated : e)) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel event");
    } finally {
      setBusyId(null);
    }
  }

  if (hydrated && !isAuthenticated) {
    return <p className="text-slate-600">Please log in.</p>;
  }
  if (hydrated && !isAdmin) {
    return <p className="text-slate-600">Only admins can view this page.</p>;
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Admin Dashboard</h1>
      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {metrics && (
        <div className="mb-8 grid grid-cols-2 gap-4 sm:grid-cols-5">
          <StatCard label="Users" value={String(metrics.totalUsers)} />
          <StatCard label="Events" value={String(metrics.totalEvents)} />
          <StatCard label="Published" value={String(metrics.publishedEvents)} />
          <StatCard label="Bookings" value={String(metrics.totalBookings)} />
          <StatCard label="Revenue" value={`$${Number(metrics.totalRevenue).toFixed(2)}`} />
        </div>
      )}

      <section className="mb-10">
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Users</h2>
        {users === null && !error && <p className="text-slate-500">Loading users...</p>}
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full min-w-[500px] text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2">Name</th>
                <th className="px-4 py-2">Email</th>
                <th className="px-4 py-2">Role</th>
              </tr>
            </thead>
            <tbody>
              {users?.map((u) => (
                <tr key={u.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-4 py-2">{u.name}</td>
                  <td className="px-4 py-2 text-slate-500">{u.email}</td>
                  <td className="px-4 py-2">
                    <select
                      value={u.role}
                      disabled={busyId === u.id}
                      onChange={(e) => handleRoleChange(u.id, e.target.value as Role)}
                      className="rounded border border-slate-300 px-2 py-1 text-sm disabled:opacity-50"
                    >
                      {ROLES.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Events</h2>
        {events === null && !error && <p className="text-slate-500">Loading events...</p>}
        <div className="space-y-2">
          {events?.map((event) => (
            <div key={event.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-900">{event.title}</span>
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[event.status]}`}>
                    {event.status}
                  </span>
                </div>
                <p className="text-xs text-slate-500">{event.venueName}</p>
              </div>
              {event.status !== "CANCELLED" && (
                <button
                  onClick={() => handleCancelEvent(event.id)}
                  disabled={busyId === event.id}
                  className="rounded border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
                >
                  {busyId === event.id ? "Cancelling..." : "Cancel event"}
                </button>
              )}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-slate-900">{value}</p>
    </div>
  );
}
