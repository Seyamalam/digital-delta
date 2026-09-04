import { lazy, Suspense, useEffect, useMemo, useReducer, useState } from "react";
import { connectObserver, type ObserverConnectOptions, type ObserverStatus, type PresentationObservation } from "./observer";
import { projectObservations } from "./projection";

const OfflineDeltaMap = lazy(() => import("./OfflineDeltaMap").then((module) => ({ default: module.OfflineDeltaMap })));

type Language = "bn" | "en";
type ControlMode = "core" | "faults";
type ScenarioState = {
  step: number;
  observerConnected: boolean;
  failedRoad: boolean;
  predictedRisk: boolean;
  rainfallMmPerHour: number;
  soilSaturationPercent: number;
  conflict: boolean;
  custodyVerified: boolean;
  droneBattery: number;
  syncing: boolean;
  nodeOffline: boolean;
  vehicleDelayed: boolean;
  duplicateRejected: boolean;
  tamperRejected: boolean;
};

const initialScenario: ScenarioState = {
  step: 0,
  observerConnected: true,
  failedRoad: false,
  predictedRisk: false,
  rainfallMmPerHour: 28,
  soilSaturationPercent: 48,
  conflict: false,
  custodyVerified: false,
  droneBattery: 74,
  syncing: false,
  nodeOffline: false,
  vehicleDelayed: false,
  duplicateRejected: false,
  tamperRejected: false,
};

type Action =
  | { type: "STEP" }
  | { type: "TOGGLE_OBSERVER" }
  | { type: "FLOOD" }
  | { type: "RISK" }
  | { type: "RAINFALL"; value: number }
  | { type: "SATURATION"; value: number }
  | { type: "CONFLICT" }
  | { type: "BATTERY" }
  | { type: "VERIFY" }
  | { type: "SYNC" }
  | { type: "NODE" }
  | { type: "DELAY" }
  | { type: "DUPLICATE" }
  | { type: "TAMPER" }
  | { type: "RESET" };

export function scenarioReducer(state: ScenarioState, action: Action): ScenarioState {
  switch (action.type) {
    case "STEP": {
      const next = (state.step + 1) % 6;
      return {
        ...initialScenario,
        observerConnected: state.observerConnected,
        step: next,
        predictedRisk: next >= 1,
        rainfallMmPerHour: next >= 1 ? 82 : 28,
        soilSaturationPercent: next >= 1 ? 91 : 48,
        failedRoad: next >= 2,
        conflict: next === 3,
        custodyVerified: next >= 5,
        droneBattery: next >= 4 ? 25 : 74,
      };
    }
    case "TOGGLE_OBSERVER": return { ...state, observerConnected: !state.observerConnected };
    case "FLOOD": return { ...state, failedRoad: !state.failedRoad };
    case "RISK": return state.predictedRisk
      ? { ...state, predictedRisk: false, rainfallMmPerHour: 28, soilSaturationPercent: 48 }
      : { ...state, predictedRisk: true, rainfallMmPerHour: 82, soilSaturationPercent: 91 };
    case "RAINFALL": return {
      ...state,
      rainfallMmPerHour: action.value,
      predictedRisk: action.value >= 72 && state.soilSaturationPercent >= 72,
    };
    case "SATURATION": return {
      ...state,
      soilSaturationPercent: action.value,
      predictedRisk: state.rainfallMmPerHour >= 72 && action.value >= 72,
    };
    case "CONFLICT": return { ...state, conflict: !state.conflict };
    case "BATTERY": return { ...state, droneBattery: state.droneBattery < 30 ? 74 : 25 };
    case "VERIFY": return { ...state, custodyVerified: !state.custodyVerified };
    case "SYNC": return { ...state, syncing: !state.syncing };
    case "NODE": return { ...state, nodeOffline: !state.nodeOffline };
    case "DELAY": return { ...state, vehicleDelayed: !state.vehicleDelayed };
    case "DUPLICATE": return { ...state, duplicateRejected: !state.duplicateRejected };
    case "TAMPER": return { ...state, tamperRejected: !state.tamperRejected };
    case "RESET": return { ...initialScenario, observerConnected: state.observerConnected };
  }
}

