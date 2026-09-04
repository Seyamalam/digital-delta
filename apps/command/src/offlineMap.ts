import { layers, namedFlavor } from "@protomaps/basemaps";
import type { StyleSpecification } from "maplibre-gl";
import routeGeometry from "./data/sylhet_route_geometry.json";

type Coordinate = [number, number];
export const offlineMapRevision = "f45649f195b99106";
type MissionProperties = {
  id: string;
  kind: "route" | "node" | "risk";
  transport?: "road" | "water" | "air";
  active?: boolean;
  failed?: boolean;
  simulated: boolean;
  edgeId?: string;
  geometrySource?: string;
};
export type MissionFeature = {
  type: "Feature";
  properties: MissionProperties;
  geometry: { type: "LineString"; coordinates: Coordinate[] } | { type: "Point"; coordinates: Coordinate };
};
export type MissionFeatureCollection = {
  type: "FeatureCollection";
  features: MissionFeature[];
};

export const sylhetNodes: Record<string, Coordinate> = {
  N1: [91.8687, 24.8949],
  N2: [91.8668, 24.9632],
  N3: [91.4073, 25.0658],
  N4: [91.7554, 25.0715],
  N5: [92.2611, 24.9945],
  N6: [91.4169, 24.3840],
  R3: [91.7000, 25.0200],
  N7: [91.6800, 25.1200],
};

export const missionBounds: [Coordinate, Coordinate] = [[91.30, 24.30], [92.36, 25.19]];

type RouteGeometryFeature = {
  type: "Feature";
  properties: {
    id: string;
    source: string;
    target: string;
    transport: "road" | "water" | "air";
    simulated: boolean;
    geometry_source: string;
  };
  geometry: { type: "LineString"; coordinates: Coordinate[] };
};

const routeFeatures = routeGeometry.features.map((feature): RouteGeometryFeature => {
  const transport = feature.properties.transport;
  if (transport !== "road" && transport !== "water" && transport !== "air") {
    throw new Error(`Unsupported transport geometry ${transport}`);
  }
  const coordinates = feature.geometry.coordinates.map((point) => {
    if (point.length !== 2 || !point.every(Number.isFinite)) throw new Error(`Invalid coordinates for ${feature.properties.id}`);
    return [point[0], point[1]] as Coordinate;
  });
  if (coordinates.length < 2) throw new Error(`Route geometry ${feature.properties.id} is too short`);
  return {
    type: "Feature",
    properties: { ...feature.properties, transport },
    geometry: { type: "LineString", coordinates },
  };
});

export const routeGeometryMetadata = routeGeometry.metadata;

export type MissionMapState = { edgeIds?: string[]; failedEdgeIds?: string[]; edgeRisks?: Record<string, number>; rendezvous?: { candidateId?: string; longitudeDegrees?: number; latitudeDegrees?: number } };
export function buildMissionGeoJson(useWaterRoute: boolean, showRisk: boolean, simulated = true, state: MissionMapState = {}): MissionFeatureCollection {
  const activeIds = new Set(state.edgeIds ?? (useWaterRoute ? ["E6", "E7"] : ["E1", "E3"]));
  const failedIds = new Set(state.failedEdgeIds ?? []);
  const features: MissionFeature[] = routeFeatures.map((feature) => {
    const { id, transport, geometry_source: geometrySource } = feature.properties;
    return {
      type: "Feature",
      properties: {
        id,
        kind: "route",
        transport,
        active: activeIds.has(id),
        failed: failedIds.has(id),
        simulated: simulated || feature.properties.simulated,
        geometrySource,
      },
      geometry: feature.geometry,
    };
  });
  for (const [id, coordinate] of Object.entries(sylhetNodes)) {
    if (id === "R3" && state.rendezvous) continue;
    features.push({
      type: "Feature",
      properties: { id, kind: "node", simulated },
      geometry: { type: "Point", coordinates: coordinate },
    });
  }
  const rendezvous = state.rendezvous;
  if (rendezvous && Number.isFinite(rendezvous.longitudeDegrees) && Number.isFinite(rendezvous.latitudeDegrees)) {
    features.push({ type: "Feature", properties: { id: rendezvous.candidateId ?? "rendezvous", kind: "node", simulated }, geometry: { type: "Point", coordinates: [rendezvous.longitudeDegrees!, rendezvous.latitudeDegrees!] } });
  }
  for (const [edgeId, probability] of Object.entries(state.edgeRisks ?? (showRisk ? { E3: 0.973 } : {}))) {
    if (!Number.isFinite(probability) || probability <= 0 || probability > 1) continue;
    const riskyEdge = routeFeatures.find((feature) => feature.properties.id === edgeId);
    if (!riskyEdge) continue;
    features.push({
      type: "Feature",
      properties: { id: `risk-${edgeId}`, kind: "risk", edgeId, simulated },
      geometry: { type: "Point", coordinates: pointAlongLine(riskyEdge.geometry.coordinates, 0.5) },
    });
  }
  return { type: "FeatureCollection", features };
}

