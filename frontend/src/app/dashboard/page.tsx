"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { EventDto, EventStatsDto, getEventStats, listMyEvents, publishEvent } from "@/services/eventsApi";
import { useAuth } from "@/hooks/useAuth";
import { ApiError } from "@/services/apiClient";

interface EventWithStats {
  event: EventDto;
  stats: EventStatsDto | null;
}

const STATUS_STYLES: Record<EventDto["status"], string> = {
  DRAFT: "bg-slate-100 text-slate-600",
  PUBLISHED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export default function OrganizerDashboardPage() {
  const { hydrated, isAuthenticated, user } = useAuth();
  const [rows, setRows] = useState<EventWithStats[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [publishingId, setPublishingId] = useState<string | null>(null);

  const canAccess = user?.role === "ORGANIZER" || user?.role === "ADMIN";

  useEffect(() => {
    if (!hydrated || !canAccess) return;
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, canAccess]);

  async function load() {
    try {
      const page = await listMyEvents();
      const withStats = await Promise.all(
        page.content.map(async (event) => {
          try {
            const stats = await getEventStats(event.id);
            return { event, stats };
          } catch {
            return { event, stats: null };
          }
        })
      );
      setRows(withStats);
    } catch {
      setError("Could not load your events");
    }
  }

  async function handlePublish(eventId: string) {
    setPublishingId(eventId);
    try {
      await publishEvent(eventId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not publish event");
    } finally {
      setPublishingId(null);
    }
  }

  if (hydrated && !isAuthenticated) {
    return <p className="text-slate-600">Please log in to view your organizer dashboard.</p>;
  }
  if (hydrated && !canAccess) {
    return <p className="text-slate-600">Only organizers can view this dashboard.</p>;
  }

  const totalRevenue = rows?.reduce((sum, r) => sum + Number(r.stats?.revenue ?? 0), 0) ?? 0;
  const totalBooked = rows?.reduce((sum, r) => sum + Number(r.stats?.bookedSeats ?? 0), 0) ?? 0;

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900">Organizer Dashboard</h1>
        <Link href="/events/new" className="rounded bg-slate-900 px-4 py-2 text-sm text-white hover:bg-slate-700">
          + New event
        </Link>
      </div>

      {rows && rows.length > 0 && (
        <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Events" value={String(rows.length)} />
          <StatCard label="Seats booked" value={String(totalBooked)} />
          <StatCard label="Total revenue" value={`$${totalRevenue.toFixed(2)}`} />
          <StatCard label="Published" value={String(rows.filter((r) => r.event.status === "PUBLISHED").length)} />
        </div>
      )}

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
      {rows === null && !error && <p className="text-slate-500">Loading your events...</p>}
      {rows && rows.length === 0 && (
        <p className="text-slate-500">
          You haven&apos;t created any events yet. <Link href="/events/new" className="underline">Create one</Link>.
        </p>
      )}

      <div className="space-y-3">
        {rows?.map(({ event, stats }) => (
          <div key={event.id} className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <Link href={`/events/${event.id}`} className="font-semibold text-slate-900 hover:underline">
                    {event.title}
                  </Link>
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[event.status]}`}>
                    {event.status}
                  </span>
                </div>
                <p className="text-sm text-slate-500">
                  {event.venueName} · {new Date(event.eventDate).toLocaleDateString(undefined, { dateStyle: "medium" })}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {event.status === "DRAFT" && (
                  <button
                    onClick={() => handlePublish(event.id)}
                    disabled={publishingId === event.id}
                    className="rounded bg-emerald-600 px-3 py-1.5 text-sm text-white hover:bg-emerald-500 disabled:opacity-50"
                  >
                    {publishingId === event.id ? "Publishing..." : "Publish"}
                  </button>
                )}
                <Link
                  href={`/events/${event.id}/seats`}
                  className="rounded border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
                >
                  Seat map
                </Link>
              </div>
            </div>

            {stats && (
              <div className="mt-3 grid grid-cols-2 gap-3 border-t border-slate-100 pt-3 sm:grid-cols-4">
                <MiniStat label="Total seats" value={stats.totalSeats} />
                <MiniStat label="Available" value={stats.availableSeats} />
                <MiniStat label="Booked" value={stats.bookedSeats} />
                <MiniStat label="Revenue" value={`$${Number(stats.revenue).toFixed(2)}`} />
              </div>
            )}
          </div>
        ))}
      </div>
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

function MiniStat({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <p className="text-xs text-slate-400">{label}</p>
      <p className="text-sm font-medium text-slate-800">{value}</p>
    </div>
  );
}
