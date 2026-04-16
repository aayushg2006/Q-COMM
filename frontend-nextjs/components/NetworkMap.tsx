"use client";

import { motion } from "framer-motion";
import { Activity, MapPinned, Navigation } from "lucide-react";
import { useMemo } from "react";
import { GoogleMap, MarkerF, PolylineF, useJsApiLoader } from "@react-google-maps/api";
import type { DarkStore, DispatchStepDTO, RouteEdge, WarehouseDTO } from "@/types/network";

interface NetworkMapProps {
  stores: DarkStore[];
  networkEdges: RouteEdge[];
  mstEdges: RouteEdge[];
  dispatchPlan: DispatchStepDTO[];
  warehouse: WarehouseDTO | null;
  warehousePinPicking?: boolean;
  onWarehousePinSelect?: (point: { lat: number; lng: number }) => void;
}

interface GoogleMapCanvasProps {
  apiKey: string;
  center: { lat: number; lng: number };
  mapBounds: Array<{ lat: number; lng: number }>;
  stores: DarkStore[];
  drawableNetworkEdges: Array<{ key: string; path: Array<{ lat: number; lng: number }> }>;
  drawableMstEdges: Array<{ key: string; path: Array<{ lat: number; lng: number }>; risk: boolean; weight: number }>;
  dispatchPolylines: Array<{
    key: string;
    sequence: number;
    path: Array<{ lat: number; lng: number }>;
    risk: boolean;
  }>;
  warehouse: WarehouseDTO | null;
  warehousePinPicking: boolean;
  onWarehousePinSelect?: (point: { lat: number; lng: number }) => void;
}

const MAP_CONTAINER_STYLE = {
  width: "100%",
  height: "60vh",
};

const DARK_MAP_STYLE = [
  { elementType: "geometry", stylers: [{ color: "#0b1220" }] },
  { elementType: "labels.text.stroke", stylers: [{ color: "#0b1220" }] },
  { elementType: "labels.text.fill", stylers: [{ color: "#95a0b8" }] },
  { featureType: "poi", elementType: "labels.text.fill", stylers: [{ color: "#7486a6" }] },
  { featureType: "road", elementType: "geometry", stylers: [{ color: "#1d293f" }] },
  { featureType: "road.arterial", elementType: "geometry", stylers: [{ color: "#243656" }] },
  { featureType: "road.highway", elementType: "geometry", stylers: [{ color: "#1f4d78" }] },
  { featureType: "water", elementType: "geometry", stylers: [{ color: "#081327" }] },
];

const DEFAULT_CENTER = { lat: 19.076, lng: 72.8777 };