export function createOfflineStyle(archiveUrl: string, missionData: MissionFeatureCollection): StyleSpecification {
  const baseLayers = layers("protomaps", namedFlavor("light"));
  return {
    version: 8,
    sources: {
      protomaps: {
        type: "vector",
        url: `pmtiles://${archiveUrl}`,
        attribution: '<a href="https://www.openstreetmap.org/copyright">© OpenStreetMap contributors</a>',
      },
      mission: { type: "geojson", data: missionData },
    },
    layers: [
      ...baseLayers,
      {
        id: "mission-routes",
        type: "line",
        source: "mission",
        filter: ["==", ["get", "kind"], "route"],
        paint: {
          "line-color": [
            "case",
            ["==", ["get", "failed"], true], "#ef5f5c",
            ["==", ["get", "transport"], "water"], "#3d8fb7",
            ["==", ["get", "transport"], "air"], "#087681",
            "#596865",
          ],
          "line-width": ["case", ["==", ["get", "active"], true], 6, 3],
          "line-opacity": ["case", ["==", ["get", "active"], true], 0.95, 0.22],
          "line-dasharray": ["case", ["==", ["get", "transport"], "air"], ["literal", [2, 2]], ["literal", [1, 0]]],
        },
      },
      {
        id: "mission-risk",
        type: "circle",
        source: "mission",
        filter: ["==", ["get", "kind"], "risk"],
        paint: {
          "circle-radius": 22,
          "circle-color": "rgba(239,95,92,0.2)",
          "circle-stroke-color": "#ef5f5c",
          "circle-stroke-width": 3,
        },
      },
      {
        id: "mission-nodes",
        type: "circle",
        source: "mission",
        filter: ["==", ["get", "kind"], "node"],
        paint: {
          "circle-radius": 5,
          "circle-color": "#073940",
          "circle-stroke-color": "#f7f5ef",
          "circle-stroke-width": 2,
        },
      },
    ],
  };
}

function pointAlongLine(line: Coordinate[], progress: number): Coordinate {
  const lengths = line.slice(1).map((point, index) => distance(line[index], point));
  const target = lengths.reduce((sum, length) => sum + length, 0) * progress;
  let travelled = 0;
  for (let index = 0; index < lengths.length; index += 1) {
    const next = travelled + lengths[index];
    if (next >= target) {
      const amount = lengths[index] === 0 ? 0 : (target - travelled) / lengths[index];
      return [
        line[index][0] + (line[index + 1][0] - line[index][0]) * amount,
        line[index][1] + (line[index + 1][1] - line[index][1]) * amount,
      ];
    }
    travelled = next;
  }
  return line.at(-1) ?? line[0];
}

function distance(left: Coordinate, right: Coordinate): number {
  const latitude = (left[1] + right[1]) * Math.PI / 360;
  const dx = (left[0] - right[0]) * Math.cos(latitude);
  const dy = left[1] - right[1];
  return Math.hypot(dx, dy);
}
