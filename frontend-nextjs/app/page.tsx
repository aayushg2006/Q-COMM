"use client";

import { motion } from "framer-motion";
import { ArrowRight, Zap } from "lucide-react";
import Link from "next/link";

export default function HomePage() {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[radial-gradient(circle_at_20%_20%,rgba(34,211,238,0.12),transparent_45%),radial-gradient(circle_at_85%_10%,rgba(59,130,246,0.12),transparent_45%),#05070d] px-6">
      <div className="bg-grid-overlay absolute inset-0" />
      <div className="absolute -top-30 left-1/2 h-72 w-72 -translate-x-1/2 rounded-full bg-cyan-400/20 blur-[100px]" />

      <motion.main
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.65, ease: "easeOut" }}
        className="relative z-10 w-full max-w-4xl rounded-3xl border border-cyan-500/25 bg-slate-950/70 p-10 text-slate-100 shadow-[0_0_80px_-28px_rgba(34,211,238,0.8)] backdrop-blur-md md:p-14"
      >
        <motion.p
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.15 }}
          className="mb-6 inline-flex items-center gap-2 rounded-full border border-cyan-400/35 bg-cyan-400/10 px-4 py-1 text-xs tracking-[0.22em] text-cyan-200 uppercase"
        >
          <Zap className="h-3.5 w-3.5" />
          Intelligent Logistics Core
        </motion.p>

        <motion.h1
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.25 }}
          className="text-balance text-4xl leading-tight font-semibold md:text-6xl"
        >
          Q-Comm Backbone: <span className="text-cyan-300">Intelligent DAA Routing.</span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.35 }}
          className="mt-6 max-w-2xl text-pretty text-base leading-7 text-slate-300 md:text-lg"
        >
          Launch a cinematic command center for minimum spanning tree optimization,
          AI-risk-aware route intelligence, and live dark-store network visualization.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.45 }}
          className="mt-10"
        >
          <Link
            href="/dashboard"
            className="group inline-flex items-center gap-2 rounded-xl border border-cyan-300/55 bg-gradient-to-r from-cyan-500/25 via-blue-500/20 to-fuchsia-500/25 px-6 py-3 text-sm font-medium tracking-[0.14em] text-cyan-100 uppercase shadow-[0_0_40px_-20px_rgba(34,211,238,1)] transition hover:border-cyan-300 hover:shadow-[0_0_45px_-15px_rgba(34,211,238,1)]"
          >
            Launch Command Center
            <ArrowRight className="h-4 w-4 transition group-hover:translate-x-1" />
          </Link>
        </motion.div>
      </motion.main>
    </div>
  );
}
