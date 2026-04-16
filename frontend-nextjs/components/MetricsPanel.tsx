"use client";

import { motion } from "framer-motion";
import { AlertTriangle, Compass, Cpu, Gauge, Network, Warehouse } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { RoutingResponseDTO } from "@/types/network";

interface MetricsPanelProps {
  result: RoutingResponseDTO | null;
}

function useCountUp(target: number): number {
  const [value, setValue] = useState(0);

  useEffect(() => {
    let frameId = 0;
    const duration = 900;
    const startTime = performance.now();

    const step = (now: number): void => {
      const progress = Math.min(1, (now - startTime) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) {
        frameId = requestAnimationFrame(step);
      }
    };

    frameId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frameId);
  }, [target]);

  return value;
}

export default function MetricsPanel({ result }: MetricsPanelProps) {
  const executionMs = useCountUp(result?.executionTimeMs ?? 0);
  const totalCost = useCountUp(result?.totalCost ?? 0);
  const dispatchCost = useCountUp(result?.dispatchCost ?? 0);
  const hasRiskFlaggedEdge = !!result?.mstEdges.some(
    (edge) => edge.isRiskFlagged && !!edge.riskReason?.trim(),
  );
  const dispatchPreview = useMemo(() => result?.dispatchPlan.slice(0, 6) ?? [], [result]);
  const riskReasons = useMemo(() => {
    if (!result) {
      return [];
    }
    const reasons = new Set<string>();
    for (const step of result.dispatchPlan) {
      if (step.isRiskFlagged && step.riskReason) {
        reasons.add(step.riskReason);
      }
    }
    for (const edge of result.mstEdges) {
      if (edge.isRiskFlagged && edge.riskReason) {
        reasons.add(edge.riskReason);
      }
    }
    return Array.from(reasons).slice(0, 3);
  }, [result]);

  return (
    <section className="rounded-2xl border border-cyan-500/20 bg-slate-950/70 p-4 shadow-[0_0_45px_-20px_rgba(34,211,238,0.7)] backdrop-blur-md">
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
        className="space-y-3"
      >
        <h2 className="flex items-center gap-2 text-sm font-semibold tracking-[0.28em] text-cyan-300/90 uppercase">
          <Gauge className="h-4 w-4" />
          Optimization Telemetry
        </h2>

        {!result ? (
          <p className="rounded-lg border border-slate-700/70 bg-slate-900/70 p-3 text-sm text-slate-400">
            Run an optimization to populate live metrics.
          </p>
        ) : (
          <motion.div
            initial="hidden"
            animate="visible"
            variants={{
              hidden: {},
              visible: {
                transition: {
                  staggerChildren: 0.08,
                },
              },
            }}
            className="grid gap-3 md:grid-cols-2"
          >
            <motion.div
              variants={{ hidden: { opacity: 0, y: 8 }, visible: { opacity: 1, y: 0 } }}
              className="rounded-xl border border-cyan-500/30 bg-slate-900/80 p-3"
            >
              <p className="flex items-center gap-2 text-xs tracking-wider text-slate-400 uppercase">
                <Cpu className="h-3.5 w-3.5" />
                Execution Time
              </p>
              <p className="mt-2 text-2xl font-semibold text-cyan-300">{executionMs} ms</p>
            </motion.div>

            <motion.div
              variants={{ hidden: { opacity: 0, y: 8 }, visible: { opacity: 1, y: 0 } }}
              className="rounded-xl border border-blue-500/30 bg-slate-900/80 p-3"
            >
              <p className="flex items-center gap-2 text-xs tracking-wider text-slate-400 uppercase">
                <Network className="h-3.5 w-3.5" />
                Total MST Cost
              </p>
              <p className="mt-2 text-2xl font-semibold text-blue-300">{totalCost}</p>
            </motion.div>

            <motion.div
              variants={{ hidden: { opacity: 0, y: 8 }, visible: { opacity: 1, y: 0 } }}
              className="rounded-xl border border-emerald-500/30 bg-slate-900/80 p-3"
            >
              <p className="flex items-center gap-2 text-xs tracking-wider text-slate-400 uppercase">
                <Compass className="h-3.5 w-3.5" />
                Dispatch Cost
              </p>
              <p className="mt-2 text-2xl font-semibold text-emerald-300">{dispatchCost}</p>
            </motion.div>

            <motion.div
              variants={{ hidden: { opacity: 0, y: 8 }, visible: { opacity: 1, y: 0 } }}
              className="rounded-xl border border-fuchsia-500/30 bg-slate-900/80 p-3"
            >
              <p className="text-xs tracking-wider text-slate-400 uppercase">Algorithm</p>
              <p className="mt-2 text-2xl font-semibold text-fuchsia-300">{result.algorithmUsed}</p>
            </motion.div>
          </motion.div>
        )}

        {result?.warehouse && (
          <div className="rounded-lg border border-emerald-500/40 bg-emerald-950/25 p-3 text-sm text-emerald-200">
            <p className="flex items-center gap-2 font-medium">
              <Warehouse className="h-4 w-4" />
              {result.warehouse.custom ? "Custom" : "Default"} operation hub: {result.warehouse.name}
            </p>
            <p className="mt-1 text-xs text-emerald-200/80">
              ({result.warehouse.lat.toFixed(5)}, {result.warehouse.lng.toFixed(5)})
            </p>
          </div>
        )}

        {hasRiskFlaggedEdge && (
          <motion.div
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            className="mt-1 flex items-start gap-2 rounded-lg border border-orange-400/40 bg-orange-950/30 p-3 text-sm text-orange-200"
          >
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p>AI risk-adjusted legs detected.</p>
              {riskReasons.length > 0 && (
                <p className="mt-1 text-xs text-orange-100/90">
                  {riskReasons.join(" | ")}
                </p>
              )}
            </div>
          </motion.div>
        )}

        {result && result.dispatchPlan.length > 0 && (
          <div className="rounded-lg border border-slate-700/70 bg-slate-900/70 p-3">
            <p className="mb-2 text-xs tracking-wider text-slate-400 uppercase">
              Dispatch Order ({result.dispatchPlan.length} steps)
            </p>
            <div className="max-h-36 space-y-1 overflow-y-auto pr-1">
              {dispatchPreview.map((step) => (
                <p key={`dispatch-${step.sequence}`} className="text-xs text-slate-300">
                  #{step.sequence} {step.fromNodeName} -&gt; {step.toStoreName} ({step.legCost})
                  {step.isRiskFlagged && step.riskReason ? " [Risk]" : ""}
                </p>
              ))}
              {result.dispatchPlan.length > dispatchPreview.length && (
                <p className="text-xs text-slate-400">+{result.dispatchPlan.length - dispatchPreview.length} more steps</p>
              )}
            </div>
          </div>
        )}
      </motion.div>
    </section>
  );
}
