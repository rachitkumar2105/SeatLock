"use client";

import { useEffect, useState } from "react";
import { EventDto, listEvents } from "@/services/eventsApi";
import { EventCard } from "@/components/EventCard";

export default function BrowseEventsPage() {
  const [events, setEvents] = useState<EventDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listEvents()
      .then((page) => setEvents(page.content))
      .catch(() => setError("Could not load events"));
  }, []);

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Upcoming events</h1>
      {error && <p className="text-red-600">{error}</p>}
      {events === null && !error && <p className="text-slate-500">Loading events...</p>}
      {events && events.length === 0 && <p className="text-slate-500">No published events yet.</p>}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {events?.map((event) => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>
    </div>
  );
}
