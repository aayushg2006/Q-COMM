export interface DarkStore {
  id: number;
  name: string;
  lat: number;
  lng: number;
}

export interface RouteEdge {
  source: number;
  target: number;
  weight: number;
  isRiskFlagged: boolean;
  riskReason?: string | null;
}

export interface WarehouseDTO {
  name: string;
  lat: number;
  lng: number;
  custom: boolean;
}

export interface DispatchStepDTO {
  sequence: number;
  fromNodeName: string;
  fromLat: number;
  fromLng: number;
  toStoreId: number;
  toStoreName: string;
  toLat: number;
  toLng: number;
  legCost: number;
  cumulativeCost: number;
  isRiskFlagged: boolean;
  riskReason?: string | null;
  fromWarehouse: boolean;
}

export interface RoutingResponseDTO {
  mstEdges: RouteEdge[];
  totalCost: number;
  executionTimeMs: number;
  algorithmUsed: string;
  warehouse: WarehouseDTO;
  dispatchPlan: DispatchStepDTO[];
  dispatchCost: number;
}

export interface AlgorithmComparisonDetailsDTO {
  sameCost: boolean;
  sameEdgeSet: boolean;
  costDelta: number;
  executionTimeDeltaMs: number;
  primEdgeCount: number;
  kruskalEdgeCount: number;
  sharedEdgeCount: number;
  edgeOverlapPercent: number;
  primDispatchSteps: number;
  kruskalDispatchSteps: number;
  primOnlyEdges: RouteEdge[];
  kruskalOnlyEdges: RouteEdge[];
}

export interface CompareResponseDTO {
  prim: RoutingResponseDTO;
  kruskal: RoutingResponseDTO;
  recommendedAlgorithm: string;
  reason: string;
  details: AlgorithmComparisonDetailsDTO;
}

export interface RoutingHistoryDTO {
  id: number;
  timestamp: string;
  algorithmUsed: string;
  executionTimeMs: number;
  totalCost: number;
}

export interface AlgorithmAnalyticsDTO {
  algorithm: string;
  runCount: number;
  avgExecutionTimeMs: number;
  avgTotalCost: number;
}

export interface HistorySummaryDTO {
  totalRuns: number;
  latestExecutionTimeMs: number | null;
  latestTotalCost: number | null;
  algorithmBreakdown: AlgorithmAnalyticsDTO[];
}

export interface AuthResponseDTO {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  username: string;
  role: string;
}

export interface AdminActionResponseDTO {
  message: string;
  activeStoreCount: number;
  totalEdgeCount: number;
}

export interface AlertsStatusDTO {
  status: string;
  message: string;
}

export interface BeltDescriptorDTO {
  code: string;
  label: string;
  warehouseName: string;
  warehouseLat: number;
  warehouseLng: number;
}

export interface WarehouseSelection {
  name?: string;
  lat?: number | null;
  lng?: number | null;
}

export type AlgorithmOption = "prim" | "kruskal";

export type EventOption =
  | "clear_traffic"
  | "heavy_rain"
  | "peak_hour_congestion"
  | "roadwork_incident";
