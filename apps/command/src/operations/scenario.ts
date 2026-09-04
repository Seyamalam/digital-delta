export type Language = "bn" | "en";
export type ControlMode = "core" | "faults";
export type ScenarioState = {
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

export const initialScenario: ScenarioState = {
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

export type Action =
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

export const copy = {
  bn: {
    command: "ডেল্টা কমান্ড",
    headquarters: "অপারেশন সদরদপ্তর",
    overview: "সাধারণ কার্যচিত্র",
    liveMap: "লাইভ সিলেট অপারেশন মানচিত্র",
    activeMission: "সক্রিয় মিশন",
    situation: "পরিস্থিতি",
    fieldLink: "ফিল্ড লিংক",
    archive: "D1 আর্কাইভ ঐচ্ছিক",
    archiveReady: "D1 আর্কাইভ সংযুক্ত",
    archiveChecking: "D1 আর্কাইভ যাচাই",
    archiveUnavailable: "D1 অনুপলব্ধ · স্থানীয় কাজ চালু",
    localTruth: "স্থানীয় প্রোটোবাফ উৎস",
    incidentBoard: "ঘটনা বোর্ড",
    commandActions: "কমান্ড অ্যাকশন",
    openIncidents: "৩টি খোলা ঘটনা",
    fleet: "বহর ও হ্যান্ডঅফ",
    operational: "অপারেশনাল",
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
    headquarters: "Operations headquarters",
    overview: "Common operating picture",
    liveMap: "Live Sylhet operations map",
    activeMission: "Active mission",
    situation: "Situation",
    fieldLink: "Field link",
    archive: "D1 archive optional",
    archiveReady: "D1 archive connected",
    archiveChecking: "Checking D1 archive",
    archiveUnavailable: "D1 unavailable · local work continues",
    localTruth: "Local Protobuf source",
    incidentBoard: "Incident board",
    commandActions: "Command actions",
    openIncidents: "3 open incidents",
    fleet: "Fleet & handoff",
    operational: "Operational",
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
