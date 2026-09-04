import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoDir = resolve(scriptDir, "../../..");
const scenarioPath = resolve(repoDir, "packages/scenario/sylhet_map.json");
const basemapPath = resolve(repoDir, "apps/field-android/app/src/main/assets/maps/sylhet_osm_basemap.geojson");
const outputPath = resolve(repoDir, "packages/scenario/sylhet_route_geometry.json");
const refreshRoads = process.argv.includes("--refresh-roads");

class MinHeap {
  values = [];
  get size() { return this.values.length; }
  push(value) {
    this.values.push(value);
    let index = this.values.length - 1;
    while (index > 0) {
      const parent = Math.floor((index - 1) / 2);
      if (this.values[parent].distance <= value.distance) break;
      this.values[index] = this.values[parent];
      index = parent;
    }
    this.values[index] = value;
  }
  pop() {
    const first = this.values[0];
    const last = this.values.pop();
    if (this.values.length && last) {
      let index = 0;
      while (true) {
        const left = index * 2 + 1;
        const right = left + 1;
        if (left >= this.values.length) break;
        const child = right < this.values.length && this.values[right].distance < this.values[left].distance ? right : left;
        if (this.values[child].distance >= last.distance) break;
        this.values[index] = this.values[child];
        index = child;
      }
      this.values[index] = last;
    }
    return first;
  }
}

const scenarioBytes = await readFile(scenarioPath);
const basemapBytes = await readFile(basemapPath);
const scenario = JSON.parse(scenarioBytes);
const basemap = JSON.parse(basemapBytes);
const previous = await readFile(outputPath, "utf8").then(JSON.parse).catch(() => null);
const previousById = new Map((previous?.features ?? []).map((feature) => [feature.properties.id, feature]));
const nodes = new Map(scenario.nodes.map((node) => [node.id, [node.lng, node.lat]]));
const waterGraph = buildWaterGraph(basemap.features);
const features = [];

for (const edge of scenario.edges) {
  const source = requiredNode(edge.source);
  const target = requiredNode(edge.target);
  const transport = normalizeTransport(edge.type);
  let route;
  let provenance;

  if (transport === "road") {
    const cached = previousById.get(edge.id);
    if (!refreshRoads && cached?.properties.geometry_source === "openstreetmap-osrm") {
      route = cached.geometry.coordinates;
      provenance = pickRoadProvenance(cached.properties);
    } else {
      const exported = await fetchRoadRoute(source, target);
      route = withScenarioEndpoints(exported.coordinates, source, target);
      provenance = {
        geometry_source: "openstreetmap-osrm",
        snapped_start_m: round(exported.snappedStartMetres),
        snapped_end_m: round(exported.snappedEndMetres),
        routed_distance_m: round(exported.distanceMetres),
      };
    }
  } else if (transport === "water") {
    const exported = waterGraph.route(source, target);
    route = withScenarioEndpoints(exported.coordinates, source, target);
    provenance = {
      geometry_source: "openstreetmap-offline-waterway",
      snapped_start_m: round(exported.snappedStartMetres),
      snapped_end_m: round(exported.snappedEndMetres),
      routed_distance_m: round(polylineLengthMetres(route)),
    };
  } else {
    route = [source, target];
    provenance = {
      geometry_source: "simulated-direct-airway",
      snapped_start_m: 0,
      snapped_end_m: 0,
      routed_distance_m: round(distanceMetres(source, target)),
    };
  }

  features.push({
    type: "Feature",
    properties: {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      transport,
      simulated: transport === "air",
      ...provenance,
    },
    geometry: {
      type: "LineString",
      coordinates: simplifyLine(route, transport === "air" ? 0 : 12),
    },
  });
}

const output = {
  type: "FeatureCollection",
  metadata: {
    region: scenario.metadata.region,
    generated_on: "2026-09-04",
    scenario_sha256: sha256(scenarioBytes),
    basemap_sha256: sha256(basemapBytes),
    road_geometry: "OpenStreetMap road network routed once through the OSRM demo service, then committed for offline use.",
    water_geometry: "OpenStreetMap waterway centerlines extracted from the checksum-pinned offline field basemap.",
    endpoint_policy: "Each line starts and ends at the scenario node; short connector segments bridge nodes to the nearest routable OSM geometry.",
    attribution: "© OpenStreetMap contributors, ODbL 1.0",
  },
  features,
};