function GoogleMapCanvas({
  apiKey,
  center,
  mapBounds,
  stores,
  drawableNetworkEdges,
  drawableMstEdges,
  dispatchPolylines,
  warehouse,
  warehousePinPicking,
  onWarehousePinSelect,
}: GoogleMapCanvasProps) {
  const { isLoaded, loadError } = useJsApiLoader({
    id: "qcomm-google-map",
    googleMapsApiKey: apiKey,
  });

  if (loadError) {
    return (
      <div className="flex h-[60vh] items-center justify-center rounded-xl border border-red-400/40 bg-red-950/25 px-6 text-center text-sm text-red-200">
        Failed to load Google Maps. Check key restrictions and internet connectivity.
      </div>
    );
  }

  if (!isLoaded) {
    return (
      <div className="flex h-[60vh] items-center justify-center rounded-xl border border-slate-700/80 bg-slate-900/70 text-sm text-slate-300">
        Loading map surface...
      </div>
    );
  }

  return (
    <div className="h-[60vh] overflow-hidden rounded-xl border border-slate-700/70">
      <GoogleMap
        mapContainerStyle={MAP_CONTAINER_STYLE}
        center={center}
        zoom={12}
        options={{
          disableDefaultUI: true,
          zoomControl: true,
          mapTypeControl: false,
          fullscreenControl: false,
          clickableIcons: false,
          styles: DARK_MAP_STYLE as google.maps.MapTypeStyle[],
        }}
        onLoad={(map) => {
          if (mapBounds.length === 0) {
            return;
          }
          const bounds = new google.maps.LatLngBounds();
          for (const point of mapBounds) {
            bounds.extend(point);
          }
          map.fitBounds(bounds, 72);
          if (mapBounds.length === 1) {
            map.setZoom(13);
          }
        }}
        onClick={(event) => {
          if (!warehousePinPicking || !onWarehousePinSelect) {
            return;
          }
          const latLng = event.latLng;
          if (!latLng) {
            return;
          }
          onWarehousePinSelect({ lat: latLng.lat(), lng: latLng.lng() });
        }}
      >
        {drawableNetworkEdges.map((edge) => (
          <PolylineF
            key={edge.key}
            path={edge.path}
            options={{
              strokeColor: "#1d4ed8",
              strokeOpacity: 0.35,
              strokeWeight: 2,
              icons: [
                {
                  icon: {
                    path: "M 0,-1 0,1",
                    strokeOpacity: 1,
                    scale: 2,
                  },
                  offset: "0",
                  repeat: "18px",
                },
              ],
            }}
          />
        ))}

        {drawableMstEdges.map((edge) => (
          <PolylineF
            key={edge.key}
            path={edge.path}
            options={{
              strokeColor: edge.risk ? "#fb923c" : "#22d3ee",
              strokeOpacity: 0.95,
              strokeWeight: edge.risk ? 5 : 4,
            }}
          />
        ))}

        {dispatchPolylines.map((step) => (
          <PolylineF
            key={step.key}
            path={step.path}
            options={{
              strokeColor: step.risk ? "#ef4444" : "#10b981",
              strokeOpacity: 0.95,
              strokeWeight: 3,
              geodesic: true,
            }}
          />
        ))}

        {stores.map((store, index) => (
          <MarkerF
            key={store.id}
            position={{ lat: store.lat, lng: store.lng }}
            title={`${store.name} (${store.lat.toFixed(4)}, ${store.lng.toFixed(4)})`}
            label={{
              text: String(index + 1),
              color: "#e2e8f0",
              fontSize: "11px",
              fontWeight: "700",
            }}
          />
        ))}

        {warehouse && (
          <MarkerF
            position={{ lat: warehouse.lat, lng: warehouse.lng }}
            title={`${warehouse.name} (${warehouse.lat.toFixed(4)}, ${warehouse.lng.toFixed(4)})`}
            icon={{
              url: "https://maps.google.com/mapfiles/ms/icons/green-dot.png",
            }}
            label={{
              text: "W",
              color: "#052e16",
              fontSize: "11px",
              fontWeight: "700",
            }}
          />
        )}
      </GoogleMap>
    </div>
  );
}

