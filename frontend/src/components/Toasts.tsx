"use client";

export interface Toast {
  id: number;
  message: string;
}

export function Toasts({ toasts }: { toasts: Toast[] }) {
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <div key={toast.id} className="rounded bg-slate-900 px-4 py-2 text-sm text-white shadow-lg">
          {toast.message}
        </div>
      ))}
    </div>
  );
}
