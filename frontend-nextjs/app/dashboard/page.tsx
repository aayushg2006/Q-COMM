"use client";

import axios from "axios";
import { motion } from "framer-motion";
import {
  AlertCircle,
  BrainCircuit,
  Download,
  LoaderCircle,
  LocateFixed,
  LogOut,
  MapPinPlus,
  Radar,
  RefreshCcw,
  Route,
  Scale,
  ShieldCheck,
  Warehouse,
} from "lucide-react";
import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useState } from "react";
import AlgorithmComparisonModal from "@/components/AlgorithmComparisonModal";
import DispatchPlanModal from "@/components/DispatchPlanModal";
import MetricsPanel from "@/components/MetricsPanel";
import {
  clearStoredAuthToken,
  compareAlgorithms,
  exportHistoryCsv,
  fetchAlertsStatus,
  fetchBelts,
  fetchDarkStores,
  fetchHistory,
  fetchHistorySummary,
  fetchRouteEdges,
  getStoredAuthToken,
  login,
  optimizeNetwork,
  resetAndSeedAllBelts,
  resetAndSeedBelt,
  seedAllBelts,
  seedBelt,
} from "@/lib/api";
import type {
  AlgorithmOption,
  BeltDescriptorDTO,
  CompareResponseDTO,
  DarkStore,
  EventOption,
  HistorySummaryDTO,
  RouteEdge,
  RoutingHistoryDTO,
  RoutingResponseDTO,
  WarehouseDTO,
  WarehouseSelection,
} from "@/types/network";

const NetworkMap = dynamic(() => import("@/components/NetworkMap"), {
  ssr: false,
  loading: () => (
    <div className="flex h-[60vh] items-center justify-center rounded-xl border border-slate-700/80 bg-slate-900/70 text-sm text-slate-300">
      <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
      Preparing map renderer...
    </div>
  ),
});

const EVENT_OPTIONS: Array<{ value: EventOption; label: string }> = [
  { value: "clear_traffic", label: "Clear Traffic (Baseline)" },
  { value: "heavy_rain", label: "Heavy Rain + Waterlogging" },
  { value: "peak_hour_congestion", label: "Peak-Hour Congestion" },
  { value: "roadwork_incident", label: "Roadwork / Lane Incident" },
];

const ALGORITHM_OPTIONS: Array<{ value: AlgorithmOption; label: string }> = [
  { value: "prim", label: "Prim's Algorithm" },
  { value: "kruskal", label: "Kruskal's Algorithm" },
];

const DEFAULT_BELT_CODE = "mumbai_borivali_andheri";

function normalizeErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)?.message;
    return message ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unexpected error while communicating with backend.";
}

function downloadBlob(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(objectUrl);
}

