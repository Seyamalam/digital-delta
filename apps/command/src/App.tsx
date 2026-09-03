import { useMemo, useReducer, useState } from "react";

type Language = "bn" | "en";
type ScenarioState = {
  step: number;
  observerConnected: boolean;
  failedRoad: boolean;
  predictedRisk: boolean;
  conflict: boolean;
  custodyVerified: boolean;
  droneBattery: number;
};

const initialScenario: ScenarioState = {
  step: 0,
  observerConnected: true,
  failedRoad: false,
  predictedRisk: false,
  conflict: false,
  custodyVerified: false,
  droneBattery: 74,
};

type Action =
  | { type: "STEP" }
  | { type: "TOGGLE_OBSERVER" }
  | { type: "FLOOD" }
  | { type: "RISK" }
  | { type: "CONFLICT" }
  | { type: "BATTERY" }
  | { type: "VERIFY" }
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
        failedRoad: next >= 2,
        conflict: next === 3,
        custodyVerified: next >= 5,
        droneBattery: next >= 4 ? 25 : 74,
      };
    }
    case "TOGGLE_OBSERVER": return { ...state, observerConnected: !state.observerConnected };
    case "FLOOD": return { ...state, failedRoad: !state.failedRoad };
    case "RISK": return { ...state, predictedRisk: !state.predictedRisk };
    case "CONFLICT": return { ...state, conflict: !state.conflict };
    case "BATTERY": return { ...state, droneBattery: state.droneBattery < 30 ? 74 : 25 };
    case "VERIFY": return { ...state, custodyVerified: !state.custodyVerified };
    case "RESET": return { ...initialScenario, observerConnected: state.observerConnected };
  }
}

const copy = {
  bn: {
    command: "ডেল্টা কমান্ড",
    subtitle: "সিলেট দুর্যোগ সমন্বয় • স্থানীয় পর্যবেক্ষণ",
    offline: "বাণিজ্যিক ইন্টারনেট নেই",
    observer: "পর্যবেক্ষক সংযুক্ত",
    observerLost: "পর্যবেক্ষক বিচ্ছিন্ন",
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
    conflict: "দ্বন্দ্ব তৈরি",
    battery: "ড্রোন ২৫%",
    verify: "হেফাজত যাচাই",
    disconnect: "ড্যাশবোর্ড বিচ্ছিন্ন করুন",
    reconnect: "ড্যাশবোর্ড পুনঃসংযোগ",
    proof: "হেফাজত",
    verified: "দুই পক্ষের স্বাক্ষর যাচাইকৃত",
    awaiting: "স্বাক্ষরের অপেক্ষায়",
    mesh: "মেশ কিউ",
    warning: "P0 SLA ভঙ্গের ঝুঁকি",
  },
  en: {
    command: "Delta Command",
    subtitle: "Sylhet disaster coordination • local observer",
    offline: "Commercial internet unavailable",
    observer: "Observer connected",
    observerLost: "Observer disconnected",
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
    conflict: "Create conflict",
    battery: "Drone to 25%",
    verify: "Verify custody",
    disconnect: "Disconnect dashboard",
    reconnect: "Reconnect dashboard",
    proof: "Custody",
    verified: "Two-party signature verified",
    awaiting: "Awaiting signatures",
    mesh: "Mesh queue",
    warning: "P0 SLA breach at risk",
  },
} as const;