await writeFile(outputPath, `${JSON.stringify(output, null, 2)}\n`);
console.log(`Wrote ${features.length} route geometries to ${outputPath}`);
for (const feature of features) {
  console.log(`${feature.properties.id}: ${feature.geometry.coordinates.length} points, ${feature.properties.routed_distance_m} m, ${feature.properties.geometry_source}`);
}

function requiredNode(id) {
  const node = nodes.get(id);
  if (!node) throw new Error(`Missing scenario node ${id}`);
  return node;
}

function normalizeTransport(type) {
  if (type === "river") return "water";
  if (type === "airway") return "air";
  return type;
}

async function fetchRoadRoute(source, target) {
  const endpoint = new URL(`https://router.project-osrm.org/route/v1/driving/${source.join(",")};${target.join(",")}`);
  endpoint.searchParams.set("overview", "full");
  endpoint.searchParams.set("geometries", "geojson");
  endpoint.searchParams.set("steps", "false");
  const response = await fetch(endpoint, {
    headers: { "User-Agent": "DigitalDeltaFairPrototype/1.0 (https://github.com/Seyamalam/digital-delta)" },
  });
  if (!response.ok) throw new Error(`OSRM returned HTTP ${response.status}`);
  const payload = await response.json();
  if (payload.code !== "Ok" || !payload.routes?.[0]?.geometry?.coordinates) {
    throw new Error(`OSRM could not route ${source.join(",")} to ${target.join(",")}`);
  }
  return {
    coordinates: payload.routes[0].geometry.coordinates,
    distanceMetres: payload.routes[0].distance,
    snappedStartMetres: distanceMetres(source, payload.waypoints[0].location),
    snappedEndMetres: distanceMetres(target, payload.waypoints[1].location),
  };
}

function buildWaterGraph(features) {
  const adjacency = new Map();
  const points = new Map();
  for (const feature of features) {
    if (feature.properties?.source_layer !== "water") continue;
    for (const line of geometryLines(feature.geometry)) {
      for (let index = 1; index < line.length; index += 1) {
        const left = key(line[index - 1]);
        const right = key(line[index]);
        points.set(left, line[index - 1]);
        points.set(right, line[index]);
        connect(adjacency, left, right, distanceMetres(line[index - 1], line[index]));
      }
    }
  }

  const componentByPoint = new Map();
  const componentSizes = new Map();
  let component = 0;
  for (const start of adjacency.keys()) {
    if (componentByPoint.has(start)) continue;
    component += 1;
    const queue = [start];
    componentByPoint.set(start, component);
    let size = 0;
    while (queue.length) {
      const current = queue.pop();
      size += 1;
      for (const next of adjacency.get(current) ?? []) {
        if (!componentByPoint.has(next.id)) {
          componentByPoint.set(next.id, component);
          queue.push(next.id);
        }
      }
    }
    componentSizes.set(component, size);
  }

  return {
    route(source, target) {
      let best = null;
      const components = [...componentSizes.entries()].filter(([, size]) => size >= 20);
      for (const [componentId] of components) {
        const start = nearestPoint(source, componentId, points, componentByPoint);
        const end = nearestPoint(target, componentId, points, componentByPoint);
        const score = start.distance + end.distance;
        if (!best || score < best.score) best = { componentId, start, end, score };
      }
      if (!best) throw new Error("No connected OSM waterway component covers this route");
      const pathKeys = shortestPath(best.start.id, best.end.id, adjacency);
      if (!pathKeys.length) throw new Error("No connected OSM waterway path found");
      return {
        coordinates: pathKeys.map((id) => points.get(id)),
        snappedStartMetres: best.start.distance,
        snappedEndMetres: best.end.distance,
      };
    },
  };
}

function geometryLines(geometry) {
  if (geometry.type === "LineString") return [geometry.coordinates];
  if (geometry.type === "MultiLineString") return geometry.coordinates;
  return [];
}

