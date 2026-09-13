import { apiFetch } from "./apiClient";

export type EventStatus = "DRAFT" | "PUBLISHED" | "CANCELLED";

export interface EventDto {
  id: string;
  organizerId: string;
  title: string;
  description: string | null;
  venueName: string;
  eventDate: string;
  status: EventStatus;
}

export interface EventStatsDto {
  eventId: string;
  totalSeats: number;
  availableSeats: number;
  lockedSeats: number;
  bookedSeats: number;
  revenue: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

export function listEvents(): Promise<Page<EventDto>> {
  return apiFetch<Page<EventDto>>("/api/events");
}

export function listMyEvents(): Promise<Page<EventDto>> {
  return apiFetch<Page<EventDto>>("/api/events/mine");
}

export function getEvent(id: string): Promise<EventDto> {
  return apiFetch<EventDto>(`/api/events/${id}`);
}

export function getEventStats(id: string): Promise<EventStatsDto> {
  return apiFetch<EventStatsDto>(`/api/events/${id}/stats`);
}

export function createEvent(input: {
  title: string;
  description: string;
  venueName: string;
  eventDate: string;
}): Promise<EventDto> {
  return apiFetch<EventDto>("/api/events", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function publishEvent(id: string): Promise<EventDto> {
  return apiFetch<EventDto>(`/api/events/${id}/publish`, { method: "POST" });
}
