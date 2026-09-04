import { describe, expect, it } from "vitest";
import { buildMissionGeoJson, createOfflineStyle, missionBounds } from "./offlineMap";

describe("offline geographic map", () => {
  it("uses only the bundled PMTiles archive and keeps OSM attribution", () => {
    const style = createOfflineStyle("http://127.0.0.1:5173/maps/sylhet.pmtiles", buildMissionGeoJson(false, false));
    const encoded = JSON.stringify(style);

    expect(encoded).toContain("pmtiles://http://127.0.0.1:5173/maps/sylhet.pmtiles");
    expect(encoded).toContain("OpenStreetMap contributors");
    expect(encoded).not.toContain("tile.openstreetmap.org");
    expect(encoded).not.toContain("protomaps.github.io/basemaps-assets");
  });

  it("places the supplied Sylhet nodes at geographic coordinates and labels simulated overlays", () => {
    const data = buildMissionGeoJson(true, true);
    const route = data.features.find((feature) => feature.properties.id === "E6");
    const risk = data.features.find((feature) => feature.properties.kind === "risk");

    expect(route?.geometry.coordinates).toEqual([[91.8687, 24.8949], [91.4073, 25.0658]]);
    expect(route?.properties).toMatchObject({ transport: "water", active: true, simulated: true });
    expect(risk?.properties).toMatchObject({ edgeId: "E3", simulated: true });
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
