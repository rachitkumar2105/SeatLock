import { apiFetch } from "./apiClient";

export type BookingStatus = "CONFIRMED" | "CANCELLED";

export interface BookingDto {
  id: string;
  eventId: string;
  status: BookingStatus;
  totalAmount: number;
  createdAt: string;
  seatIds: string[];
}

export function createBooking(eventId: string, seatIds: string[], idempotencyKey: string): Promise<BookingDto> {
  return apiFetch<BookingDto>("/api/bookings", {
    method: "POST",
    body: JSON.stringify({ eventId, seatIds }),
    idempotencyKey,
  });
}

export function myBookings(): Promise<BookingDto[]> {
  return apiFetch<BookingDto[]>("/api/bookings/me");
}

export function cancelBooking(id: string): Promise<BookingDto> {
  return apiFetch<BookingDto>(`/api/bookings/${id}/cancel`, { method: "POST" });
}