export function App() {
  const [language, setLanguage] = useState<Language>("bn");
  const [state, dispatch] = useReducer(scenarioReducer, initialScenario);
  const t = copy[language];
  const events = useMemo(() => buildEvents(state, language), [state, language]);

  return (
    <main className="command-shell" data-language={language}>
      <header className="topbar">
        <div className="brand-lockup">
          <DeltaMark />
          <div><h1>{t.command}</h1><p>{t.subtitle}</p></div>
        </div>
        <div className="top-status">
          <span className="pill offline"><i />{t.offline}</span>
          <span className={`pill ${state.observerConnected ? "connected" : "lost"}`}>
            <i />{state.observerConnected ? t.observer : t.observerLost}
          </span>
          <button className="language" onClick={() => setLanguage(language === "bn" ? "en" : "bn")}>
            {language === "bn" ? "English" : "বাংলা"}
          </button>
        </div>
      </header>

      <section className="mission-strip" aria-label={t.mission}>
        <div className="priority">P0</div>
        <div className="mission-title"><span>{t.simulated}</span><strong>{t.mission}</strong></div>
        <div className="mission-metric"><small>{t.eta}</small><strong>{state.failedRoad ? "45" : "65"}<em> min</em></strong></div>
        <div className="mission-alert"><small>{t.droneRequired}</small><strong>{state.failedRoad ? t.warning : t.fieldSafe}</strong></div>
      </section>

      <div className="dashboard-grid">
        <section className="map-panel panel">
          <PanelHeading eyebrow="M4 + M7 + M8" title={t.route} meta="24.8949°N / 91.8687°E" />
          <DeltaMap state={state} language={language} />
          <div className="route-caption">
            <div><span>{t.route}</span><strong>{state.failedRoad ? t.routeValue : "Truck • N1 → N2 → N4"}</strong></div>
            <div className="route-proof"><span>R3 RENDEZVOUS</span><strong>25.0200, 91.7000</strong></div>
          </div>
        </section>

        <aside className="right-rail">
          <section className="panel nodes-panel">
            <PanelHeading eyebrow="M3" title={t.network} meta="3 / 3" />
            <Node id="A" role="Clinic" battery={82} status="P0 ready" />
            <Node id="B" role="Boat relay" battery={58} status="4 queued" />
            <Node id="C" role="Drone operator" battery={state.droneBattery} status={state.droneBattery < 30 ? "60% throttle" : "ready"} />
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
          <Inventory label="Blood cooler" amount="1 / 1" level={100} critical />
          <Inventory label="Medicine" amount="4 / 6" level={66} critical />
          <Inventory label="ORS" amount="120 / 200" level={60} />
          <Inventory label="Tarpaulin" amount="P2 • N3" level={35} />
        </section>

        <section className="panel event-panel" aria-live="polite">
          <PanelHeading eyebrow="PROTOBUF LEDGER" title={t.events} meta={`SEED 20260412 • ${events.length}`} />
          <ol>{events.map((event) => <li key={event.id}><time>{event.time}</time><i className={event.tone} /><div><strong>{event.title}</strong><span>{event.detail}</span></div><code>{event.id}</code></li>)}</ol>
        </section>

        <section className="panel control-panel">
          <PanelHeading eyebrow="LOCAL ONLY" title={t.controls} meta="SIMULATION" />
          <div className="control-grid">
            <Control active={state.predictedRisk} onClick={() => dispatch({ type: "RISK" })} label={t.risk} code="M7" />
            <Control active={state.failedRoad} onClick={() => dispatch({ type: "FLOOD" })} label={t.road} code="M4" />
            <Control active={state.conflict} onClick={() => dispatch({ type: "CONFLICT" })} label={t.conflict} code="M2" />
            <Control active={state.droneBattery < 30} onClick={() => dispatch({ type: "BATTERY" })} label={t.battery} code="M3" />
            <Control active={state.custodyVerified} onClick={() => dispatch({ type: "VERIFY" })} label={t.verify} code="M5" />
            <Control active={!state.observerConnected} onClick={() => dispatch({ type: "TOGGLE_OBSERVER" })} label={state.observerConnected ? t.disconnect : t.reconnect} code="SYS" />
          </div>
          <div className="primary-controls">
            <button className="advance" onClick={() => dispatch({ type: "STEP" })}>{t.step}<span>0{state.step + 1} / 06</span></button>
            <button className="reset" onClick={() => dispatch({ type: "RESET" })}>{t.reset}</button>
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

function DeltaMark() { return <svg className="delta-mark" viewBox="0 0 58 58" aria-hidden="true"><path d="M29 5 52 50H6L29 5Z" /><path d="M29 17v27M18 29l11 8 11-8" /></svg>; }

function DeltaMap({ state, language }: { state: ScenarioState; language: Language }) {
  return <div className="delta-map"><svg viewBox="0 0 900 410" role="img" aria-label={language === "bn" ? "সিলেটের সিমুলেটেড পথ মানচিত্র" : "Simulated Sylhet route map"}>
    <defs><filter id="glow"><feGaussianBlur stdDeviation="5" result="blur" /><feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge></filter></defs>
    <path className="land" d="M36 48C170 8 320 32 414 72c92 39 188 5 294 35 126 36 171 140 120 218-50 77-169 55-281 54-152-2-255 29-376-8C42 331-30 218 36 48Z" />
    <path className="water ghost" d="M69 36c115 77 125 170 261 202 114 27 245-2 498 126" />
    <path className="water ghost" d="M208 34c53 98 78 130 174 174 97 45 179 42 254 168" />
    <path className={`road ${state.failedRoad ? "failed" : "active"}`} d="M146 275 322 125 536 175" />
    <path className={`boat ${state.failedRoad ? "active" : ""}`} d="M146 275C270 339 405 328 512 246" />
    <path className={`air ${state.failedRoad ? "active" : ""}`} d="M512 246Q653 104 795 119" />
    {state.predictedRisk && <circle className="risk-ring" cx="435" cy="150" r="54" />}
    <MapNode x={146} y={275} id="N1" label="Sylhet hub" />
    <MapNode x={322} y={125} id="N2" label="Airport" />
    <MapNode x={536} y={175} id="N4" label="Companyganj" />
    <MapNode x={512} y={246} id="R3" label="Rendezvous" active={state.failedRoad} />
    <MapNode x={795} y={119} id="N7" label="Haor clinic" active={state.failedRoad} />
    {state.failedRoad && <g className="vehicle boat-icon" transform="translate(360 309)"><circle r="20" /><text y="6">◒</text></g>}
    {state.failedRoad && <g className="vehicle drone-icon" transform="translate(650 160)"><circle r="20" /><text y="6">✦</text></g>}
  </svg><div className="map-legend"><span><i className="road-key" />Road</span><span><i className="water-key" />Waterway</span><span><i className="air-key" />Simulated airway</span></div></div>;
}

function MapNode({ x, y, id, label, active = false }: { x: number; y: number; id: string; label: string; active?: boolean }) {
  return <g className={`map-node ${active ? "active" : ""}`} transform={`translate(${x} ${y})`}><circle r="10" /><circle className="halo" r="20" /><text x="16" y="-4">{id}</text><text className="node-label" x="16" y="14">{label}</text></g>;
}

function Node({ id, role, battery, status }: { id: string; role: string; battery: number; status: string }) {
  return <div className="node-row"><span className="node-id">{id}</span><div><strong>{role}</strong><small>{status}</small></div><div className="battery"><span style={{ width: `${battery}%` }} /><em>{battery}%</em></div></div>;
}

function Inventory({ label, amount, level, critical = false }: { label: string; amount: string; level: number; critical?: boolean }) {
  return <div className="inventory-row"><div><strong>{label}</strong><span>{amount}</span></div><div className="inventory-track"><i className={critical ? "critical" : ""} style={{ width: `${level}%` }} /></div></div>;
}

function Control({ active, onClick, label, code }: { active: boolean; onClick: () => void; label: string; code: string }) {
  return <button className={`control ${active ? "active" : ""}`} aria-pressed={active} onClick={onClick}><code>{code}</code><span>{label}</span><i /></button>;
}

function buildEvents(state: ScenarioState, language: Language) {
  const en = language === "en";
  const events = [
    { id: "REQ…A19F", time: "08:01:04", tone: "teal", title: en ? "P0 request stored offline" : "P0 অনুরোধ অফলাইনে সংরক্ষিত", detail: "N4 → N7 • PROTOBUF" },
    { id: "ROUTE…C821", time: "08:01:06", tone: "blue", title: en ? "Truck route selected" : "ট্রাকের পথ নির্বাচিত", detail: "E1 + E3 • 65 MIN" },
  ];
  if (state.predictedRisk) events.push({ id: "RISK…94D2", time: "08:01:18", tone: "amber", title: en ? "E3 risk predicted at 97.3%" : "E3 ঝুঁকি ৯৭.৩% পূর্বাভাস", detail: "ONNX • SIMULATED INPUTS" });
  if (state.failedRoad) events.push({ id: "RV…7A11", time: "08:01:22", tone: "coral", title: en ? "R3 rendezvous planned" : "R3 মিলনস্থল পরিকল্পিত", detail: "BOAT → SIMULATED DRONE" });
  if (state.conflict) events.push({ id: "CRDT…91B0", time: "08:01:25", tone: "amber", title: en ? "Destination conflict needs review" : "গন্তব্য দ্বন্দ্বে মানব সিদ্ধান্ত প্রয়োজন", detail: "VECTOR CLOCKS CONCURRENT" });
  if (state.droneBattery < 30) events.push({ id: "MESH…0A7C", time: "08:01:31", tone: "blue", title: en ? "Broadcast reduced by 60%" : "সম্প্রচার ৬০% কমানো হয়েছে", detail: "DRONE-07 • 25%" });
  if (state.custodyVerified) events.push({ id: "POD…4120", time: "08:01:44", tone: "green", title: en ? "Drone custody verified" : "ড্রোন হেফাজত যাচাইকৃত", detail: "RSA-PSS • CHAIN VALID" });
  return events;
}