export default function NetworkMap({
  stores,
  networkEdges,
  mstEdges,
  dispatchPlan,
  warehouse,
  warehousePinPicking = false,
  onWarehousePinSelect,
}: NetworkMapProps) {
  const apiKey = (process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY ?? "").trim();

  const storeById = useMemo(() => {
    const map = new Map<number, DarkStore>();
    for (const store of stores) {
      map.set(store.id, store);
    }
    return map;
  }, [stores]);

  const center = useMemo(() => {
    if (warehouse) {
      return { lat: warehouse.lat, lng: warehouse.lng };
    }
    if (stores.length === 0) {
      return DEFAULT_CENTER;
    }
    const avgLat = stores.reduce((acc, store) => acc + store.lat, 0) / stores.length;
    const avgLng = stores.reduce((acc, store) => acc + store.lng, 0) / stores.length;
    return { lat: avgLat, lng: avgLng };
  }, [stores, warehouse]);

  const drawableNetworkEdges = useMemo(
    () =>
      networkEdges
        .map((edge, index) => {
          const source = storeById.get(edge.source);
          const target = storeById.get(edge.target);
          if (!source || !target) {
            return null;
          }
          return {
            key: `network-${edge.source}-${edge.target}-${edge.weight}-${index}`,
            path: [
              { lat: source.lat, lng: source.lng },
              { lat: target.lat, lng: target.lng },
            ],
          };
        })
        .filter((edge): edge is NonNullable<typeof edge> => !!edge),
    [networkEdges, storeById],
  );

  const drawableMstEdges = useMemo(
    () =>
      mstEdges
        .map((edge, index) => {
          const source = storeById.get(edge.source);
          const target = storeById.get(edge.target);
          if (!source || !target) {
            return null;
          }
          return {
            key: `mst-${edge.source}-${edge.target}-${edge.weight}-${index}`,
            path: [
              { lat: source.lat, lng: source.lng },
              { lat: target.lat, lng: target.lng },
            ],
            risk: edge.isRiskFlagged && !!edge.riskReason,
            weight: edge.weight,
          };
        })
        .filter((edge): edge is NonNullable<typeof edge> => !!edge),
    [mstEdges, storeById],
  );

  const dispatchPolylines = useMemo(
    () =>
      dispatchPlan.map((step) => ({
        key: `dispatch-${step.sequence}-${step.toStoreId}`,
        sequence: step.sequence,
        path: [
          { lat: step.fromLat, lng: step.fromLng },
          { lat: step.toLat, lng: step.toLng },
        ],
        risk: step.isRiskFlagged && !!step.riskReason,
      })),
    [dispatchPlan],
  );

  const mapBounds = useMemo(
    () => [
      ...stores.map((store) => ({ lat: store.lat, lng: store.lng })),
      ...(warehouse ? [{ lat: warehouse.lat, lng: warehouse.lng }] : []),
    ],
    [stores, warehouse],
  );

  return (
    <section className="h-full rounded-2xl border border-blue-500/20 bg-slate-950/70 p-4 shadow-[0_0_45px_-22px_rgba(59,130,246,0.8)] backdrop-blur-md">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-sm font-semibold tracking-[0.24em] text-blue-300 uppercase">
          <MapPinned className="h-4 w-4" />
          Network Canvas
        </h2>
        <div className="flex items-center gap-4 text-xs text-slate-400">
          <span className="flex items-center gap-2">
            <Activity className="h-3.5 w-3.5 text-cyan-300" />
            Live MST Route Overlay
          </span>
          <span className="flex items-center gap-1 text-emerald-200">
            <Navigation className="h-3.5 w-3.5" />
            Dispatch steps: {dispatchPolylines.length}
          </span>
          <span className="text-cyan-200">MST routes: {drawableMstEdges.length}</span>
        </div>
      </div>

      {warehousePinPicking && (
        <div className="mb-2 rounded-lg border border-fuchsia-400/45 bg-fuchsia-900/20 px-3 py-2 text-xs text-fuchsia-200">
          Pin mode enabled. Click anywhere on the map to set warehouse coordinates.
        </div>
      )}

      {stores.length === 0 ? (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex h-[60vh] items-center justify-center rounded-xl border border-slate-700/80 bg-slate-900/70 text-sm text-slate-400"
        >
          Waiting for dark-store coordinates from backend.
        </motion.div>
      ) : !apiKey ? (
        <div className="flex h-[60vh] items-center justify-center rounded-xl border border-amber-400/40 bg-amber-950/25 px-6 text-center text-sm text-amber-200">
          Google Maps key is missing. Add <code className="mx-1 rounded bg-slate-900 px-1 py-0.5">NEXT_PUBLIC_GOOGLE_MAPS_API_KEY</code>
          in <code className="ml-1 rounded bg-slate-900 px-1 py-0.5">frontend-nextjs/.env.local</code>.
        </div>
      ) : (
        <GoogleMapCanvas
          apiKey={apiKey}
          center={center}
          mapBounds={mapBounds}
          stores={stores}
          drawableNetworkEdges={drawableNetworkEdges}
          drawableMstEdges={drawableMstEdges}
          dispatchPolylines={dispatchPolylines}
          warehouse={warehouse}
          warehousePinPicking={warehousePinPicking}
          onWarehousePinSelect={onWarehousePinSelect}
        />
      )}
    </section>
  );
}