const copy = {
  bn: {
    command: "ডেল্টা কমান্ড",
    subtitle: "সিলেট দুর্যোগ সমন্বয় • স্থানীয় পর্যবেক্ষণ",
    offline: "বাণিজ্যিক ইন্টারনেট নেই",
    observer: "লাইভ পর্যবেক্ষক সংযুক্ত",
    observerLost: "পর্যবেক্ষক বিচ্ছিন্ন",
    observerConnecting: "স্থানীয় পর্যবেক্ষক সংযোগ হচ্ছে",
    seededFallback: "বীজভিত্তিক অফলাইন দৃশ্য",
    syncing: "স্থানীয় পর্যবেক্ষণ সিঙ্ক হচ্ছে",
    fieldSafe: "ফিল্ড কাজ চালু আছে",
    simulated: "সিমুলেটেড মহড়া",
    mission: "P0 রক্ত ও ওষুধ • টাঙ্গুয়ার হাওর ক্লিনিক",
    droneRequired: "ড্রোন আবশ্যক",
    route: "সক্রিয় পথ",
    routeValue: "নৌযান → R3 → সিমুলেটেড ড্রোন",
    eta: "সরবরাহ ETA",
    inventory: "জরুরি মজুত",
    network: "ফিল্ড নোড",
    events: "লাইভ ইভেন্ট ধারা",
    controls: "দুর্যোগ নিয়ন্ত্রণ",
    step: "পরের মহড়া ধাপ",
    reset: "বীজে রিসেট",
    road: "E3 সড়ক বন্ধ",
    risk: "ঝুঁকি পূর্বাভাস",
    rainfall: "সিমুলেটেড বৃষ্টিপাত",
    saturation: "সিমুলেটেড মাটির স্যাচুরেশন",
    conflict: "দ্বন্দ্ব তৈরি",
    battery: "ড্রোন ২৫%",
    verify: "হেফাজত যাচাই",
    disconnect: "ড্যাশবোর্ড বিচ্ছিন্ন করুন",
    reconnect: "ড্যাশবোর্ড পুনঃসংযোগ",
    core: "মূল মহড়া",
    faults: "ফল্ট ল্যাব",
    showSync: "সিঙ্ক দেখান",
    nodeOffline: "নোড B অফলাইন",
    delayBoat: "নৌযান বিলম্ব",
    rejectDuplicate: "ডুপ্লিকেট প্রত্যাখ্যান",
    rejectTamper: "বদলানো QR প্রত্যাখ্যান",
    autoReplay: "স্বয়ংক্রিয় রিপ্লে",
    pauseReplay: "রিপ্লে থামান",
    proof: "হেফাজত",
    verified: "দুই পক্ষের স্বাক্ষর যাচাইকৃত",
    awaiting: "স্বাক্ষরের অপেক্ষায়",
    mesh: "মেশ কিউ",
    warning: "P0 SLA ভঙ্গের ঝুঁকি",
    truckRoute: "ট্রাক • N1 → N2 → N4",
    clinic: "ক্লিনিক",
    boatRelay: "নৌযান রিলে",
    droneOperator: "ড্রোন অপারেটর",
    p0Ready: "P0 প্রস্তুত",
    queued: "৪টি কিউতে",
    ready: "প্রস্তুত",
    throttled: "৬০% কম সম্প্রচার",
    offlineQueued: "অফলাইন • কিউ সুরক্ষিত",
    bloodCooler: "রক্তের কুলার",
    medicine: "ওষুধ",
    tarpaulin: "ত্রিপল",
  },
  en: {
    command: "Delta Command",
    subtitle: "Sylhet disaster coordination • local observer",
    offline: "Commercial internet unavailable",
    observer: "Live observer connected",
    observerLost: "Observer disconnected",
    observerConnecting: "Connecting local observer",
    seededFallback: "Seeded offline view",
    syncing: "Syncing local observer",
    fieldSafe: "Field work continues",
    simulated: "SIMULATED EXERCISE",
    mission: "P0 blood & medicine • Tanguar Haor Clinic",
    droneRequired: "DRONE-REQUIRED",
    route: "Active route",
    routeValue: "Boat → R3 → simulated drone",
    eta: "Delivery ETA",
    inventory: "Critical inventory",
    network: "Field nodes",
    events: "Live event stream",
    controls: "Disaster control",
    step: "Advance exercise",
    reset: "Reset to seed",
    road: "Fail road E3",
    risk: "Predict route risk",
    rainfall: "Simulated rainfall",
    saturation: "Simulated soil saturation",
    conflict: "Create conflict",
    battery: "Drone to 25%",
    verify: "Verify custody",
    disconnect: "Disconnect dashboard",
    reconnect: "Reconnect dashboard",
    core: "Core drill",
    faults: "Fault lab",
    showSync: "Show syncing",
    nodeOffline: "Node B offline",
    delayBoat: "Delay boat",
    rejectDuplicate: "Reject duplicate",
    rejectTamper: "Reject tampered QR",
    autoReplay: "Auto replay",
    pauseReplay: "Pause replay",
    proof: "Custody",
    verified: "Two-party signature verified",
    awaiting: "Awaiting signatures",
    mesh: "Mesh queue",
    warning: "P0 SLA breach at risk",
    truckRoute: "Truck • N1 → N2 → N4",
    clinic: "Clinic",
    boatRelay: "Boat relay",
    droneOperator: "Drone operator",
    p0Ready: "P0 ready",
    queued: "4 queued",
    ready: "ready",
    throttled: "60% throttle",
    offlineQueued: "offline • queue retained",
    bloodCooler: "Blood cooler",
    medicine: "Medicine",
    tarpaulin: "Tarpaulin",
  },
} as const;