function connect(adjacency, left, right, weight) {
  if (!adjacency.has(left)) adjacency.set(left, []);
  if (!adjacency.has(right)) adjacency.set(right, []);
  adjacency.get(left).push({ id: right, weight });
  adjacency.get(right).push({ id: left, weight });
}

function nearestPoint(target, componentId, points, componentByPoint) {
  let best = null;
  for (const [id, point] of points) {
    if (componentByPoint.get(id) !== componentId) continue;
    const distance = distanceMetres(target, point);
    if (!best || distance < best.distance) best = { id, distance };
  }
  return best;
}

function shortestPath(start, target, adjacency) {
  const distances = new Map([[start, 0]]);
  const previous = new Map();
  const queue = new MinHeap();
  queue.push({ id: start, distance: 0 });
  while (queue.size) {
    const current = queue.pop();
    if (current.distance !== distances.get(current.id)) continue;
    if (current.id === target) break;
    for (const edge of adjacency.get(current.id) ?? []) {
      const candidate = current.distance + edge.weight;
      if (candidate < (distances.get(edge.id) ?? Number.POSITIVE_INFINITY)) {
        distances.set(edge.id, candidate);
        previous.set(edge.id, current.id);
        queue.push({ id: edge.id, distance: candidate });
      }
    }
  }
  if (!distances.has(target)) return [];
  const path = [target];
  while (path[0] !== start) path.unshift(previous.get(path[0]));
  return path;
}

function withScenarioEndpoints(coordinates, source, target) {
  const result = coordinates.map(([longitude, latitude]) => [round(longitude, 6), round(latitude, 6)]);
  if (distanceMetres(source, result[0]) > 1) result.unshift(source);
  if (distanceMetres(target, result.at(-1)) > 1) result.push(target);
  return result;
}

function simplifyLine(points, toleranceMetres) {
  if (points.length <= 2 || toleranceMetres <= 0) return points;
  const first = points[0];
  const last = points.at(-1);
  let maxDistance = 0;
  let split = 0;
  for (let index = 1; index < points.length - 1; index += 1) {
    const distance = pointSegmentDistanceMetres(points[index], first, last);
    if (distance > maxDistance) {
      maxDistance = distance;
      split = index;
    }
  }
  if (maxDistance <= toleranceMetres) return [first, last];
  const left = simplifyLine(points.slice(0, split + 1), toleranceMetres);
  const right = simplifyLine(points.slice(split), toleranceMetres);
  return [...left.slice(0, -1), ...right];
}

function pointSegmentDistanceMetres(point, start, end) {
  const latitude = (start[1] + end[1]) / 2;
  const scaleX = 111_320 * Math.cos(latitude * Math.PI / 180);
  const scaleY = 110_540;
  const px = (point[0] - start[0]) * scaleX;
  const py = (point[1] - start[1]) * scaleY;
  const ex = (end[0] - start[0]) * scaleX;
  const ey = (end[1] - start[1]) * scaleY;
  const lengthSquared = ex * ex + ey * ey;
  const amount = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, (px * ex + py * ey) / lengthSquared));
  return Math.hypot(px - amount * ex, py - amount * ey);
}

function polylineLengthMetres(points) {
  return points.slice(1).reduce((total, point, index) => total + distanceMetres(points[index], point), 0);
}

function distanceMetres(left, right) {
  const latitude = (left[1] + right[1]) * Math.PI / 360;
  const dx = (left[0] - right[0]) * 111_320 * Math.cos(latitude);
  const dy = (left[1] - right[1]) * 110_540;
  return Math.hypot(dx, dy);
}

function key(point) {
  return `${point[0].toFixed(6)},${point[1].toFixed(6)}`;
}

function pickRoadProvenance(properties) {
  return {
    geometry_source: properties.geometry_source,
    snapped_start_m: properties.snapped_start_m,
    snapped_end_m: properties.snapped_end_m,
    routed_distance_m: properties.routed_distance_m,
  };
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function round(value, decimals = 0) {
  const scale = 10 ** decimals;
  return Math.round(value * scale) / scale;
}
