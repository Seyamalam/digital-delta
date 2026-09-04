import { describe, expect, it } from "vitest";
import { buildMissionGeoJson, createOfflineStyle, missionBounds, routeGeometryMetadata } from "./offlineMap";

describe("offline geographic map", () => {
  it("uses only the bundled PMTiles archive and keeps OSM attribution", () => {
    const style = createOfflineStyle("http://127.0.0.1:5173/maps/sylhet.pmtiles", buildMissionGeoJson(false, false));
    const encoded = JSON.stringify(style);

    expect(encoded).toContain("pmtiles://http://127.0.0.1:5173/maps/sylhet.pmtiles");
    expect(encoded).toContain("OpenStreetMap contributors");
    expect(encoded).not.toContain("tile.openstreetmap.org");
    expect(encoded).not.toContain("protomaps.github.io/basemaps-assets");
  });

  it("uses committed OSM-following geometry for road and water routes", () => {
    const data = buildMissionGeoJson(true, true);
    const road = data.features.find((feature) => feature.properties.id === "E3");
    const water = data.features.find((feature) => feature.properties.id === "E6");
    const air = data.features.find((feature) => feature.properties.id === "A2");
    const risk = data.features.find((feature) => feature.properties.kind === "risk");

    expect(road?.geometry.type).toBe("LineString");
    expect(road?.geometry.coordinates.length).toBeGreaterThan(2);
    expect(road?.geometry.coordinates[0]).toEqual([91.8668, 24.9632]);
    expect(road?.geometry.coordinates.at(-1)).toEqual([91.7554, 25.0715]);
    expect(road?.properties.geometrySource).toBe("openstreetmap-osrm");

    expect(water?.geometry.type).toBe("LineString");
    expect(water?.geometry.coordinates.length).toBeGreaterThan(2);
    expect(water?.geometry.coordinates[0]).toEqual([91.8687, 24.8949]);
    expect(water?.geometry.coordinates.at(-1)).toEqual([91.4073, 25.0658]);
    expect(water?.properties).toMatchObject({
      transport: "water",
      active: true,
      simulated: true,
      geometrySource: "openstreetmap-offline-waterway",
    });

    expect(air?.geometry.coordinates).toHaveLength(2);
    expect(air?.properties).toMatchObject({ simulated: true, geometrySource: "simulated-direct-airway" });
    expect(risk?.properties).toMatchObject({ edgeId: "E3", simulated: true });
    expect(risk?.geometry.coordinates).not.toEqual([
      (91.8668 + 91.7554) / 2,
      (24.9632 + 25.0715) / 2,
    ]);
    expect(routeGeometryMetadata.attribution).toContain("OpenStreetMap contributors");
  });

  it("defines a viewport that contains every mission node", () => {
    const [[west, south], [east, north]] = missionBounds;
    const data = buildMissionGeoJson(false, false);
    const points = data.features.filter((feature) => feature.geometry.type === "Point");

    for (const feature of points) {
      const [longitude, latitude] = feature.geometry.coordinates;
      expect(longitude).toBeGreaterThanOrEqual(west);
      expect(longitude).toBeLessThanOrEqual(east);
      expect(latitude).toBeGreaterThanOrEqual(south);
      expect(latitude).toBeLessThanOrEqual(north);
    }
  });
});