export default function DashboardPage() {
  const [stores, setStores] = useState<DarkStore[]>([]);
  const [initialEdges, setInitialEdges] = useState<RouteEdge[]>([]);
  const [result, setResult] = useState<RoutingResponseDTO | null>(null);
  const [algorithm, setAlgorithm] = useState<AlgorithmOption>("prim");
  const [beltCode, setBeltCode] = useState<string>(DEFAULT_BELT_CODE);
  const [beltOptions, setBeltOptions] = useState<BeltDescriptorDTO[]>([]);
  const [event, setEvent] = useState<EventOption>("clear_traffic");
  const [useDefaultWarehouse, setUseDefaultWarehouse] = useState(true);
  const [warehouseName, setWarehouseName] = useState("");
  const [warehouseLat, setWarehouseLat] = useState("");
  const [warehouseLng, setWarehouseLng] = useState("");
  const [isLocatingWarehouse, setIsLocatingWarehouse] = useState(false);
  const [isWarehousePinMode, setIsWarehousePinMode] = useState(false);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const [isOptimizing, setIsOptimizing] = useState(false);
  const [isComparing, setIsComparing] = useState(false);
  const [isAdminWorking, setIsAdminWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [adminMessage, setAdminMessage] = useState<string | null>(null);
  const [compareResult, setCompareResult] = useState<CompareResponseDTO | null>(null);
  const [historyRows, setHistoryRows] = useState<RoutingHistoryDTO[]>([]);
  const [historySummary, setHistorySummary] = useState<HistorySummaryDTO | null>(null);
  const [alertsStatus, setAlertsStatus] = useState<string>("STANDBY");
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authUsername, setAuthUsername] = useState("admin");
  const [authPassword, setAuthPassword] = useState("admin123");
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [isDispatchModalOpen, setIsDispatchModalOpen] = useState(false);
  const [isComparisonModalOpen, setIsComparisonModalOpen] = useState(false);

  const mstEdges = useMemo(() => result?.mstEdges ?? [], [result]);
  const selectedBelt = useMemo(
    () => beltOptions.find((belt) => belt.code === beltCode) ?? null,
    [beltCode, beltOptions],
  );
  const selectedBeltLabel = useMemo(() => selectedBelt?.label ?? beltCode, [beltCode, selectedBelt]);

  const applyBeltWarehouseDefaults = useCallback((belt: BeltDescriptorDTO | null) => {
    if (!belt) {
      return;
    }
    setWarehouseName(belt.warehouseName);
    setWarehouseLat(String(belt.warehouseLat));
    setWarehouseLng(String(belt.warehouseLng));
    setIsWarehousePinMode(false);
  }, []);

  const applyCustomWarehouseCoordinates = useCallback(
    (lat: number, lng: number, name?: string) => {
      setUseDefaultWarehouse(false);
      setWarehouseLat(lat.toFixed(6));
      setWarehouseLng(lng.toFixed(6));
      if (name) {
        setWarehouseName(name);
      }
      setIsWarehousePinMode(false);
    },
    [],
  );

  const resolveWarehouseSelection = useCallback((): WarehouseSelection => {
    if (useDefaultWarehouse) {
      return {
        name: selectedBelt?.warehouseName,
        lat: selectedBelt?.warehouseLat,
        lng: selectedBelt?.warehouseLng,
      };
    }

    const parsedLat = Number(warehouseLat);
    const parsedLng = Number(warehouseLng);
    if (!Number.isFinite(parsedLat) || !Number.isFinite(parsedLng)) {
      throw new Error("Custom warehouse coordinates must be valid numeric values.");
    }
    if (parsedLat < -90 || parsedLat > 90 || parsedLng < -180 || parsedLng > 180) {
      throw new Error("Custom warehouse coordinates are out of range.");
    }

    return {
      name: warehouseName.trim() || "Custom Mega Warehouse",
      lat: parsedLat,
      lng: parsedLng,
    };
  }, [selectedBelt, useDefaultWarehouse, warehouseLat, warehouseLng, warehouseName]);

  const warehousePreview = useMemo<WarehouseDTO | null>(() => {
    if (result?.warehouse) {
      return result.warehouse;
    }

    if (useDefaultWarehouse) {
      if (!selectedBelt) {
        return null;
      }
      return {
        name: selectedBelt.warehouseName,
        lat: selectedBelt.warehouseLat,
        lng: selectedBelt.warehouseLng,
        custom: false,
      };
    }

    const parsedLat = Number(warehouseLat);
    const parsedLng = Number(warehouseLng);
    if (!Number.isFinite(parsedLat) || !Number.isFinite(parsedLng)) {
      return null;
    }

    return {
      name: warehouseName.trim() || "Custom Mega Warehouse",
      lat: parsedLat,
      lng: parsedLng,
      custom: true,
    };
  }, [result, selectedBelt, useDefaultWarehouse, warehouseLat, warehouseLng, warehouseName]);

  const refreshAnalytics = useCallback(async () => {
    try {
      const [historyData, summaryData] = await Promise.all([fetchHistory(12), fetchHistorySummary()]);
      setHistoryRows(historyData);
      setHistorySummary(summaryData);
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    }

    try {
      const status = await fetchAlertsStatus();
      setAlertsStatus(status.status);
    } catch {
      setAlertsStatus("STANDBY");
    }
  }, []);

  const loadInitialGraph = useCallback(async (selectedBeltCode: string) => {
    setIsBootstrapping(true);
    setError(null);
    try {
      const [storesData, edgesData] = await Promise.all([
        fetchDarkStores(selectedBeltCode),
        fetchRouteEdges(selectedBeltCode),
      ]);
      setStores(storesData);
      setInitialEdges(edgesData);
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsBootstrapping(false);
    }
  }, []);

  useEffect(() => {
    const hasToken = !!getStoredAuthToken();
    setIsAuthenticated(hasToken);
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    void (async () => {
      try {
        const belts = await fetchBelts();
        setBeltOptions(belts);

        const nextCode =
          belts.length > 0 && !belts.some((belt) => belt.code === beltCode)
            ? belts[0].code
            : beltCode;

        setBeltCode(nextCode);
        applyBeltWarehouseDefaults(
          belts.find((belt) => belt.code === nextCode) ?? null,
        );
      } catch (requestError) {
        setError(normalizeErrorMessage(requestError));
      }
      await refreshAnalytics();
    })();
  }, [applyBeltWarehouseDefaults, beltCode, isAuthenticated, refreshAnalytics]);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    void loadInitialGraph(beltCode);
  }, [beltCode, isAuthenticated, loadInitialGraph]);

  const executeOptimization = useCallback(async () => {
    setIsOptimizing(true);
    setError(null);
    setAdminMessage(null);
    setCompareResult(null);

    try {
      const warehouse = resolveWarehouseSelection();
      const optimizationResult = await optimizeNetwork(algorithm, event, beltCode, warehouse);
      setResult(optimizationResult);
      setIsDispatchModalOpen(true);
      await refreshAnalytics();
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsOptimizing(false);
    }
  }, [algorithm, beltCode, event, refreshAnalytics, resolveWarehouseSelection]);

  const executeCompare = useCallback(async () => {
    setIsComparing(true);
    setError(null);

    try {
      const warehouse = resolveWarehouseSelection();
      const compared = await compareAlgorithms(event, beltCode, warehouse);
      setCompareResult(compared);
      setResult(compared.recommendedAlgorithm === "PRIM" ? compared.prim : compared.kruskal);
      setIsComparisonModalOpen(true);
      await refreshAnalytics();
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsComparing(false);
    }
  }, [beltCode, event, refreshAnalytics, resolveWarehouseSelection]);

  const handleLogin = useCallback(async () => {
    setIsAuthenticating(true);
    setAuthError(null);
    try {
      await login(authUsername, authPassword);
      setIsAuthenticated(true);
    } catch (requestError) {
      setAuthError(normalizeErrorMessage(requestError));
    } finally {
      setIsAuthenticating(false);
    }
  }, [authPassword, authUsername]);

  const handleLogout = useCallback(() => {
    clearStoredAuthToken();
    setIsAuthenticated(false);
    setResult(null);
    setCompareResult(null);
    setHistoryRows([]);
    setHistorySummary(null);
    setStores([]);
    setInitialEdges([]);
    setBeltOptions([]);
    setBeltCode(DEFAULT_BELT_CODE);
    setIsLocatingWarehouse(false);
    setIsWarehousePinMode(false);
    setIsDispatchModalOpen(false);
    setIsComparisonModalOpen(false);
  }, []);

  const handleExportHistory = useCallback(async () => {
    setError(null);
    try {
      const blob = await exportHistoryCsv(100);
      downloadBlob(blob, "routing_history.csv");
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    }
  }, []);

  const handleSeed = useCallback(async () => {
    setIsAdminWorking(true);
    setError(null);
    try {
      const response = await seedBelt(beltCode);
      setAdminMessage(response.message);
      await loadInitialGraph(beltCode);
      await refreshAnalytics();
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsAdminWorking(false);
    }
  }, [beltCode, loadInitialGraph, refreshAnalytics]);

  const handleResetAndSeed = useCallback(async () => {
    const confirmed = window.confirm(
      `This will clear ${selectedBeltLabel} network/history and reseed it. Continue?`,
    );
    if (!confirmed) {
      return;
    }
    setIsAdminWorking(true);
    setError(null);
    try {
      const response = await resetAndSeedBelt(beltCode);
      setAdminMessage(response.message);
      setResult(null);
      setCompareResult(null);
      await loadInitialGraph(beltCode);
      await refreshAnalytics();
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsAdminWorking(false);
    }
  }, [beltCode, loadInitialGraph, refreshAnalytics, selectedBeltLabel]);

  const handleSeedAllBelts = useCallback(async () => {
    setIsAdminWorking(true);
    setError(null);
    try {
      const response = await seedAllBelts();
      setAdminMessage(response.message);
      await loadInitialGraph(beltCode);
      await refreshAnalytics();
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsAdminWorking(false);
    }
  }, [beltCode, loadInitialGraph, refreshAnalytics]);

  const handleResetAndSeedAllBelts = useCallback(async () => {
    const confirmed = window.confirm(
      "This will reset all belt data and all history, then reseed all belts. Continue?",
    );
    if (!confirmed) {
      return;
    }
    setIsAdminWorking(true);
    setError(null);
    try {
      const response = await resetAndSeedAllBelts();
      setAdminMessage(response.message);
      setResult(null);
      setCompareResult(null);
      await loadInitialGraph(beltCode);
      await refreshAnalytics();
    } catch (requestError) {
      setError(normalizeErrorMessage(requestError));
    } finally {
      setIsAdminWorking(false);
    }
  }, [beltCode, loadInitialGraph, refreshAnalytics]);

  const handleUseCurrentLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setError("Geolocation is not supported in this browser.");
      return;
    }

    setIsLocatingWarehouse(true);
    setError(null);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        applyCustomWarehouseCoordinates(
          position.coords.latitude,
          position.coords.longitude,
          "Current Location Warehouse",
        );
        setIsLocatingWarehouse(false);
      },
      (geoError) => {
        setIsLocatingWarehouse(false);
        setError(`Unable to fetch current location: ${geoError.message}`);
      },
      {
        enableHighAccuracy: true,
        timeout: 12_000,
        maximumAge: 60_000,
      },
    );
  }, [applyCustomWarehouseCoordinates]);

  const handleWarehousePinSelect = useCallback(
    (point: { lat: number; lng: number }) => {
      applyCustomWarehouseCoordinates(point.lat, point.lng);
    },
    [applyCustomWarehouseCoordinates],
  );

  const edgeLabel = useCallback(
    (edge: RouteEdge) => {
      const sourceName = stores.find((store) => store.id === edge.source)?.name ?? `Store ${edge.source}`;
      const targetName = stores.find((store) => store.id === edge.target)?.name ?? `Store ${edge.target}`;
      return `${sourceName} -> ${targetName} (${edge.weight})`;
    },
    [stores],
  );

  if (!isAuthenticated) {
    return (
      <div className="flex min-h-[calc(100vh-3rem)] items-center justify-center">
        <div className="w-full max-w-md rounded-2xl border border-cyan-500/25 bg-slate-950/80 p-6 shadow-[0_0_45px_-18px_rgba(34,211,238,0.8)] backdrop-blur-md">
          <h1 className="text-lg font-semibold text-cyan-200">Secure Access</h1>
          <p className="mt-1 text-sm text-slate-400">Login to open Q-Comm command center.</p>

          <label className="mt-5 block text-xs tracking-wider text-slate-400 uppercase">Username</label>
          <input
            value={authUsername}
            onChange={(eventValue) => setAuthUsername(eventValue.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 focus:border-cyan-400 focus:outline-none"
            placeholder="admin"
          />

          <label className="mt-4 block text-xs tracking-wider text-slate-400 uppercase">Password</label>
          <input
            type="password"
            value={authPassword}
            onChange={(eventValue) => setAuthPassword(eventValue.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 focus:border-cyan-400 focus:outline-none"
            placeholder="admin123"
          />

          {authError && <p className="mt-3 text-sm text-red-300">{authError}</p>}

          <button
            type="button"
            onClick={() => void handleLogin()}
            disabled={isAuthenticating}
            className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-cyan-300/45 bg-gradient-to-r from-cyan-500/20 via-blue-500/20 to-fuchsia-500/20 px-4 py-2 text-sm font-medium tracking-[0.12em] text-cyan-100 uppercase transition hover:border-cyan-300 hover:shadow-[0_0_30px_-10px_rgba(34,211,238,1)] disabled:cursor-not-allowed disabled:opacity-55"
          >
            {isAuthenticating ? (
              <>
                <LoaderCircle className="h-4 w-4 animate-spin" />
                Authenticating...
              </>
            ) : (
              <>
                <ShieldCheck className="h-4 w-4" />
                Login
              </>
            )}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-[calc(100vh-3rem)] flex-col gap-4">
      <header className="rounded-2xl border border-cyan-500/20 bg-slate-950/70 px-5 py-4 shadow-[0_0_40px_-22px_rgba(34,211,238,0.7)] backdrop-blur-md">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs tracking-[0.28em] text-cyan-300 uppercase">Q-Comm Backbone</p>
            <h1 className="mt-1 text-xl font-semibold text-slate-100 md:text-2xl">
              Intelligent Network Command Center
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <div className="inline-flex items-center gap-2 rounded-full border border-cyan-400/35 bg-cyan-500/10 px-3 py-1 text-xs text-cyan-200">
              <Radar className="h-3.5 w-3.5" />
              Live DAA Optimization Surface
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex items-center gap-1 rounded-full border border-slate-600/60 px-3 py-1 text-xs text-slate-300 hover:border-cyan-400/50 hover:text-cyan-200"
            >
              <LogOut className="h-3.5 w-3.5" />
              Logout
            </button>
          </div>
        </div>
      </header>

      <div className="grid flex-1 gap-4 lg:grid-cols-[30%_70%]">
        <motion.aside
          initial={{ opacity: 0, x: -14 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.4 }}
          className="space-y-4 rounded-2xl border border-cyan-500/20 bg-slate-950/70 p-4 shadow-[0_0_45px_-22px_rgba(34,211,238,0.75)] backdrop-blur-md"
        >
          <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-4">
            <h2 className="mb-4 flex items-center gap-2 text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">
              <BrainCircuit className="h-4 w-4" />
              Control Panel
            </h2>

            <label className="mb-2 block text-xs tracking-wider text-slate-400 uppercase">Belt Selection</label>
            <select
              value={beltCode}
              onChange={(eventValue) => {
                const nextBeltCode = eventValue.target.value;
                setBeltCode(nextBeltCode);
                if (useDefaultWarehouse) {
                  applyBeltWarehouseDefaults(
                    beltOptions.find((belt) => belt.code === nextBeltCode) ?? null,
                  );
                }
              }}
              className="mb-4 w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 focus:border-cyan-400 focus:outline-none"
            >
              {beltOptions.length === 0 ? (
                <option value={DEFAULT_BELT_CODE}>Mumbai Borivali-Andheri Belt</option>
              ) : (
                beltOptions.map((belt) => (
                  <option key={belt.code} value={belt.code}>
                    {belt.label}
                  </option>
                ))
              )}
            </select>

            <label className="mb-2 block text-xs tracking-wider text-slate-400 uppercase">Operation Hub</label>
            <div className="mb-3 flex gap-2">
              <button
                type="button"
                onClick={() => {
                  setUseDefaultWarehouse(true);
                  setIsWarehousePinMode(false);
                  applyBeltWarehouseDefaults(selectedBelt);
                }}
                className={`flex-1 rounded-lg border px-3 py-2 text-xs uppercase transition ${
                  useDefaultWarehouse
                    ? "border-emerald-300/60 bg-emerald-500/15 text-emerald-200"
                    : "border-slate-600/70 bg-slate-950/80 text-slate-300"
                }`}
              >
                Belt Default
              </button>
              <button
                type="button"
                onClick={() => setUseDefaultWarehouse(false)}
                className={`flex-1 rounded-lg border px-3 py-2 text-xs uppercase transition ${
                  !useDefaultWarehouse
                    ? "border-fuchsia-300/60 bg-fuchsia-500/15 text-fuchsia-200"
                    : "border-slate-600/70 bg-slate-950/80 text-slate-300"
                }`}
              >
                Custom
              </button>
            </div>

            <input
              value={warehouseName}
              onChange={(eventValue) => setWarehouseName(eventValue.target.value)}
              disabled={useDefaultWarehouse}
              className="mb-2 w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 disabled:cursor-not-allowed disabled:opacity-70"
              placeholder="Warehouse name"
            />
            <div className="mb-2 grid grid-cols-2 gap-2">
              <input
                value={warehouseLat}
                onChange={(eventValue) => setWarehouseLat(eventValue.target.value)}
                disabled={useDefaultWarehouse}
                className="w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 disabled:cursor-not-allowed disabled:opacity-70"
                placeholder="Latitude"
              />
              <input
                value={warehouseLng}
                onChange={(eventValue) => setWarehouseLng(eventValue.target.value)}
                disabled={useDefaultWarehouse}
                className="w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 disabled:cursor-not-allowed disabled:opacity-70"
                placeholder="Longitude"
              />
            </div>
            <div className="mb-4 grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={handleUseCurrentLocation}
                disabled={isLocatingWarehouse}
                className="inline-flex items-center justify-center gap-1 rounded-lg border border-cyan-400/45 bg-cyan-500/10 px-2 py-2 text-xs text-cyan-200 uppercase hover:border-cyan-300 disabled:cursor-not-allowed disabled:opacity-55"
              >
                {isLocatingWarehouse ? (
                  <>
                    <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                    Locating...
                  </>
                ) : (
                  <>
                    <LocateFixed className="h-3.5 w-3.5" />
                    Current Location
                  </>
                )}
              </button>
              <button
                type="button"
                onClick={() => {
                  setUseDefaultWarehouse(false);
                  setError(null);
                  setIsWarehousePinMode((previous) => !previous);
                }}
                className={`inline-flex items-center justify-center gap-1 rounded-lg border px-2 py-2 text-xs uppercase transition ${
                  isWarehousePinMode
                    ? "border-fuchsia-300/60 bg-fuchsia-500/15 text-fuchsia-200"
                    : "border-slate-600/70 bg-slate-950/80 text-slate-300 hover:border-fuchsia-300/50"
                }`}
              >
                <MapPinPlus className="h-3.5 w-3.5" />
                {isWarehousePinMode ? "Cancel Pin Mode" : "Pick On Map"}
              </button>
            </div>

            <label className="mb-2 block text-xs tracking-wider text-slate-400 uppercase">
              Algorithm Selection
            </label>
            <select
              value={algorithm}
              onChange={(eventValue) => setAlgorithm(eventValue.target.value as AlgorithmOption)}
              className="mb-4 w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 focus:border-cyan-400 focus:outline-none"
            >
              {ALGORITHM_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <label className="mb-2 block text-xs tracking-wider text-slate-400 uppercase">Event Simulation</label>
            <select
              value={event}
              onChange={(eventValue) => setEvent(eventValue.target.value as EventOption)}
              className="mb-5 w-full rounded-lg border border-slate-600/70 bg-slate-950/80 px-3 py-2 text-sm text-slate-200 focus:border-cyan-400 focus:outline-none"
            >
              {EVENT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <div className="grid gap-2">
              <button
                type="button"
                onClick={() => void executeOptimization()}
                disabled={isOptimizing || isBootstrapping || stores.length === 0}
                className="group inline-flex w-full items-center justify-center gap-2 rounded-lg border border-cyan-300/45 bg-gradient-to-r from-cyan-500/20 via-blue-500/20 to-fuchsia-500/20 px-4 py-2 text-sm font-medium tracking-[0.12em] text-cyan-100 uppercase transition hover:border-cyan-300 hover:shadow-[0_0_30px_-10px_rgba(34,211,238,1)] disabled:cursor-not-allowed disabled:opacity-55"
              >
                {isOptimizing ? (
                  <>
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                    Executing...
                  </>
                ) : (
                  <>
                    <Route className="h-4 w-4 transition group-hover:translate-x-0.5" />
                    Execute Optimization
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={() => void executeCompare()}
                disabled={isComparing || isBootstrapping || stores.length === 0}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-purple-300/45 bg-purple-500/10 px-4 py-2 text-sm font-medium tracking-[0.1em] text-purple-200 uppercase transition hover:border-purple-300 disabled:cursor-not-allowed disabled:opacity-55"
              >
                {isComparing ? (
                  <>
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                    Comparing...
                  </>
                ) : (
                  <>
                    <Scale className="h-4 w-4" />
                    Compare Algorithms
                  </>
                )}
              </button>
            </div>
          </div>

          <MetricsPanel result={result} />

          <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-4">
            <p className="mb-2 text-xs tracking-[0.18em] text-cyan-300 uppercase">History Snapshot</p>
            <p className="text-sm text-slate-300">Runs: {historySummary?.totalRuns ?? 0}</p>
            <p className="text-xs text-slate-400">
              Latest cost: {historySummary?.latestTotalCost ?? "--"} | Latest exec: {historySummary?.latestExecutionTimeMs ?? "--"} ms
            </p>
          </div>

          <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-4">
            <p className="mb-2 text-xs tracking-[0.18em] text-cyan-300 uppercase">Operations</p>
            <div className="grid gap-2">
              <button type="button" onClick={() => void handleExportHistory()} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-600/80 bg-slate-950/80 px-3 py-2 text-xs text-slate-200 uppercase hover:border-cyan-400/60"><Download className="h-3.5 w-3.5" />Export History CSV</button>
              <button type="button" onClick={() => void handleSeed()} disabled={isAdminWorking} className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-600/80 bg-slate-950/80 px-3 py-2 text-xs text-slate-200 uppercase hover:border-cyan-400/60 disabled:opacity-55"><RefreshCcw className="h-3.5 w-3.5" />Seed Selected Belt</button>
              <button type="button" onClick={() => void handleResetAndSeed()} disabled={isAdminWorking} className="inline-flex items-center justify-center gap-2 rounded-lg border border-red-500/60 bg-red-950/30 px-3 py-2 text-xs text-red-200 uppercase hover:border-red-400 disabled:opacity-55"><RefreshCcw className="h-3.5 w-3.5" />Reset + Seed Selected Belt</button>
              <button type="button" onClick={() => void handleSeedAllBelts()} disabled={isAdminWorking} className="inline-flex items-center justify-center gap-2 rounded-lg border border-cyan-500/60 bg-cyan-950/30 px-3 py-2 text-xs text-cyan-200 uppercase hover:border-cyan-400 disabled:opacity-55"><RefreshCcw className="h-3.5 w-3.5" />Seed All Belts</button>
              <button type="button" onClick={() => void handleResetAndSeedAllBelts()} disabled={isAdminWorking} className="inline-flex items-center justify-center gap-2 rounded-lg border border-red-500/60 bg-red-950/30 px-3 py-2 text-xs text-red-200 uppercase hover:border-red-400 disabled:opacity-55"><RefreshCcw className="h-3.5 w-3.5" />Reset + Seed All Belts</button>
            </div>
            <p className="mt-2 text-xs text-slate-400">Alerts: {alertsStatus} (standby)</p>
            {adminMessage && <p className="mt-1 text-xs text-emerald-300">{adminMessage}</p>}
          </div>
        </motion.aside>

        <motion.main initial={{ opacity: 0, x: 14 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.4, delay: 0.08 }} className="space-y-4">
          {error && (
            <div className="flex items-start gap-2 rounded-xl border border-red-500/45 bg-red-950/30 p-3 text-sm text-red-200">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <div><p className="font-medium">Backend communication issue</p><p className="text-red-200/85">{error}</p></div>
            </div>
          )}

          {compareResult && (
            <div className="rounded-xl border border-purple-500/50 bg-gradient-to-r from-purple-950/35 via-slate-950/35 to-fuchsia-950/35 p-3 text-sm text-purple-200">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-semibold tracking-wide uppercase">Comparison Completed</p>
                  <p className="text-purple-100">
                    Recommendation: <span className="font-semibold">{compareResult.recommendedAlgorithm}</span>
                  </p>
                  <p className="text-purple-200/85">{compareResult.reason}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setIsComparisonModalOpen(true)}
                  className="rounded-lg border border-purple-300/50 bg-purple-500/15 px-3 py-1.5 text-xs font-medium text-purple-100 uppercase hover:border-purple-300 hover:bg-purple-500/25"
                >
                  Open Detailed Popup
                </button>
              </div>

              <div className="mt-2 grid gap-1 text-xs text-purple-200/80 md:grid-cols-2">
                <p>Prim: cost {compareResult.prim.totalCost}, {compareResult.prim.executionTimeMs} ms</p>
                <p>Kruskal: cost {compareResult.kruskal.totalCost}, {compareResult.kruskal.executionTimeMs} ms</p>
                <p>Shared edges: {compareResult.details.sharedEdgeCount}</p>
                <p>Overlap: {compareResult.details.edgeOverlapPercent}%</p>
              </div>

              {!compareResult.details.sameEdgeSet && (
                <div className="mt-2 grid gap-1 text-xs text-purple-200/75">
                  <p>Route topology differs across algorithms in this scenario.</p>
                  <p>Prim-only edges: {compareResult.details.primOnlyEdges.length}</p>
                  {compareResult.details.primOnlyEdges.slice(0, 2).map((edge, index) => (
                    <p key={`prim-only-${edge.source}-${edge.target}-${index}`}>P: {edgeLabel(edge)}</p>
                  ))}
                  <p>Kruskal-only edges: {compareResult.details.kruskalOnlyEdges.length}</p>
                  {compareResult.details.kruskalOnlyEdges.slice(0, 2).map((edge, index) => (
                    <p key={`kruskal-only-${edge.source}-${edge.target}-${index}`}>K: {edgeLabel(edge)}</p>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="rounded-xl border border-emerald-500/35 bg-emerald-950/20 px-3 py-2 text-xs text-emerald-200">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="flex items-center gap-2"><Warehouse className="h-4 w-4" />Distribution origin: {warehousePreview?.name ?? "Not configured"}{warehousePreview && <span> ({warehousePreview.lat.toFixed(5)}, {warehousePreview.lng.toFixed(5)})</span>}</p>
              {result && (
                <button
                  type="button"
                  onClick={() => setIsDispatchModalOpen(true)}
                  className="rounded-lg border border-emerald-300/50 bg-emerald-500/10 px-2 py-1 text-[11px] font-medium text-emerald-100 uppercase hover:border-emerald-200 hover:bg-emerald-500/20"
                >
                  View Dispatch Popup
                </button>
              )}
            </div>
          </div>

          {isBootstrapping ? (
            <div className="flex h-[60vh] items-center justify-center rounded-2xl border border-slate-700/70 bg-slate-900/65 text-sm text-slate-300"><LoaderCircle className="mr-2 h-4 w-4 animate-spin text-cyan-300" />Loading network topology...</div>
          ) : (
            <NetworkMap
              stores={stores}
              networkEdges={initialEdges}
              mstEdges={mstEdges}
              dispatchPlan={result?.dispatchPlan ?? []}
              warehouse={warehousePreview}
              warehousePinPicking={isWarehousePinMode}
              onWarehousePinSelect={handleWarehousePinSelect}
            />
          )}

          <div className="rounded-xl border border-slate-700/70 bg-slate-900/70 p-3">
            <p className="mb-2 text-xs tracking-[0.18em] text-cyan-300 uppercase">Recent Runs</p>
            {historyRows.length === 0 ? (
              <p className="text-sm text-slate-400">No optimization history yet.</p>
            ) : (
              <div className="max-h-36 overflow-y-auto pr-1">
                {historyRows.map((row) => (
                  <p key={row.id} className="text-xs text-slate-300">{new Date(row.timestamp).toLocaleString()} | {row.algorithmUsed} | {row.executionTimeMs} ms | cost {row.totalCost}</p>
                ))}
              </div>
            )}
          </div>
        </motion.main>
      </div>

      <DispatchPlanModal
        open={isDispatchModalOpen}
        onClose={() => setIsDispatchModalOpen(false)}
        result={result}
      />

      <AlgorithmComparisonModal
        open={isComparisonModalOpen}
        onClose={() => setIsComparisonModalOpen(false)}
        comparison={compareResult}
        stores={stores}
      />
    </div>
  );
}
