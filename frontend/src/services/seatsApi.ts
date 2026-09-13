import { apiFetch } from "./apiClient";

export type SeatStatus = "AVAILABLE" | "LOCKED" | "BOOKED";

export interface SeatDto {
  id: string;
  eventId: string;
  section: string;
  row: string;
  number: number;
  price: number;
  status: SeatStatus;
  lockedBy: string | null;
  lockExpiresAt: string | null;
}

export interface SeatDefinition {
  section: string;
  row: string;
  number: number;
  price: number;
}

export function listSeats(eventId: string): Promise<SeatDto[]> {
  return apiFetch<SeatDto[]>(`/api/events/${eventId}/seats`);
}

export function createSeats(eventId: string, seats: SeatDefinition[]): Promise<SeatDto[]> {
  return apiFetch<SeatDto[]>(`/api/events/${eventId}/seats`, {
    method: "POST",
    body: JSON.stringify({ seats }),
  });
}

export function lockSeat(seatId: string): Promise<SeatDto> {
  return apiFetch<SeatDto>(`/api/seats/${seatId}/lock`, { method: "POST" });
}

export function releaseSeat(seatId: string): Promise<void> {
  return apiFetch<void>(`/api/seats/${seatId}/release`, { method: "POST" });
}
