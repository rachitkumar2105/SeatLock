"use client";

import { SeatDto } from "@/services/seatsApi";
import { SeatCell } from "./SeatCell";

export function SeatMap({
  seats,
  currentUserId,
  pendingSeatIds,
  onSeatClick,
}: {
  seats: SeatDto[];
  currentUserId: string | undefined;
  pendingSeatIds: Set<string>;
  onSeatClick: (seat: SeatDto) => void;
}) {
  const sections = new Map<string, Map<string, SeatDto[]>>();
  for (const seat of seats) {
    if (!sections.has(seat.section)) sections.set(seat.section, new Map());
    const rows = sections.get(seat.section)!;
    if (!rows.has(seat.row)) rows.set(seat.row, []);
    rows.get(seat.row)!.push(seat);
  }

  return (
    <div className="space-y-8">
      {Array.from(sections.entries()).map(([section, rows]) => (
        <div key={section} role="group" aria-label={`Section ${section}`}>
          <h3 className="mb-2 text-sm font-semibold text-slate-700">Section {section}</h3>
          <div className="space-y-2">
            {Array.from(rows.entries()).map(([row, rowSeats]) => (
              <div key={row} role="group" aria-label={`Row ${row}`} className="flex items-center gap-2">
                <span aria-hidden="true" className="w-5 text-xs font-medium text-slate-400">{row}</span>
                <div className="flex flex-wrap gap-2 pb-3">
                  {rowSeats
                    .sort((a, b) => a.number - b.number)
                    .map((seat) => (
                      <SeatCell
                        key={seat.id}
                        seat={seat}
                        isMine={seat.lockedBy === currentUserId}
                        isPending={pendingSeatIds.has(seat.id)}
                        onClick={() => onSeatClick(seat)}
                      />
                    ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
