import { createHash } from "node:crypto";
import { open, readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { VectorTile } from "@mapbox/vector-tile";
import { PbfReader } from "pbf";
import { PMTiles } from "pmtiles";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const commandDir = resolve(scriptDir, "..");
const repoDir = resolve(commandDir, "../..");
const archivePath = resolve(commandDir, "public/maps/sylhet.pmtiles");
const outputPath = resolve(
  repoDir,
  "apps/field-android/app/src/main/assets/maps/sylhet_osm_basemap.geojson",
);

// A single reviewed zoom is intentionally used: it gives the field map genuine
// geographic context without loading a multi-resolution tile pyramid into RAM.
const zoom = 10;
const bounds = { west: 91.30, south: 24.30, east: 92.36, north: 25.19 };
const retainedLayers = new Set(["boundaries", "earth", "landuse", "places", "roads", "water"]);
const retainedProperties = new Set([
  "kind",
  "kind_detail",
  "min_zoom",
  "name",
  "name:en",
  "ref",
  "sort_rank",
]);

class NodeFileSource {
  constructor(path) {
    this.path = path;
    this.handle = null;
  }

  getKey() {
    return this.path;
  }

  async getBytes(offset, length) {
    this.handle ??= await open(this.path, "r");
    const buffer = Buffer.alloc(length);
    const { bytesRead } = await this.handle.read(buffer, 0, length, offset);
    if (bytesRead !== length) throw new Error(`short PMTiles read: ${bytesRead}/${length}`);
    return { data: buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength) };
  }

  async close() {
    await this.handle?.close();
  }
}

const source = new NodeFileSource(archivePath);
const archive = new PMTiles(source);
const archiveBytes = await readFile(archivePath);
const archiveSha256 = createHash("sha256").update(archiveBytes).digest("hex");
const features = [];

try {
  const minX = longitudeToTile(bounds.west, zoom);
  const maxX = longitudeToTile(bounds.east, zoom);
  const minY = latitudeToTile(bounds.north, zoom);
  const maxY = latitudeToTile(bounds.south, zoom);

  for (let x = minX; x <= maxX; x += 1) {
    for (let y = minY; y <= maxY; y += 1) {
      const response = await archive.getZxy(zoom, x, y);
      if (!response) continue;
      const tile = new VectorTile(new PbfReader(new Uint8Array(response.data)));
      for (const layerName of Object.keys(tile.layers).sort()) {
        if (!retainedLayers.has(layerName)) continue;
        const layer = tile.layers[layerName];
        for (let index = 0; index < layer.length; index += 1) {
          const feature = layer.feature(index).toGeoJSON(x, y, zoom);
          if (!intersectsBounds(feature, bounds)) continue;
          const properties = { source_layer: layerName };
          for (const key of Object.keys(feature.properties ?? {}).sort()) {
            if (retainedProperties.has(key)) properties[key] = feature.properties[key];
          }
          features.push({ type: "Feature", properties, geometry: feature.geometry });
        }
      }
    }
  }
} finally {
  await source.close();
}

features.sort((left, right) => stableKey(left).localeCompare(stableKey(right)));
const collection = {
  type: "FeatureCollection",
  metadata: {
    source: "Protomaps Basemap derived from OpenStreetMap and Natural Earth",
    attribution: "© OpenStreetMap contributors",
    archive_sha256: archiveSha256,
    extraction_zoom: zoom,
    bounds: [bounds.west, bounds.south, bounds.east, bounds.north],
  },
  features,
};

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, `${JSON.stringify(collection)}\n`, "utf8");
const outputSha256 = createHash("sha256").update(await readFile(outputPath)).digest("hex");
console.log(`Exported ${features.length} OSM-derived features to ${outputPath}`);
console.log(`${outputSha256}  ${outputPath}`);

function longitudeToTile(longitude, z) {
  return Math.floor(((longitude + 180) / 360) * 2 ** z);
}

function latitudeToTile(latitude, z) {
  const radians = (latitude * Math.PI) / 180;
  return Math.floor(((1 - Math.asinh(Math.tan(radians)) / Math.PI) / 2) * 2 ** z);
}

function intersectsBounds(feature, target) {
  const coordinates = [];
  collectCoordinates(feature.geometry?.coordinates, coordinates);
  return coordinates.some(
    ([longitude, latitude]) =>
      longitude >= target.west &&
      longitude <= target.east &&
      latitude >= target.south &&
      latitude <= target.north,
  );
}

function collectCoordinates(value, output) {
  if (!Array.isArray(value)) return;
  if (value.length >= 2 && typeof value[0] === "number" && typeof value[1] === "number") {
    output.push(value);
    return;
  }
  for (const child of value) collectCoordinates(child, output);
}

function stableKey(feature) {
  return `${feature.properties.source_layer}|${feature.properties.kind ?? ""}|${feature.properties.name ?? ""}|${JSON.stringify(feature.geometry)}`;
}
