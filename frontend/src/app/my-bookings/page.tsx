"use client";

import { useEffect, useState } from "react";
import { BookingDto, myBookings, cancelBooking } from "@/services/bookingsApi";
import { useAuth } from "@/hooks/useAuth";
import { ApiError } from "@/services/apiClient";

export default function MyBookingsPage() {
  const { hydrated, isAuthenticated } = useAuth();
  const [bookings, setBookings] = useState<BookingDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!hydrated || !isAuthenticated) return;
    myBookings()
      .then(setBookings)
      .catch(() => setError("Could not load your bookings"));
  }, [hydrated, isAuthenticated]);

  async function handleCancel(id: string) {
    try {
      const updated = await cancelBooking(id);
      setBookings((prev) => (prev ? prev.map((b) => (b.id === updated.id ? updated : b)) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel booking");
    }
  }

  if (hydrated && !isAuthenticated) {
    return <p className="text-slate-600">Please log in to view your bookings.</p>;
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">My Bookings</h1>
      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
      {bookings === null && !error && <p className="text-slate-500">Loading...</p>}
      {bookings && bookings.length === 0 && <p className="text-slate-500">No bookings yet.</p>}

      <div className="space-y-3">
        {bookings?.map((booking) => (
          <div key={booking.id} className="flex items-center justify-between rounded border border-slate-200 bg-white p-4">
            <div>
              <p className="font-medium text-slate-900">
                {booking.seatIds.length} seat{booking.seatIds.length === 1 ? "" : "s"} · ${Number(booking.totalAmount).toFixed(2)}
              </p>
              <p className="text-xs text-slate-500">
                Booked {new Date(booking.createdAt).toLocaleString()} ·{" "}
                <span className={booking.status === "CONFIRMED" ? "text-emerald-600" : "text-slate-400"}>
                  {booking.status}
                </span>
              </p>
            </div>
            {booking.status === "CONFIRMED" && (
              <button
                onClick={() => handleCancel(booking.id)}
                className="rounded border border-slate-300 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
              >
                Cancel
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