type AppProps = {
  observerConnect?: (options: ObserverConnectOptions) => () => void;
  observerUrl?: string;
};

export function App({ observerConnect: injectedObserverConnect, observerUrl = import.meta.env.VITE_OBSERVER_URL ?? "http://127.0.0.1:7071/observer/events" }: AppProps = {}) {
  const [language, setLanguage] = useState<Language>("bn");
  const [controlMode, setControlMode] = useState<ControlMode>("core");
  const [isReplaying, setIsReplaying] = useState(false);
  const [observerStatus, setObserverStatus] = useState<ObserverStatus | "seeded">("seeded");
  const [observations, setObservations] = useState<PresentationObservation[]>([]);
  const [state, dispatch] = useReducer(scenarioReducer, initialScenario);
  const t = copy[language];
  const projection = useMemo(() => projectObservations(observations), [observations]);
  const liveRoute = projection.route;
  const liveEdgeIds = liveRoute?.edgeIds ?? [];
  const liveRouteIsWaterway = liveRoute?.mode === "TRANSPORT_MODE_WATERWAY";
  const displayState = {
    ...state,
    failedRoad: state.failedRoad || projection.failedEdges.size > 0 || liveRouteIsWaterway,
    predictedRisk: state.predictedRisk || projection.edgeRisks.size > 0,
    vehicleDelayed: state.vehicleDelayed || projection.delayedVehicleIds.size > 0,
  };
  const liveEta = liveRoute?.etaMinutes ?? null;
  const useFallbackRoute = displayState.failedRoad || displayState.predictedRisk;
  const events = useMemo(() => buildEvents(state, language, observations), [state, language, observations]);
  const observerConnector = injectedObserverConnect ?? (typeof EventSource === "undefined" ? null : connectObserver);

  useEffect(() => {
    if (!state.observerConnected) {
      setObserverStatus("seeded");
      return;
    }
    if (!observerConnector) {
      setObserverStatus("seeded");
      return;
    }
    return observerConnector({
      url: observerUrl,
      onStatus: setObserverStatus,
      onObservation: (observation) => setObservations((current) => {
        if (current.some((event) => event.sequence === observation.sequence)) return current;
        return [...current, observation].sort((left, right) => left.sequence - right.sequence).slice(-100);
      }),
    });
  }, [observerConnector, observerUrl, state.observerConnected]);

  useEffect(() => {
    if (!isReplaying) return;
    const timer = window.setInterval(() => dispatch({ type: "STEP" }), 1_700);
    return () => window.clearInterval(timer);
  }, [isReplaying]);

  const reset = () => {
    setIsReplaying(false);
    dispatch({ type: "RESET" });
  };

  const observerLabel = !state.observerConnected
    ? t.observerLost
    : state.syncing
      ? t.syncing
      : observerStatus === "live"
        ? t.observer
        : observerStatus === "connecting"
          ? t.observerConnecting
          : t.seededFallback;
  const observerTone = !state.observerConnected
    ? "lost"
    : state.syncing || observerStatus === "connecting"
      ? "syncing"
      : observerStatus === "live"
        ? "connected"
        : "offline";
  const routeLabel = liveRoute
    ? `${liveRouteIsWaterway ? (language === "bn" ? "নৌযান" : "Boat") : (language === "bn" ? "ট্রাক" : "Truck")} • ${liveEdgeIds.join(" → ")}`
    : useFallbackRoute ? t.routeValue : t.truckRoute;
  const rendezvousLabel = projection.rendezvous?.candidateId ?? "R3";
  const rendezvousCoordinates = projection.rendezvous?.latitudeDegrees !== undefined && projection.rendezvous.longitudeDegrees !== undefined
    ? `${projection.rendezvous.latitudeDegrees.toFixed(4)}, ${projection.rendezvous.longitudeDegrees.toFixed(4)}`
    : "25.0200, 91.7000";

  return (
    <main className="command-shell" data-language={language}>
      <header className="topbar">
        <div className="brand-lockup">
          <DeltaMark />
          <div><h1>{t.command}</h1><p>{t.subtitle}</p></div>
        </div>
        <div className="top-status">
          <span className="pill offline"><i />{t.offline}</span>
          <span className={`pill ${observerTone}`}>
            <i />{observerLabel}
          </span>
          <button className="language" onClick={() => setLanguage(language === "bn" ? "en" : "bn")}>
            {language === "bn" ? "English" : "বাংলা"}
          </button>
        </div>
      </header>

      <section className="mission-strip" aria-label={t.mission}>
        <div className="priority">P0</div>
        <div className="mission-title"><span>{t.simulated}</span><strong>{t.mission}</strong></div>
        <div className="mission-metric"><small>{t.eta}</small><strong>{liveEta ?? ((displayState.failedRoad ? 45 : 65) + (displayState.vehicleDelayed ? 18 : 0))}<em> min</em></strong></div>
        <div className="mission-alert"><small>{t.droneRequired}</small><strong>{displayState.failedRoad ? t.warning : t.fieldSafe}</strong></div>
      </section>

      <div className="dashboard-grid">
        <section className="map-panel panel">
          <PanelHeading eyebrow="M4 + M7 + M8" title={t.route} meta="24.8949°N / 91.8687°E" />
          <Suspense fallback={<OfflineMapModuleFallback language={language} />}>
            <OfflineDeltaMap useWaterRoute={useFallbackRoute} showRisk={displayState.predictedRisk} simulated={liveRoute?.simulated ?? true} language={language} />
          </Suspense>
          <div className="route-caption">
            <div><span>{t.route}</span><strong>{routeLabel}</strong></div>
            <div className="route-proof"><span>{rendezvousLabel} RENDEZVOUS</span><strong>{rendezvousCoordinates}</strong></div>
          </div>
        </section>

        <aside className="right-rail">
          <section className="panel nodes-panel">
            <PanelHeading eyebrow="M3" title={t.network} meta="3 / 3" />
            <Node id="A" role={t.clinic} battery={82} status={t.p0Ready} />
            <Node id="B" role={t.boatRelay} battery={58} status={state.nodeOffline ? t.offlineQueued : t.queued} offline={state.nodeOffline} />
            <Node id="C" role={t.droneOperator} battery={state.droneBattery} status={state.droneBattery < 30 ? t.throttled : t.ready} />
            <div className="mesh-line"><span>A</span><b /><span>B</span><b /><span>C</span></div>
          </section>
          <section className={`panel custody-panel ${state.custodyVerified ? "is-verified" : ""}`}>
            <PanelHeading eyebrow="M5" title={t.proof} meta={state.custodyVerified ? "SHA 925E4120" : "PENDING"} />
            <div className="custody-seal">{state.custodyVerified ? "✓" : "⌁"}</div>
            <strong>{state.custodyVerified ? t.verified : t.awaiting}</strong>
            <p>Boat-02 → simulated-drone-07</p>
          </section>
        </aside>

        <section className="panel inventory-panel">
          <PanelHeading eyebrow="M6" title={t.inventory} meta="P0 FIRST" />
          <Inventory label={t.bloodCooler} amount="1 / 1" level={100} critical />
          <Inventory label={t.medicine} amount="4 / 6" level={66} critical />
          <Inventory label="ORS" amount="120 / 200" level={60} />
          <Inventory label={t.tarpaulin} amount="P2 • N3" level={35} />
        </section>

        <section className="panel event-panel" aria-live="polite">
          <PanelHeading eyebrow="PROTOBUF LEDGER" title={t.events} meta={projection.latestSequence > 0 ? `LIVE SEQ ${projection.latestSequence} • ${events.length}` : `SEED 20260412 • ${events.length}`} />
          <ol>{events.map((event) => <li key={event.id}><time>{event.time}</time><i className={event.tone} /><div><strong>{event.title}</strong><span>{event.detail}</span></div><code>{event.id}</code></li>)}</ol>
        </section>

        <section className="panel control-panel">
          <PanelHeading eyebrow="LOCAL ONLY" title={t.controls} meta="SIMULATION" />
          <div className="control-tabs" role="tablist" aria-label={t.controls}>
            <button role="tab" aria-selected={controlMode === "core"} onClick={() => setControlMode("core")}>{t.core}</button>
            <button role="tab" aria-selected={controlMode === "faults"} onClick={() => setControlMode("faults")}>{t.faults}</button>
          </div>
          {controlMode === "core" && <div className="environment-controls">
            <EnvironmentControl
              label={t.rainfall}
              valueLabel={`${state.rainfallMmPerHour} mm/h`}
              value={state.rainfallMmPerHour}
              max={140}
              onChange={(value) => dispatch({ type: "RAINFALL", value })}
            />
            <EnvironmentControl
              label={t.saturation}
              valueLabel={`${state.soilSaturationPercent}%`}
              value={state.soilSaturationPercent}
              max={100}
              onChange={(value) => dispatch({ type: "SATURATION", value })}
            />
          </div>}
          <div className="control-grid">
            {controlMode === "core" ? <>
              <Control active={state.failedRoad} onClick={() => dispatch({ type: "FLOOD" })} label={t.road} code="M4" />
              <Control active={state.conflict} onClick={() => dispatch({ type: "CONFLICT" })} label={t.conflict} code="M2" />
              <Control active={state.droneBattery < 30} onClick={() => dispatch({ type: "BATTERY" })} label={t.battery} code="M3" />
              <Control active={state.custodyVerified} onClick={() => dispatch({ type: "VERIFY" })} label={t.verify} code="M5" />
            </> : <>
              <Control active={state.syncing} onClick={() => dispatch({ type: "SYNC" })} label={t.showSync} code="SYNC" />
              <Control active={state.nodeOffline} onClick={() => dispatch({ type: "NODE" })} label={t.nodeOffline} code="M3" />
              <Control active={state.vehicleDelayed} onClick={() => dispatch({ type: "DELAY" })} label={t.delayBoat} code="M8" />
              <Control active={state.duplicateRejected} onClick={() => dispatch({ type: "DUPLICATE" })} label={t.rejectDuplicate} code="M3" />
              <Control active={state.tamperRejected} onClick={() => dispatch({ type: "TAMPER" })} label={t.rejectTamper} code="M5" />
              <Control active={!state.observerConnected} onClick={() => dispatch({ type: "TOGGLE_OBSERVER" })} label={state.observerConnected ? t.disconnect : t.reconnect} code="SYS" />
            </>}
          </div>
          <div className="primary-controls">
            <button className="advance" onClick={() => dispatch({ type: "STEP" })}>{t.step}<span>0{state.step + 1} / 06</span></button>
            <button className={`replay ${isReplaying ? "active" : ""}`} aria-pressed={isReplaying} onClick={() => setIsReplaying(!isReplaying)}>{isReplaying ? t.pauseReplay : t.autoReplay}</button>
            <button className="reset" onClick={reset}>{t.reset}</button>
          </div>
        </section>
      </div>
      {!state.observerConnected && <div className="disconnect-banner" role="status"><strong>{t.observerLost}</strong><span>{t.fieldSafe}</span></div>}
    </main>
  );
}

