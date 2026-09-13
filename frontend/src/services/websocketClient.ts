import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? "http://localhost:8080/ws";

export interface SeatStatusMessage {
  seatId: string;
  status: "AVAILABLE" | "LOCKED" | "BOOKED";
  lockedBy: string | null;
  lockExpiresAt: string | null;
}

export function subscribeToEventSeats(
  eventId: string,
  onMessage: (message: SeatStatusMessage) => void
): () => void {
  const client = new Client({
    webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
    reconnectDelay: 3000,
  });

  client.onConnect = () => {
    client.subscribe(`/topic/events/${eventId}/seats`, (frame: IMessage) => {
      onMessage(JSON.parse(frame.body) as SeatStatusMessage);
    });
  };

  client.activate();

  return () => {
    client.deactivate();
  };
}
