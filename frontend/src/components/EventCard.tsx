import Link from "next/link";
import { EventDto } from "@/services/eventsApi";

export function EventCard({ event }: { event: EventDto }) {
  const date = new Date(event.eventDate);
  return (
    <Link
      href={`/events/${event.id}`}
      className="block rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md"
    >
      <h3 className="text-lg font-semibold text-slate-900">{event.title}</h3>
      <p className="mt-1 text-sm text-slate-600">{event.venueName}</p>
      <p className="mt-1 text-sm text-slate-500">
        {date.toLocaleDateString(undefined, { dateStyle: "medium" })}
      </p>
    </Link>
  );
}
