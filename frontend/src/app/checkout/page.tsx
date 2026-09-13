"use client";

import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { EventDto, getEvent } from "@/services/eventsApi";
import { SeatDto, listSeats } from "@/services/seatsApi";
import { createBooking } from "@/services/bookingsApi";
import { ApiError } from "@/services/apiClient";
import { useAuth } from "@/hooks/useAuth";

function CheckoutContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { isAuthenticated, hydrated } = useAuth();

  const eventId = searchParams.get("eventId") ?? "";
  const seatIds = useMemo(() => (searchParams.get("seatIds") ?? "").split(",").filter(Boolean), [searchParams]);

  const [event, setEvent] = useState<EventDto | null>(null);
  const [seats, setSeats] = useState<SeatDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmedBookingId, setConfirmedBookingId] = useState<string | null>(null);

  // Generated once per checkout attempt and reused on retry, so a double-click or a retried
  // request after a network hiccup can never create two bookings for the same intent.
  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

  useEffect(() => {
    if (!eventId || seatIds.length === 0) return;
    Promise.all([getEvent(eventId), listSeats(eventId)])
      .then(([eventData, seatData]) => {
        setEvent(eventData);
        setSeats(seatData.filter((s) => seatIds.includes(s.id)));
      })
      .catch(() => setError("Could not load checkout details"));
  }, [eventId, seatIds]);

  const total = seats.reduce((sum, s) => sum + Number(s.price), 0);

  async function handleConfirm() {
    setSubmitting(true);
    setError(null);
    try {
      const booking = await createBooking(eventId, seatIds, idempotencyKeyRef.current);
      setConfirmedBookingId(booking.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Payment failed, please try again");
    } finally {
      setSubmitting(false);
    }
  }

  if (hydrated && !isAuthenticated) {
    return <p className="text-slate-600">Please log in to check out.</p>;
  }

  if (!eventId || seatIds.length === 0) {
    return <p className="text-slate-600">No seats selected.</p>;
  }

  if (confirmedBookingId) {
    return (
      <div className="mx-auto max-w-md text-center">
        <h1 className="text-2xl font-semibold text-emerald-700">Booking confirmed!</h1>
        <p className="mt-2 text-slate-600">Your seats are locked in. See you at the show.</p>
        <button
          onClick={() => router.push("/my-bookings")}
          className="mt-6 rounded bg-slate-900 px-5 py-2 text-white hover:bg-slate-700"
        >
          View my bookings
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Checkout</h1>
      {event && <p className="mb-4 text-slate-600">{event.title}</p>}

      <ul className="mb-4 divide-y divide-slate-200 rounded border border-slate-200 bg-white">
        {seats.map((seat) => (
          <li key={seat.id} className="flex justify-between px-4 py-2 text-sm">
            <span>
              {seat.section}
              {seat.row}
              {seat.number}
            </span>
            <span>${Number(seat.price).toFixed(2)}</span>
          </li>
        ))}
      </ul>

      <div className="mb-6 flex justify-between text-lg font-semibold text-slate-900">
        <span>Total</span>
        <span>${total.toFixed(2)}</span>
      </div>

      <div className="mb-4 rounded border border-slate-200 bg-white p-4">
        <p className="mb-2 text-sm font-medium text-slate-700">Mock payment</p>
        <input
          disabled
          placeholder="4242 4242 4242 4242"
          className="w-full rounded border border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-400"
        />
        <p className="mt-1 text-xs text-slate-400">No real payment provider is wired up — this is a portfolio project.</p>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      <button
        onClick={handleConfirm}
        disabled={submitting || seats.length === 0}
        className="w-full rounded bg-slate-900 px-4 py-2.5 text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {submitting ? "Confirming..." : `Confirm booking · $${total.toFixed(2)}`}
      </button>
    </div>
  );
}

export default function CheckoutPage() {
  return (
    <Suspense fallback={<p className="text-slate-500">Loading checkout...</p>}>
      <CheckoutContent />
    </Suspense>
  );
}
