"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Compass, X } from "lucide-react";
import type { RoutingResponseDTO } from "@/types/network";

interface DispatchPlanModalProps {
  open: boolean;
  onClose: () => void;
  result: RoutingResponseDTO | null;
}

export default function DispatchPlanModal({ open, onClose, result }: DispatchPlanModalProps) {
  const assessedRiskCount = result
    ? result.dispatchPlan.filter((step) => step.isRiskFlagged && !!step.riskReason?.trim()).length
    : 0;

  return (
    <AnimatePresence>
      {open && result && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/75 p-4 backdrop-blur-sm"
          onClick={onClose}
        >
          <motion.section
            initial={{ opacity: 0, scale: 0.96, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 12 }}
            transition={{ duration: 0.22 }}
            className="w-full max-w-4xl rounded-2xl border border-cyan-400/30 bg-slate-950/95 shadow-[0_0_50px_-18px_rgba(34,211,238,0.75)]"
            role="dialog"
            aria-modal="true"
            onClick={(eventValue) => eventValue.stopPropagation()}
          >
            <header className="flex items-center justify-between border-b border-slate-700/70 px-5 py-4">
              <div>
                <h3 className="flex items-center gap-2 text-lg font-semibold text-cyan-200">
                  <Compass className="h-5 w-5" />
                  Dispatch Path Ready
                </h3>
                <p className="mt-1 text-xs text-slate-400">
                  {result.algorithmUsed} | Dispatch cost {result.dispatchCost} | MST cost {result.totalCost}
                </p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-slate-600/70 bg-slate-900/70 p-2 text-slate-300 transition hover:border-cyan-400/60 hover:text-cyan-200"
                aria-label="Close dispatch modal"
              >
                <X className="h-4 w-4" />
              </button>
            </header>

            <div className="max-h-[70vh] overflow-y-auto px-5 py-4">
              <div className="mb-3 grid gap-2 text-xs text-slate-300 md:grid-cols-4">
                <div className="rounded-lg border border-slate-700/70 bg-slate-900/70 p-2">
                  Warehouse
                  <p className="mt-1 text-cyan-200">{result.warehouse.name}</p>
                </div>
                <div className="rounded-lg border border-slate-700/70 bg-slate-900/70 p-2">
                  Total Steps
                  <p className="mt-1 text-cyan-200">{result.dispatchPlan.length}</p>
                </div>
                <div className="rounded-lg border border-slate-700/70 bg-slate-900/70 p-2">
                  Dispatch Cost
                  <p className="mt-1 text-emerald-200">{result.dispatchCost}</p>
                </div>
                <div className="rounded-lg border border-slate-700/70 bg-slate-900/70 p-2">
                  Risk Legs
                  <p className="mt-1 text-orange-200">{assessedRiskCount}</p>
                </div>
              </div>

              <div className="space-y-2">
                {result.dispatchPlan.map((step) => {
                  const hasAssessedRisk = step.isRiskFlagged && !!step.riskReason?.trim();
                  return (
                    <div
                      key={`dispatch-step-${step.sequence}`}
                      className={`rounded-lg border px-3 py-2 text-sm ${
                        hasAssessedRisk
                          ? "border-orange-400/45 bg-orange-950/25 text-orange-200"
                          : "border-slate-700/70 bg-slate-900/70 text-slate-200"
                      }`}
                    >
                      <p className="font-medium">
                        #{step.sequence} {step.fromNodeName} -&gt; {step.toStoreName}
                      </p>
                      <p className="mt-1 text-xs">
                        Leg cost: {step.legCost} | Cumulative: {step.cumulativeCost}
                        {step.fromWarehouse ? " | Start from warehouse" : ""}
                        {hasAssessedRisk ? ` | Risk reason: ${step.riskReason}` : ""}
                      </p>
                    </div>
                  );
                })}
              </div>
            </div>
          </motion.section>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
