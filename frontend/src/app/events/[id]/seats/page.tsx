"use client";

import { use, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { EventDto, getEvent } from "@/services/eventsApi";
import { SeatDto, listSeats, lockSeat, releaseSeat } from "@/services/seatsApi";
import { useSeatWebSocket } from "@/hooks/useWebSocket";
import { useAuth } from "@/hooks/useAuth";
import { SeatMap } from "@/components/SeatMap";
import { Toasts, Toast } from "@/components/Toasts";
import { Skeleton, SeatMapSkeleton } from "@/components/Skeleton";
import { ApiError } from "@/services/apiClient";

export default function SeatSelectionPage({ params }: PageProps<"/events/[id]/seats">) {
  const { id } = use(params);
  const router = useRouter();
  const { user, isAuthenticated } = useAuth();

  const [event, setEvent] = useState<EventDto | null>(null);
  const [seats, setSeats] = useState<SeatDto[]>([]);
  const [pendingSeatIds, setPendingSeatIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastIdRef = useRef(0);

  // useSeatWebSocket subscribes once per eventId and never recreates its callback, so it would
  // otherwise close over a stale `user` from the first render (often still undefined pre-hydration).
  // Read the current id through a ref instead of the closed-over variable.
  const userIdRef = useRef<string | undefined>(user?.id);
  useEffect(() => {
    userIdRef.current = user?.id;
  }, [user?.id]);

  useEffect(() => {
    Promise.all([getEvent(id), listSeats(id)])
      .then(([eventData, seatData]) => {
        setEvent(eventData);
        setSeats(seatData);
      })
      .catch(() => setError("Could not load the seat map"));
  }, [id]);

  function pushToast(message: string) {
    const toastId = ++toastIdRef.current;
    setToasts((prev) => [...prev, { id: toastId, message }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== toastId)), 4000);
  }

  useSeatWebSocket(id, (message) => {
    setSeats((prev) => {
      const seat = prev.find((s) => s.id === message.seatId);
      if (seat && message.lockedBy !== userIdRef.current) {
        if (message.status === "BOOKED") pushToast(`Seat ${seat.section}${seat.row}${seat.number} was just booked`);
        else if (message.status === "LOCKED") pushToast(`Seat ${seat.section}${seat.row}${seat.number} was just selected by someone else`);
      }
      return prev.map((s) =>
        s.id === message.seatId
          ? { ...s, status: message.status, lockedBy: message.lockedBy, lockExpiresAt: message.lockExpiresAt }
          : s
      );
    });
  });

  async function handleSeatClick(seat: SeatDto) {
    if (!isAuthenticated) {
      router.push("/login");
      return;
    }
    setPendingSeatIds((prev) => new Set(prev).add(seat.id));
    setError(null);
    try {
      if (seat.status === "AVAILABLE") {
        const locked = await lockSeat(seat.id);
        setSeats((prev) => prev.map((s) => (s.id === locked.id ? locked : s)));
      } else if (seat.status === "LOCKED" && seat.lockedBy === user?.id) {
        await releaseSeat(seat.id);
        setSeats((prev) =>
          prev.map((s) => (s.id === seat.id ? { ...s, status: "AVAILABLE", lockedBy: null, lockExpiresAt: null } : s))
        );
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "That seat is no longer available");
    } finally {
      setPendingSeatIds((prev) => {
        const next = new Set(prev);
        next.delete(seat.id);
        return next;
      });
    }
  }

  const mySeats = seats.filter((s) => s.status === "LOCKED" && s.lockedBy === user?.id);
  const total = mySeats.reduce((sum, s) => sum + Number(s.price), 0);

  if (error && seats.length === 0) return <p className="text-red-600">{error}</p>;
  if (!event) {
    return (
      <div>
        <Skeleton className="h-8 w-64" />
        <Skeleton className="mt-2 h-5 w-40" />
        <div className="mt-6">
          <SeatMapSkeleton />
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold text-slate-900">{event.title}</h1>
      <p className="mb-6 text-slate-600">{event.venueName}</p>

      {/* Each legend entry pairs its color with a glyph and text label — color is never the only
          signal, so this reads the same for colorblind users as the seat map itself does. */}
      <div className="mb-4 flex flex-wrap gap-4 text-xs text-slate-600">
        <span className="flex items-center gap-1">
          <span aria-hidden="true" className="h-3 w-3 rounded bg-emerald-100" /> Available
        </span>
        <span className="flex items-center gap-1">
          <span aria-hidden="true" className="h-3 w-3 rounded bg-amber-400 text-center text-[8px] leading-3">✓</span> Selected by you
        </span>
        <span className="flex items-center gap-1">
          <span aria-hidden="true" className="h-3 w-3 rounded bg-amber-100 text-center text-[8px] leading-3">⏱</span> Held by someone else
        </span>
        <span className="flex items-center gap-1">
          <span aria-hidden="true" className="h-3 w-3 rounded bg-slate-300 text-center text-[8px] leading-3">×</span> Booked
        </span>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      <SeatMap seats={seats} currentUserId={user?.id} pendingSeatIds={pendingSeatIds} onSeatClick={handleSeatClick} />

      <div className="sticky bottom-0 mt-8 flex items-center justify-between rounded-lg border border-slate-200 bg-white p-4 shadow">
        <div>
          <p className="text-sm text-slate-600">
            {mySeats.length} seat{mySeats.length === 1 ? "" : "s"} selected
          </p>
          <p className="text-lg font-semibold text-slate-900">${total.toFixed(2)}</p>
        </div>
        <button
          disabled={mySeats.length === 0}
          onClick={() => router.push(`/checkout?eventId=${event.id}&seatIds=${mySeats.map((s) => s.id).join(",")}`)}
          className="rounded bg-slate-900 px-5 py-2 text-white hover:bg-slate-700 disabled:opacity-40"
        >
          Proceed to checkout
        </button>
      </div>

      <Toasts toasts={toasts} />
    </div>
  );
}