function PanelHeading({ eyebrow, title, meta }: { eyebrow: string; title: string; meta: string }) {
  return <div className="panel-heading"><div><span>{eyebrow}</span><h2>{title}</h2></div><code>{meta}</code></div>;
}

function OfflineMapModuleFallback({ language }: { language: Language }) {
  const labels = language === "bn"
    ? { map: "সিলেটের অফলাইন ভৌগোলিক মানচিত্র", road: "সড়ক", water: "নৌপথ", air: "সিমুলেটেড আকাশপথ", loading: "মানচিত্র মডিউল প্রস্তুত হচ্ছে", local: "স্থানীয় PMTiles" }
    : { map: "Offline geographic map of Sylhet", road: "Road", water: "Waterway", air: "Simulated airway", loading: "Preparing map module", local: "LOCAL PMTILES" };
  return <div className="delta-map geographic-map" aria-label={labels.map}>
    <div className="map-loading" aria-live="polite"><div className="delta-loader"><i /><i /><i /></div><strong>{labels.loading}</strong><span>{labels.local}</span></div>
    <div className="map-legend"><span><i className="road-key" />{labels.road}</span><span><i className="water-key" />{labels.water}</span><span><i className="air-key" />{labels.air}</span><code>{labels.local}</code></div>
  </div>;
}

