import { useEffect, useRef, useState } from "react";
import { addProtocol, AttributionControl, GeoJSONSource, Map as MapLibreMap, Marker, setWorkerUrl } from "maplibre-gl";
import { PMTiles, Protocol } from "pmtiles";
import { buildMissionGeoJson, createOfflineStyle, missionBounds, offlineMapRevision, sylhetNodes } from "./offlineMap";

type OfflineDeltaMapProps = {
  useWaterRoute: boolean;
  showRisk: boolean;
  simulated: boolean;
  language: "bn" | "en";
};

const protocol = new Protocol();
const registeredArchives = new Set<string>();
let protocolRegistered = false;

export function OfflineDeltaMap({ useWaterRoute, showRisk, simulated, language }: OfflineDeltaMapProps) {
  const host = useRef<HTMLDivElement>(null);
  const map = useRef<MapLibreMap | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "unavailable">("loading");

  useEffect(() => {
    if (!host.current || !hasWebGL()) {
      setStatus("unavailable");
      return;
    }
    const archive = new URL("maps/sylhet.pmtiles", document.baseURI);
    archive.searchParams.set("v", offlineMapRevision);
    const archiveUrl = archive.toString();
    setWorkerUrl(new URL("vendor/maplibre/maplibre-gl-worker.mjs", document.baseURI).toString());
    registerArchive(archiveUrl);
    const instance = new MapLibreMap({
      container: host.current,
      style: createOfflineStyle(archiveUrl, buildMissionGeoJson(useWaterRoute, showRisk, simulated)),
      bounds: missionBounds,
      fitBoundsOptions: { padding: 28 },
      minZoom: 6,
      maxZoom: 14,
      attributionControl: false,
      dragPan: false,
      dragRotate: false,
      scrollZoom: false,
      keyboard: false,
      pitchWithRotate: false,
      touchZoomRotate: false,
    });
    instance.addControl(new AttributionControl({ compact: true }), "bottom-right");
    instance.on("error", (event) => {
      console.warn("offline map failed to render", event.error);
      setStatus((current) => current === "ready" ? current : "unavailable");
    });
    instance.once("load", () => {
      instance.resize();
      instance.fitBounds(missionBounds, { padding: 28, duration: 0 });
      if (host.current) {
        const center = instance.getCenter();
        host.current.dataset.camera = `${center.lng.toFixed(4)},${center.lat.toFixed(4)}@${instance.getZoom().toFixed(2)}`;
      }
      setStatus("ready");
      for (const [id, coordinates] of Object.entries(sylhetNodes)) {
        const element = document.createElement("div");
        element.className = "geographic-node-label";
        element.textContent = id;
        element.setAttribute("aria-label", id);
        new Marker({ element, anchor: "bottom-left" }).setLngLat(coordinates).addTo(instance);
      }
    });
    map.current = instance;
    return () => {
      map.current = null;
      instance.remove();
    };
  }, []);

  useEffect(() => {
    const source = map.current?.getSource("mission");
    if (source instanceof GeoJSONSource) {
      source.setData(buildMissionGeoJson(useWaterRoute, showRisk, simulated));
    }
  }, [showRisk, simulated, useWaterRoute]);

  const labels = language === "bn"
    ? { road: "সড়ক", water: "নৌপথ", air: "সিমুলেটেড আকাশপথ", loading: "অফলাইন মানচিত্র প্রস্তুত হচ্ছে", unavailable: "মানচিত্র রেন্ডারার অনুপলব্ধ", local: "স্থানীয় PMTiles" }
    : { road: "Road", water: "Waterway", air: "Simulated airway", loading: "Preparing offline map", unavailable: "Map renderer unavailable", local: "LOCAL PMTILES" };

  return <div className="delta-map geographic-map" aria-label={language === "bn" ? "সিলেটের অফলাইন ভৌগোলিক মানচিত্র" : "Offline geographic map of Sylhet"}>
    <div ref={host} className="maplibre-host" />
    {status !== "ready" && <div className={`map-loading ${status}`} aria-live="polite">
      <div className="delta-loader"><i /><i /><i /></div>
      <strong>{status === "unavailable" ? labels.unavailable : labels.loading}</strong>
      <span>{labels.local}</span>
    </div>}
    <div className="map-legend">
      <span><i className="road-key" />{labels.road}</span>
      <span><i className="water-key" />{labels.water}</span>
      <span><i className="air-key" />{labels.air}</span>
      <code>{labels.local}</code>
    </div>
  </div>;
}

function registerArchive(archiveUrl: string) {
  if (!protocolRegistered) {
    addProtocol("pmtiles", protocol.tile);
    protocolRegistered = true;
  }
  if (!registeredArchives.has(archiveUrl)) {
    protocol.add(new PMTiles(archiveUrl));
    registeredArchives.add(archiveUrl);
  }
}

function hasWebGL(): boolean {
  if (typeof WebGLRenderingContext === "undefined" && typeof WebGL2RenderingContext === "undefined") return false;
  try {
    const canvas = document.createElement("canvas");
    return Boolean(canvas.getContext("webgl2") ?? canvas.getContext("webgl"));
  } catch {
    return false;
  }
}
