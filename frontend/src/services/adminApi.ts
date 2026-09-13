import { apiFetch } from "./apiClient";
import { EventDto, Page } from "./eventsApi";
import { Role } from "@/store/authStore";

export interface AdminUserDto {
  id: string;
  name: string;
  email: string;
  role: Role;
}

export interface PlatformMetricsDto {
  totalUsers: number;
  totalEvents: number;
  publishedEvents: number;
  totalBookings: number;
  totalRevenue: number;
}

export function listUsers(): Promise<Page<AdminUserDto>> {
  return apiFetch<Page<AdminUserDto>>("/api/admin/users");
}

export function updateUserRole(userId: string, role: Role): Promise<AdminUserDto> {
  return apiFetch<AdminUserDto>(`/api/admin/users/${userId}/role`, {
    method: "PATCH",
    body: JSON.stringify({ role }),
  });
}

export function listAllEvents(): Promise<Page<EventDto>> {
  return apiFetch<Page<EventDto>>("/api/admin/events");
}

export function cancelEvent(eventId: string): Promise<EventDto> {
  return apiFetch<EventDto>(`/api/admin/events/${eventId}/cancel`, { method: "POST" });
}

export function getMetrics(): Promise<PlatformMetricsDto> {
  return apiFetch<PlatformMetricsDto>("/api/admin/metrics");
}
