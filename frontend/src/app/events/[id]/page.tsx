"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { EventDto, getEvent, publishEvent } from "@/services/eventsApi";
import { SeatDefinition, createSeats, listSeats } from "@/services/seatsApi";
import { ApiError } from "@/services/apiClient";
import { useAuth } from "@/hooks/useAuth";

export default function EventDetailsPage({ params }: PageProps<"/events/[id]">) {
  const { id } = use(params);
  const { user } = useAuth();

  const [event, setEvent] = useState<EventDto | null>(null);
  const [seatCount, setSeatCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [section, setSection] = useState("A");
  const [rows, setRows] = useState(3);
  const [seatsPerRow, setSeatsPerRow] = useState(8);
  const [price, setPrice] = useState(50);
  const [layoutMessage, setLayoutMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const isOwner = user && event && user.id === event.organizerId;

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function load() {
    try {
      const [eventData, seats] = await Promise.all([getEvent(id), listSeats(id)]);
      setEvent(eventData);
      setSeatCount(seats.length);
    } catch {
      setError("Could not load this event");
    }
  }

  async function handleGenerateLayout(e: React.FormEvent) {
    e.preventDefault();
    setLayoutMessage(null);
    setSubmitting(true);
    try {
      const rowLabels = Array.from({ length: rows }, (_, i) => String.fromCharCode(65 + i));
      const definitions: SeatDefinition[] = rowLabels.flatMap((row) =>
        Array.from({ length: seatsPerRow }, (_, i) => ({
          section,
          row,
          number: i + 1,
          price,
        }))
      );
      const created = await createSeats(id, definitions);
      setSeatCount((prev) => (prev ?? 0) + created.length);
      setLayoutMessage(`Added ${created.length} seats.`);
    } catch (err) {
      setLayoutMessage(err instanceof ApiError ? err.message : "Could not create seats");
    } finally {
      setSubmitting(false);
    }
  }

  async function handlePublish() {
    if (!event) return;
    try {
      const updated = await publishEvent(event.id);
      setEvent(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not publish event");
    }
  }

  if (error) return <p className="text-red-600">{error}</p>;
  if (!event) return <p className="text-slate-500">Loading...</p>;

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold text-slate-900">{event.title}</h1>
      <p className="mt-1 text-slate-600">{event.venueName}</p>
      <p className="mt-1 text-slate-500">
        {new Date(event.eventDate).toLocaleString(undefined, { dateStyle: "full", timeStyle: "short" })}
      </p>
      <span className="mt-2 inline-block rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
        {event.status}
      </span>
      {event.description && <p className="mt-4 text-slate-700">{event.description}</p>}

      <div className="mt-6">
        {seatCount !== null && seatCount > 0 ? (
          <Link
            href={`/events/${event.id}/seats`}
            className="inline-block rounded bg-slate-900 px-4 py-2 text-white hover:bg-slate-700"
          >
            Select Seats
          </Link>
        ) : (
          <p className="text-sm text-slate-500">This event has no seats yet.</p>
        )}
      </div>

      {isOwner && (
        <div className="mt-10 border-t border-slate-200 pt-6">
          <h2 className="text-lg font-semibold text-slate-900">Organizer tools</h2>

          {event.status === "DRAFT" && (
            <button
              onClick={handlePublish}
              className="mt-3 rounded bg-emerald-600 px-4 py-2 text-white hover:bg-emerald-500"
            >
              Publish event
            </button>
          )}

          <form onSubmit={handleGenerateLayout} className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div>
              <label className="block text-xs font-medium text-slate-600">Section</label>
              <input
                value={section}
                onChange={(e) => setSection(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600">Rows</label>
              <input
                type="number"
                min={1}
                max={26}
                value={rows}
                onChange={(e) => setRows(Number(e.target.value))}
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600">Seats/row</label>
              <input
                type="number"
                min={1}
                max={50}
                value={seatsPerRow}
                onChange={(e) => setSeatsPerRow(Number(e.target.value))}
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-600">Price</label>
              <input
                type="number"
                min={0}
                step="0.01"
                value={price}
                onChange={(e) => setPrice(Number(e.target.value))}
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
              />
            </div>
            <div className="col-span-2 sm:col-span-4">
              <button
                type="submit"
                disabled={submitting}
                className="rounded bg-slate-900 px-4 py-1.5 text-sm text-white hover:bg-slate-700 disabled:opacity-50"
              >
                {submitting ? "Adding..." : "Generate seat layout"}
              </button>
              {layoutMessage && <span className="ml-3 text-sm text-slate-600">{layoutMessage}</span>}
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
