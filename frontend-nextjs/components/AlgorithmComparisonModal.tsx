"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Layers3, Scale, X } from "lucide-react";
import { useMemo } from "react";
import type { CompareResponseDTO, DarkStore, RouteEdge, RoutingResponseDTO } from "@/types/network";

interface AlgorithmComparisonModalProps {
  open: boolean;
  onClose: () => void;
  comparison: CompareResponseDTO | null;
  stores: DarkStore[];
}

function routeLabel(edge: RouteEdge, storesById: Map<number, string>): string {
  const sourceName = storesById.get(edge.source) ?? `Store ${edge.source}`;
  const targetName = storesById.get(edge.target) ?? `Store ${edge.target}`;
  const suffix = edge.isRiskFlagged && edge.riskReason ? ` | ${edge.riskReason}` : "";
  return `${sourceName} -> ${targetName} (${edge.weight})${suffix}`;
}

function SummaryCard({ title, data }: { title: string; data: RoutingResponseDTO }) {
  const riskLegs = data.mstEdges.filter((edge) => edge.isRiskFlagged).length;
  return (
    <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-3">
      <p className="text-xs tracking-wider text-slate-400 uppercase">{title}</p>
      <div className="mt-2 grid grid-cols-2 gap-2 text-xs text-slate-300">
        <p>MST Cost: <span className="text-cyan-200">{data.totalCost}</span></p>
        <p>Exec: <span className="text-cyan-200">{data.executionTimeMs} ms</span></p>
        <p>MST Edges: <span className="text-cyan-200">{data.mstEdges.length}</span></p>
        <p>Dispatch Steps: <span className="text-cyan-200">{data.dispatchPlan.length}</span></p>
        <p>Dispatch Cost: <span className="text-cyan-200">{data.dispatchCost}</span></p>
        <p>Risk Legs: <span className="text-orange-200">{riskLegs}</span></p>
      </div>
    </div>
  );
}

export default function AlgorithmComparisonModal({
  open,
  onClose,
  comparison,
  stores,
}: AlgorithmComparisonModalProps) {
  const storesById = useMemo(() => {
    const map = new Map<number, string>();
    for (const store of stores) {
      map.set(store.id, store.name);
    }
    return map;
  }, [stores]);

  return (
    <AnimatePresence>
      {open && comparison && (
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
            className="w-full max-w-5xl rounded-2xl border border-purple-400/30 bg-slate-950/95 shadow-[0_0_55px_-20px_rgba(168,85,247,0.72)]"
            role="dialog"
            aria-modal="true"
            onClick={(eventValue) => eventValue.stopPropagation()}
          >
            <header className="flex items-center justify-between border-b border-slate-700/70 px-5 py-4">
              <div>
                <h3 className="flex items-center gap-2 text-lg font-semibold text-purple-200">
                  <Scale className="h-5 w-5" />
                  Detailed Algorithm Comparison
                </h3>
                <p className="mt-1 text-sm text-purple-200/80">
                  Recommendation: <span className="font-semibold">{comparison.recommendedAlgorithm}</span> |{" "}
                  {comparison.reason}
                </p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-slate-600/70 bg-slate-900/70 p-2 text-slate-300 transition hover:border-purple-400/60 hover:text-purple-200"
                aria-label="Close comparison modal"
              >
                <X className="h-4 w-4" />
              </button>
            </header>

            <div className="max-h-[72vh] overflow-y-auto px-5 py-4">
              <div className="mb-3 grid gap-3 md:grid-cols-2">
                <SummaryCard title="Prim" data={comparison.prim} />
                <SummaryCard title="Kruskal" data={comparison.kruskal} />
              </div>

              <div className="mb-3 grid gap-2 rounded-xl border border-slate-700/70 bg-slate-900/70 p-3 text-xs text-slate-300 md:grid-cols-4">
                <p>Cost Delta (P-K): <span className="text-cyan-200">{comparison.details.costDelta}</span></p>
                <p>Exec Delta (P-K): <span className="text-cyan-200">{comparison.details.executionTimeDeltaMs} ms</span></p>
                <p>Shared Edge Count: <span className="text-cyan-200">{comparison.details.sharedEdgeCount}</span></p>
                <p>Edge Overlap: <span className="text-cyan-200">{comparison.details.edgeOverlapPercent}%</span></p>
              </div>

              <div className="grid gap-3 md:grid-cols-2">
                <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-3">
                  <p className="flex items-center gap-2 text-xs tracking-wider text-slate-400 uppercase">
                    <Layers3 className="h-3.5 w-3.5" />
                    Prim-Only Edges ({comparison.details.primOnlyEdges.length})
                  </p>
                  <div className="mt-2 max-h-40 space-y-1 overflow-y-auto text-xs text-slate-300">
                    {comparison.details.primOnlyEdges.length === 0 ? (
                      <p className="text-slate-500">No Prim-only edges in this scenario.</p>
                    ) : (
                      comparison.details.primOnlyEdges.map((edge, index) => (
                        <p key={`prim-only-${edge.source}-${edge.target}-${index}`}>
                          {routeLabel(edge, storesById)}
                        </p>
                      ))
                    )}
                  </div>
                </div>

                <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-3">
                  <p className="flex items-center gap-2 text-xs tracking-wider text-slate-400 uppercase">
                    <Layers3 className="h-3.5 w-3.5" />
                    Kruskal-Only Edges ({comparison.details.kruskalOnlyEdges.length})
                  </p>
                  <div className="mt-2 max-h-40 space-y-1 overflow-y-auto text-xs text-slate-300">
                    {comparison.details.kruskalOnlyEdges.length === 0 ? (
                      <p className="text-slate-500">No Kruskal-only edges in this scenario.</p>
                    ) : (
                      comparison.details.kruskalOnlyEdges.map((edge, index) => (
                        <p key={`kruskal-only-${edge.source}-${edge.target}-${index}`}>
                          {routeLabel(edge, storesById)}
                        </p>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          </motion.section>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
