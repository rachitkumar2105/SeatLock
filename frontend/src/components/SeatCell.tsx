"use client";

import { useEffect, useState } from "react";
import { SeatDto } from "@/services/seatsApi";

function statusClasses(seat: SeatDto, isMine: boolean, isPending: boolean) {
  if (isPending) return "bg-slate-200 text-slate-400 cursor-wait";
  if (seat.status === "AVAILABLE") return "bg-emerald-100 text-emerald-800 hover:bg-emerald-200 cursor-pointer";
  if (seat.status === "LOCKED" && isMine) return "bg-amber-400 text-amber-950 cursor-pointer ring-2 ring-amber-600";
  if (seat.status === "LOCKED") return "bg-amber-100 text-amber-500 cursor-not-allowed";
  return "bg-slate-300 text-slate-500 cursor-not-allowed";
}

// Color alone shouldn't be the only signal (colorblind users, low-contrast displays), so each
// non-available status also gets a small glyph baked into the corner of the cell.
function statusGlyph(seat: SeatDto, isMine: boolean): string | null {
  if (seat.status === "BOOKED") return "×"; // ×
  if (seat.status === "LOCKED" && isMine) return "✓"; // check
  if (seat.status === "LOCKED") return "⏱"; // held-by-someone-else clock
  return null;
}

function statusLabel(seat: SeatDto, isMine: boolean): string {
  if (seat.status === "BOOKED") return "booked";
  if (seat.status === "LOCKED" && isMine) return "held by you";
  if (seat.status === "LOCKED") return "held by another user";
  return "available";
}

function secondsRemaining(lockExpiresAt: string | null): number | null {
  if (!lockExpiresAt) return null;
  const ms = new Date(lockExpiresAt).getTime() - Date.now();
  return ms > 0 ? Math.ceil(ms / 1000) : 0;
}

export function SeatCell({
  seat,
  isMine,
  isPending,
  onClick,
}: {
  seat: SeatDto;
  isMine: boolean;
  isPending: boolean;
  onClick: () => void;
}) {
  const showCountdown = isMine && seat.status === "LOCKED";

  // `remaining` is fully derived from props, so it's computed directly in render rather than
  // synced into state via an effect. The effect's only job is forcing a re-render once a second
  // so that derived value ticks down — its setState call happens inside the interval's own
  // callback, not synchronously in the effect body.
  const [, forceTick] = useState(0);
  useEffect(() => {
    if (!showCountdown) return;
    const interval = setInterval(() => forceTick((t) => t + 1), 1000);
    return () => clearInterval(interval);
  }, [showCountdown]);

  const remaining = showCountdown ? secondsRemaining(seat.lockExpiresAt) : null;

  const disabled = isPending || (seat.status !== "AVAILABLE" && !(seat.status === "LOCKED" && isMine));
  const glyph = statusGlyph(seat, isMine);
  const label = `Seat ${seat.section}${seat.row}${seat.number}, $${seat.price}, ${statusLabel(seat, isMine)}`;

  return (
    <button
      type="button"
      title={`${seat.section}${seat.row}${seat.number} · $${seat.price}`}
      aria-label={label}
      aria-disabled={disabled}
      disabled={disabled}
      onClick={onClick}
      // h-11 w-11 (44x44px) meets the minimum recommended touch target size; hover states alone
      // don't help on touch devices, so the tap target itself has to be big enough.
      className={`relative flex h-11 w-11 items-center justify-center rounded text-xs font-medium transition ${statusClasses(
        seat,
        isMine,
        isPending
      )}`}
    >
      {seat.number}
      {glyph && (
        <span aria-hidden="true" className="absolute -right-1 -top-1 text-[10px] leading-none">
          {glyph}
        </span>
      )}
      {remaining !== null && (
        <span className="absolute -bottom-4 left-1/2 -translate-x-1/2 whitespace-nowrap text-[10px] text-amber-700">
          {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, "0")}
        </span>
      )}
    </button>
  );
}
