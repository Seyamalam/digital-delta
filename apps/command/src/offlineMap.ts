import { layers, namedFlavor } from "@protomaps/basemaps";
import type { StyleSpecification } from "maplibre-gl";

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

const edges: Array<[string, string, string, "road" | "water" | "air"]> = [
  ["E1", "N1", "N2", "road"],
  ["E3", "N2", "N4", "road"],
  ["E6", "N1", "N3", "water"],
  ["E7", "N3", "N4", "water"],
  ["A1", "R3", "N7", "air"],
];

export function buildMissionGeoJson(useWaterRoute: boolean, showRisk: boolean, simulated = true): MissionFeatureCollection {
  const features: MissionFeature[] = edges.map(([id, source, target, transport]) => ({
    type: "Feature",
    properties: {
      id,
      kind: "route",
      transport,
      active: transport === "road" ? !useWaterRoute : useWaterRoute,
      failed: id === "E3" && useWaterRoute,
      simulated,
    },
    geometry: { type: "LineString", coordinates: [sylhetNodes[source], sylhetNodes[target]] },
  }));
  for (const [id, coordinate] of Object.entries(sylhetNodes)) {
    features.push({
      type: "Feature",
      properties: { id, kind: "node", simulated },
      geometry: { type: "Point", coordinates: coordinate },
    });
  }
  if (showRisk) {
    features.push({
      type: "Feature",
      properties: { id: "risk-E3", kind: "risk", edgeId: "E3", simulated },
      geometry: { type: "Point", coordinates: midpoint(sylhetNodes.N2, sylhetNodes.N4) },
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

function midpoint(left: Coordinate, right: Coordinate): Coordinate {
  return [(left[0] + right[0]) / 2, (left[1] + right[1]) / 2];
}
