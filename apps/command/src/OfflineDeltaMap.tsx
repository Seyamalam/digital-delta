import { useEffect, useRef, useState } from "react";
import { addProtocol, AttributionControl, GeoJSONSource, Map as MapLibreMap, Marker, NavigationControl, setWorkerUrl } from "maplibre-gl";
import { PMTiles, Protocol } from "pmtiles";
import { buildMissionGeoJson, createOfflineStyle, missionBounds, offlineMapRevision, type MissionMapState } from "./offlineMap";
import { Button } from "@/components/ui/button";
import { Layers, LocateFixed, MapPin, X } from "lucide-react";
import { exerciseZones, operationalLocations } from "./operations/mapLayers";

type OfflineDeltaMapProps = MissionMapState & {
  useWaterRoute: boolean;
  showRisk: boolean;
  simulated: boolean;
  language: "bn" | "en";
  exerciseOverlay?: boolean;
};

const protocol = new Protocol();
const registeredArchives = new Set<string>();
let protocolRegistered = false;

export function OfflineDeltaMap({ useWaterRoute, showRisk, simulated, language, exerciseOverlay = true, ...missionState }: OfflineDeltaMapProps) {
  const host = useRef<HTMLDivElement>(null);
  const map = useRef<MapLibreMap | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "unavailable">("loading");
  const [panel, setPanel] = useState<"layers" | "locations" | null>(null);
  const [selected, setSelected] = useState("");
  const [zonesVisible, setZonesVisible] = useState(false);
  const [routesVisible, setRoutesVisible] = useState(true);
  const markers = useRef<Marker[]>([]);
  const latestData = useRef(buildMissionGeoJson(useWaterRoute, showRisk, simulated, missionState));
  latestData.current = buildMissionGeoJson(useWaterRoute, showRisk, simulated, { ...missionState, showScenarioNodes: exerciseOverlay });
  const say = (en: string, bn: string) => language === "en" ? en : bn;

  useEffect(() => {
    if (!host.current || !hasWebGL()) {
      setStatus("unavailable");
      return;
    }
    const archive = new URL("/maps/sylhet.pmtiles", document.baseURI);
    archive.searchParams.set("v", offlineMapRevision);
    const archiveUrl = archive.toString();
    setWorkerUrl(new URL("/vendor/maplibre/maplibre-gl-worker.mjs", document.baseURI).toString());
    registerArchive(archiveUrl);
    const instance = new MapLibreMap({
      container: host.current,
      style: createOfflineStyle(archiveUrl, latestData.current),
      bounds: missionBounds,
      fitBoundsOptions: { padding: 28 },
      minZoom: 6,
      maxZoom: 14,
      attributionControl: false,
      dragPan: true,
      dragRotate: false,
      scrollZoom: false,
      keyboard: true,
      pitchWithRotate: false,
      touchZoomRotate: true,
    });
    instance.addControl(new AttributionControl({ compact: true }), "bottom-right");
    instance.addControl(new NavigationControl({ showCompass: false, visualizePitch: false }), "top-right");
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
      instance.addSource("exercise-zones", { type: "geojson", data: exerciseZones });
      instance.addLayer({ id: "exercise-zones-fill", type: "fill", source: "exercise-zones", layout: { visibility: "none" }, paint: { "fill-color": ["match", ["get", "kind"], "hazard", "#c84237", "#14835e"], "fill-opacity": 0.17 } }, "mission-routes");
      instance.addLayer({ id: "exercise-zones-outline", type: "line", source: "exercise-zones", layout: { visibility: "none" }, paint: { "line-color": ["match", ["get", "kind"], "hazard", "#c84237", "#14835e"], "line-width": 2, "line-dasharray": [3, 2] } }, "mission-routes");
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
      source.setData(latestData.current);
    }
  }, [showRisk, simulated, useWaterRoute, missionState.edgeIds, missionState.failedEdgeIds, missionState.edgeRisks, missionState.rendezvous, exerciseOverlay, status]);

  useEffect(() => {
    if (status !== "ready" || !map.current) return;
    map.current.setLayoutProperty("mission-routes", "visibility", routesVisible ? "visible" : "none");
    for (const id of ["exercise-zones-fill", "exercise-zones-outline"]) map.current.setLayoutProperty(id, "visibility", zonesVisible && exerciseOverlay ? "visible" : "none");
  }, [status, routesVisible, zonesVisible, exerciseOverlay]);

  useEffect(() => {
    if (status !== "ready" || !map.current) return;
    const instance = map.current;
    markers.current.forEach((marker) => marker.remove());
    markers.current = [];
    if (!exerciseOverlay) return;
    for (const location of operationalLocations) {
      const element = document.createElement("button");
      element.type = "button";
      element.className = "geographic-node-label";
      element.textContent = location.id;
      element.setAttribute("aria-label", `${location.name[language]} · ${say("exercise location", "মহড়ার অবস্থান")}`);
      element.addEventListener("click", () => { setPanel("locations"); setSelected(location.id); });
      markers.current.push(new Marker({ element, anchor: "bottom-left" }).setLngLat(location.coordinates).addTo(instance));
    }
    return () => { markers.current.forEach((marker) => marker.remove()); markers.current = []; };
  }, [status, language, exerciseOverlay]);

  useEffect(() => {
    const locationId = new URLSearchParams(window.location.search).get("location");
    if (locationId && operationalLocations.some((location) => location.id === locationId)) {
      setSelected(locationId); setPanel("locations");
    }
  }, []);
  useEffect(() => {
    const location = operationalLocations.find((item) => item.id === selected);
    if (location && status === "ready" && exerciseOverlay) map.current?.flyTo({ center: location.coordinates, zoom: 11, duration: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 700 });
  }, [selected, status, exerciseOverlay]);
  const selectedLocation = operationalLocations.find((location) => location.id === selected);

  const labels = language === "bn"
    ? { road: "সড়ক", water: "নৌপথ", air: "সিমুলেটেড আকাশপথ", loading: "অফলাইন মানচিত্র প্রস্তুত হচ্ছে", unavailable: "মানচিত্র রেন্ডারার অনুপলব্ধ", local: "স্থানীয় PMTiles • OSM পথরেখা" }
    : { road: "Road", water: "Waterway", air: "Simulated airway", loading: "Preparing offline map", unavailable: "Map renderer unavailable", local: "LOCAL PMTILES • OSM ROUTE GEOMETRY" };

  return <div className="delta-map geographic-map" aria-label={language === "bn" ? "সিলেটের অফলাইন ভৌগোলিক মানচিত্র" : "Offline geographic map of Sylhet"}>
    <div ref={host} className="maplibre-host" />
    <div className="hq-map-tools">
      <Button variant="outline" onClick={() => setPanel(panel === "layers" ? null : "layers")} aria-expanded={panel === "layers"}><Layers data-icon="inline-start" />{say("Layers", "স্তর")}</Button>
      <Button variant="outline" onClick={() => setPanel(panel === "locations" ? null : "locations")} aria-label={say("Locations", "অবস্থান")} aria-expanded={panel === "locations"}><MapPin data-icon="inline-start" /><span className="hq-map-tool-label">{say("Locations", "অবস্থান")}</span></Button>
      <Button variant="outline" size="icon" aria-label={say("Fit operating area", "পুরো কার্যক্রম এলাকা দেখুন")} onClick={() => map.current?.fitBounds(missionBounds, { padding: 40, duration: 0 })}><LocateFixed /></Button>
    </div>
    {panel && <section className="hq-map-details" aria-label={panel === "layers" ? say("Map layers", "মানচিত্রের স্তর") : say("Location details", "অবস্থানের বিবরণ")}>
      <Button variant="ghost" size="icon" className="float-right" aria-label={say("Close map panel", "মানচিত্রের প্যানেল বন্ধ করুন")} onClick={() => setPanel(null)}><X /></Button>
      {panel === "layers" ? <><h3>{say("Map layers", "মানচিত্রের স্তর")}</h3><label><input type="checkbox" checked={routesVisible} onChange={(event) => setRoutesVisible(event.target.checked)} />{say("Route network", "পথের নেটওয়ার্ক")}</label><label><input type="checkbox" disabled={!exerciseOverlay} checked={zonesVisible && exerciseOverlay} onChange={(event) => setZonesVisible(event.target.checked)} />{say("Exercise zones", "মহড়ার অঞ্চল")}</label><p>{say("Red: simulated flood impact. Green: exercise assembly area. Neither is a verified safety boundary.", "লাল: সিমুলেটেড বন্যার প্রভাব। সবুজ: মহড়ার সমাবেশ এলাকা। কোনোটিই যাচাইকৃত নিরাপত্তা সীমানা নয়।")}</p></> : <><h3>{say("Locations", "অবস্থান")}</h3>{exerciseOverlay ? <><select aria-label={say("Select location", "অবস্থান নির্বাচন করুন")} value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">{say("Choose a location", "একটি অবস্থান বেছে নিন")}</option>{operationalLocations.map((location) => <option key={location.id} value={location.id}>{location.id} · {location.name[language]}</option>)}</select>{selectedLocation && <><strong>{selectedLocation.name[language]}</strong><p>{selectedLocation.description[language]}</p><code>{selectedLocation.coordinates[1].toFixed(4)}, {selectedLocation.coordinates[0].toFixed(4)}</code></>}</> : <p>{say("No verified location feed. Exercise locations are hidden in field mode.", "যাচাইকৃত অবস্থানের উৎস নেই। ফিল্ড মোডে মহড়ার অবস্থান লুকানো থাকে।")}</p>}</>}
    </section>}
    {status !== "ready" && <div className={`map-loading ${status}`} aria-live="polite">
      <div className="map-loader-line" />
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