function DeltaMark() { return <svg className="delta-mark" viewBox="0 0 58 58" aria-hidden="true"><path d="M29 5 52 50H6L29 5Z" /><path d="M29 17v27M18 29l11 8 11-8" /></svg>; }

function Node({ id, role, battery, status, offline = false }: { id: string; role: string; battery: number; status: string; offline?: boolean }) {
  return <div className={`node-row ${offline ? "is-offline" : ""}`}><span className="node-id">{id}</span><div><strong>{role}</strong><small>{status}</small></div><div className="battery"><span style={{ width: `${battery}%` }} /><em>{battery}%</em></div></div>;
}

function Inventory({ label, amount, level, critical = false }: { label: string; amount: string; level: number; critical?: boolean }) {
  return <div className="inventory-row"><div><strong>{label}</strong><span>{amount}</span></div><div className="inventory-track"><i className={critical ? "critical" : ""} style={{ width: `${level}%` }} /></div></div>;
}

function Control({ active, onClick, label, code }: { active: boolean; onClick: () => void; label: string; code: string }) {
  return <button className={`control ${active ? "active" : ""}`} aria-pressed={active} onClick={onClick}><code>{code}</code><span>{label}</span><i /></button>;
}

function EnvironmentControl({ label, valueLabel, value, max, onChange }: { label: string; valueLabel: string; value: number; max: number; onChange: (value: number) => void }) {
  return <label className="environment-control">
    <span>{label}</span>
    <strong>{valueLabel}</strong>
    <input aria-label={label} type="range" min="0" max={max} value={value} onChange={(event) => onChange(Number(event.target.value))} />
  </label>;
}

