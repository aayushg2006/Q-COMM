import type { ReactNode } from "react";

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[radial-gradient(circle_at_15%_10%,rgba(34,211,238,0.12),transparent_35%),radial-gradient(circle_at_88%_12%,rgba(59,130,246,0.14),transparent_38%),#03060d] px-4 py-4 text-slate-100 md:px-6 md:py-6">
      <div className="bg-grid-overlay absolute inset-0 opacity-85" />
      <div className="relative z-10 mx-auto flex w-full max-w-[1800px] flex-col gap-4">
        {children}
      </div>
    </div>
  );
}

