"use client";

import { useEffect } from "react";
import { subscribeToEventSeats, SeatStatusMessage } from "@/services/websocketClient";

export function useSeatWebSocket(eventId: string | undefined, onMessage: (message: SeatStatusMessage) => void) {
  useEffect(() => {
    if (!eventId) return;
    const unsubscribe = subscribeToEventSeats(eventId, onMessage);
    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);
}
