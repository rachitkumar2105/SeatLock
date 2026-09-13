"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";

export function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  return (
    <nav className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <Link href="/" className="text-lg font-semibold text-slate-900">
          SeatLock
        </Link>
        <div className="flex items-center gap-4 text-sm">
          <Link href="/" className="text-slate-600 hover:text-slate-900">
            Events
          </Link>
          {isAuthenticated && (
            <Link href="/my-bookings" className="text-slate-600 hover:text-slate-900">
              My Bookings
            </Link>
          )}
          {isAuthenticated && (user?.role === "ORGANIZER" || user?.role === "ADMIN") && (
            <Link href="/events/new" className="text-slate-600 hover:text-slate-900">
              Create Event
            </Link>
          )}
          {isAuthenticated ? (
            <>
              <span className="text-slate-500">{user?.name}</span>
              <button onClick={handleLogout} className="rounded bg-slate-900 px-3 py-1.5 text-white hover:bg-slate-700">
                Log out
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="text-slate-600 hover:text-slate-900">
                Log in
              </Link>
              <Link href="/register" className="rounded bg-slate-900 px-3 py-1.5 text-white hover:bg-slate-700">
                Sign up
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