function buildEvents(state: ScenarioState, language: Language, observations: PresentationObservation[] = []) {
  const en = language === "en";
  const events = [
    { id: "REQ…A19F", time: "08:01:04", tone: "teal", title: en ? "P0 request stored offline" : "P0 অনুরোধ অফলাইনে সংরক্ষিত", detail: "N4 → N7 • PROTOBUF" },
    { id: "ROUTE…C821", time: "08:01:06", tone: "blue", title: en ? "Truck route selected" : "ট্রাকের পথ নির্বাচিত", detail: "E1 + E3 • 65 MIN" },
  ];
  if (state.predictedRisk) events.push({ id: "RISK…94D2", time: "08:01:18", tone: "amber", title: en ? "E3 risk predicted at 97.3%" : "E3 ঝুঁকি ৯৭.৩% পূর্বাভাস", detail: `${state.rainfallMmPerHour} MM/H • ${state.soilSaturationPercent}% SOIL • SIMULATED INPUTS` });
  if (state.failedRoad) events.push({ id: "RV…7A11", time: "08:01:22", tone: "coral", title: en ? "R3 rendezvous planned" : "R3 মিলনস্থল পরিকল্পিত", detail: "BOAT → SIMULATED DRONE" });
  if (state.conflict) events.push({ id: "CRDT…91B0", time: "08:01:25", tone: "amber", title: en ? "Destination conflict needs review" : "গন্তব্য দ্বন্দ্বে মানব সিদ্ধান্ত প্রয়োজন", detail: "VECTOR CLOCKS CONCURRENT" });
  if (state.droneBattery < 30) events.push({ id: "MESH…0A7C", time: "08:01:31", tone: "blue", title: en ? "Broadcast reduced by 60%" : "সম্প্রচার ৬০% কমানো হয়েছে", detail: "DRONE-07 • 25%" });
  if (state.custodyVerified) events.push({ id: "POD…4120", time: "08:01:44", tone: "green", title: en ? "Drone custody verified" : "ড্রোন হেফাজত যাচাইকৃত", detail: "RSA-PSS • CHAIN VALID" });
  if (state.syncing) events.push({ id: "SYNC…882A", time: "08:01:48", tone: "blue", title: en ? "Observer projection rebuilding" : "পর্যবেক্ষণ প্রক্ষেপণ পুনর্গঠিত হচ্ছে", detail: "LOCAL LINK • FIELD INDEPENDENT" });
  if (state.nodeOffline) events.push({ id: "NODE…B300", time: "08:01:52", tone: "coral", title: en ? "Relay B offline; queue retained" : "রিলে B অফলাইন; কিউ সুরক্ষিত", detail: "STORE-AND-FORWARD • 4 QUEUED" });
  if (state.vehicleDelayed) events.push({ id: "DELAY…18M", time: "08:01:56", tone: "amber", title: en ? "Boat delayed by 18 min" : "নৌযান ১৮ মিনিট বিলম্বিত", detail: "SIMULATED VEHICLE INPUT" });
  if (state.duplicateRejected) events.push({ id: "DEDUP…44A1", time: "08:02:01", tone: "green", title: en ? "Duplicate envelope rejected" : "ডুপ্লিকেট এনভেলপ প্রত্যাখ্যাত", detail: "MESSAGE ID ALREADY CLAIMED" });
  if (state.tamperRejected) events.push({ id: "POD…BAD5", time: "08:02:06", tone: "green", title: en ? "Signature mismatch rejected" : "স্বাক্ষর অমিল প্রত্যাখ্যাত", detail: "CUSTODY CHAIN UNCHANGED" });
  for (const observation of observations) {
    const eventTitles: Record<string, [string, string]> = {
      reliefRequestCreated: ["Relief request received", "ত্রাণ অনুরোধ গ্রহণ করা হয়েছে"],
      edgeRiskPredicted: ["Route risk prediction received", "পথের ঝুঁকি পূর্বাভাস গ্রহণ করা হয়েছে"],
      edgeStatusChanged: ["Edge status changed", "পথের অবস্থা পরিবর্তিত"],
      routePlanned: ["Live route received", "লাইভ পথ গ্রহণ করা হয়েছে"],
      slaBreachPredicted: ["SLA warning received", "SLA সতর্কতা গ্রহণ করা হয়েছে"],
      rendezvousPlanned: ["Rendezvous plan received", "মিলনস্থল পরিকল্পনা গ্রহণ করা হয়েছে"],
      vehicleStateChanged: ["Vehicle state changed", "যানের অবস্থা পরিবর্তিত"],
    };
    const titles = eventTitles[observation.kind] ?? ["Live field event received", "লাইভ মাঠ ইভেন্ট গ্রহণ করা হয়েছে"];
    const eventTime = new Date(observation.occurredAtUnixMs).toLocaleTimeString(en ? "en-GB" : "bn-BD", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
      timeZone: "Asia/Dhaka",
    });
    events.push({
      id: observation.eventId,
      time: eventTime,
      tone: observation.simulated ? "amber" : observation.kind === "routePlanned" ? "blue" : "teal",
      title: en ? titles[0] : titles[1],
      detail: `${observation.sourceNodeId} • SEQ ${observation.sequence} • ${observation.simulated ? "SIMULATED EVENT" : "LIVE EVENT"}`,
    });
  }
  return events;
}
