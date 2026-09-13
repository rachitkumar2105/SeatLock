"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";

export function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);

  async function handleLogout() {
    setMenuOpen(false);
    await logout();
    router.push("/");
  }

  const isOrganizer = isAuthenticated && (user?.role === "ORGANIZER" || user?.role === "ADMIN");
  const isAdmin = isAuthenticated && user?.role === "ADMIN";

  const links = (
    <>
      <Link href="/" onClick={() => setMenuOpen(false)} className="text-slate-600 hover:text-slate-900">
        Events
      </Link>
      {isAuthenticated && (
        <Link href="/my-bookings" onClick={() => setMenuOpen(false)} className="text-slate-600 hover:text-slate-900">
          My Bookings
        </Link>
      )}
      {isOrganizer && (
        <Link href="/dashboard" onClick={() => setMenuOpen(false)} className="text-slate-600 hover:text-slate-900">
          Dashboard
        </Link>
      )}
      {isOrganizer && (
        <Link href="/events/new" onClick={() => setMenuOpen(false)} className="text-slate-600 hover:text-slate-900">
          Create Event
        </Link>
      )}
      {isAdmin && (
        <Link href="/admin" onClick={() => setMenuOpen(false)} className="text-slate-600 hover:text-slate-900">
          Admin
        </Link>
      )}
    </>
  );

  return (
    <nav className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
        <Link href="/" className="text-lg font-semibold text-slate-900">
          SeatLock
        </Link>

        {/* Desktop nav */}
        <div className="hidden items-center gap-4 text-sm md:flex">
          {links}
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

        {/* Mobile menu toggle */}
        <button
          className="rounded p-2 text-slate-600 hover:bg-slate-100 md:hidden"
          aria-label="Toggle menu"
          onClick={() => setMenuOpen((v) => !v)}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            {menuOpen ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M3 6h18M3 12h18M3 18h18" />}
          </svg>
        </button>
      </div>

      {/* Mobile nav panel */}
      {menuOpen && (
        <div className="flex flex-col gap-3 border-t border-slate-200 px-4 py-3 text-sm md:hidden">
          {links}
          {isAuthenticated ? (
            <>
              <span className="text-slate-500">{user?.name}</span>
              <button
                onClick={handleLogout}
                className="rounded bg-slate-900 px-3 py-1.5 text-center text-white hover:bg-slate-700"
              >
                Log out
              </button>
            </>
          ) : (
            <>
              <Link href="/login" onClick={() => setMenuOpen(false)} className="text-slate-600 hover:text-slate-900">
                Log in
              </Link>
              <Link
                href="/register"
                onClick={() => setMenuOpen(false)}
                className="rounded bg-slate-900 px-3 py-1.5 text-center text-white hover:bg-slate-700"
              >
                Sign up
              </Link>
            </>
          )}
        </div>
      )}
    </nav>
  );
}
