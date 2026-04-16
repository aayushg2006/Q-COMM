import axios from "axios";
import type {
  AdminActionResponseDTO,
  AlgorithmOption,
  AlertsStatusDTO,
  AuthResponseDTO,
  BeltDescriptorDTO,
  CompareResponseDTO,
  DarkStore,
  EventOption,
  HistorySummaryDTO,
  RouteEdge,
  RoutingHistoryDTO,
  RoutingResponseDTO,
  WarehouseSelection,
} from "@/types/network";

const AUTH_TOKEN_KEY = "qcomm_auth_token";

const api = axios.create({
  baseURL: "http://localhost:8080/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 20_000,
});

const EVENT_LABELS: Record<EventOption, string> = {
  clear_traffic: "",
  heavy_rain: "Heavy rain and waterlogging affecting arterial roads.",
  peak_hour_congestion: "Peak-hour congestion with high traffic density.",
  roadwork_incident: "Roadwork and lane closure incident on major corridors.",
};

api.interceptors.request.use((config) => {
  const token = getStoredAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearStoredAuthToken();
    }
    return Promise.reject(error);
  },
);

function ensureBrowser(): boolean {
  return typeof window !== "undefined";
}

export function getStoredAuthToken(): string | null {
  if (!ensureBrowser()) {
    return null;
  }
  return window.localStorage.getItem(AUTH_TOKEN_KEY);
}

export function clearStoredAuthToken(): void {
  if (!ensureBrowser()) {
    return;
  }
  window.localStorage.removeItem(AUTH_TOKEN_KEY);
}

function setStoredAuthToken(token: string): void {
  if (!ensureBrowser()) {
    return;
  }
  window.localStorage.setItem(AUTH_TOKEN_KEY, token);
}

export async function login(username: string, password: string): Promise<AuthResponseDTO> {
  const response = await api.post<AuthResponseDTO>("/auth/login", {
    username,
    password,
  });
  setStoredAuthToken(response.data.token);
  return response.data;
}

export async function fetchDarkStores(beltCode?: string): Promise<DarkStore[]> {
  const response = await api.get<DarkStore[]>("/network/stores", {
    params: { belt: beltCode },
  });
  return response.data;
}

export async function fetchRouteEdges(beltCode?: string): Promise<RouteEdge[]> {
  const response = await api.get<RouteEdge[]>("/network/edges", {
    params: { belt: beltCode },
  });
  return response.data;
}

export async function optimizeNetwork(
  algorithm: AlgorithmOption,
  event: EventOption,
  beltCode?: string,
  warehouse?: WarehouseSelection,
): Promise<RoutingResponseDTO> {
  const payload = {
    algorithm,
    event: EVENT_LABELS[event],
    beltCode,
    warehouseName: warehouse?.name,
    warehouseLat: warehouse?.lat ?? null,
    warehouseLng: warehouse?.lng ?? null,
  };
  const response = await api.post<RoutingResponseDTO>("/network/optimize", payload, {
    timeout: 60_000,
  });
  return response.data;
}

export async function compareAlgorithms(
  event: EventOption,
  beltCode?: string,
  warehouse?: WarehouseSelection,
): Promise<CompareResponseDTO> {
  const response = await api.post<CompareResponseDTO>(
    "/network/compare",
    {
      event: EVENT_LABELS[event],
      beltCode,
      warehouseName: warehouse?.name,
      warehouseLat: warehouse?.lat ?? null,
      warehouseLng: warehouse?.lng ?? null,
    },
    { timeout: 60_000 },
  );
  return response.data;
}

export async function fetchHistory(limit = 20): Promise<RoutingHistoryDTO[]> {
  const response = await api.get<RoutingHistoryDTO[]>("/network/history", {
    params: { limit },
  });
  return response.data;
}

export async function fetchHistorySummary(): Promise<HistorySummaryDTO> {
  const response = await api.get<HistorySummaryDTO>("/network/history/summary");
  return response.data;
}

export async function fetchBelts(): Promise<BeltDescriptorDTO[]> {
  const response = await api.get<BeltDescriptorDTO[]>("/admin/belts");
  return response.data;
}

export async function exportHistoryCsv(limit = 100): Promise<Blob> {
  const response = await api.get("/network/history/export.csv", {
    params: { limit },
    responseType: "blob",
  });
  return response.data as Blob;
}

export async function seedBelt(beltCode: string): Promise<AdminActionResponseDTO> {
  const response = await api.post<AdminActionResponseDTO>(`/admin/seed/${beltCode}`);
  return response.data;
}

export async function resetAndSeedBelt(beltCode: string): Promise<AdminActionResponseDTO> {
  const response = await api.post<AdminActionResponseDTO>(`/admin/reset-and-seed/${beltCode}`);
  return response.data;
}

export async function seedAllBelts(): Promise<AdminActionResponseDTO> {
  const response = await api.post<AdminActionResponseDTO>("/admin/seed-all");
  return response.data;
}

export async function resetAndSeedAllBelts(): Promise<AdminActionResponseDTO> {
  const response = await api.post<AdminActionResponseDTO>("/admin/reset-and-seed-all");
  return response.data;
}

export async function fetchAlertsStatus(): Promise<AlertsStatusDTO> {
  const response = await api.get<AlertsStatusDTO>("/admin/alerts/status");
  return response.data;
}
