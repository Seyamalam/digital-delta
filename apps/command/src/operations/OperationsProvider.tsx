"use client";

import { createContext, useContext, useEffect, useReducer, useState, type ReactNode } from "react";
import { connectObserver, type ObserverConnectOptions, type ObserverStatus, type PresentationObservation } from "../observer";
import { activePredictedRisks, hasFeasibleRoute, missionEvaluation, missionSlaState, projectObservations } from "../projection";
import { copy, initialScenario, scenarioReducer, type Language } from "./scenario";

export type OperationsOptions = {
  observerConnect?: (options: ObserverConnectOptions) => () => void;
  observerUrl?: string;
};

export const emptyFeed = () => ({ projection: projectObservations([]), recent: [] as PresentationObservation[] });
export function receiveObservation(current: ReturnType<typeof emptyFeed>, observation: PresentationObservation | null) {
  if (observation === null) return emptyFeed();
  if (observation.sequence <= current.projection.latestSequence) return current;
  return {
    projection: projectObservations([observation], current.projection),
    recent: [...current.recent, observation].slice(-100),
  };
}

function useOperationsState({ observerConnect, observerUrl = process.env.NEXT_PUBLIC_OBSERVER_URL ?? "http://127.0.0.1:7071/observer/events" }: OperationsOptions) {
  const [language, setLanguage] = useState<Language>("bn");
  const [state, dispatch] = useReducer(scenarioReducer, initialScenario);
  const [feed, receive] = useReducer(receiveObservation, undefined, emptyFeed);
  const [observerStatus, setObserverStatus] = useState<ObserverStatus>("connecting");
  const [mode, setMode] = useState<"field" | "exercise">("exercise");
  const [isReplaying, setIsReplaying] = useState(false);
  const [selection, selectMission] = useState<string>();
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  const connector = observerConnect ?? connectObserver;
  useEffect(() => {
    if (!state.observerConnected || (!observerConnect && typeof EventSource === "undefined")) return;
    return connector({ url: observerUrl, onStatus: setObserverStatus, onReset: () => { receive(null); selectMission(undefined); setMode("field"); }, onObservation: (event) => {
      receive(event);
      // Compare newly received timestamps with reception time, not the last 30s display tick.
      setNow(Date.now());
      setMode("field");
      setIsReplaying(false);
    } });
  }, [connector, observerConnect, observerUrl, state.observerConnected]);
  useEffect(() => { document.documentElement.lang = language; }, [language]);
  useEffect(() => {
    if (!isReplaying || mode !== "exercise") return;
    const timer = window.setInterval(() => dispatch({ type: "STEP" }), 1700);
    return () => window.clearInterval(timer);
  }, [isReplaying, mode]);
  const t = copy[language];
  const say = (en: string, bn: string) => language === "en" ? en : bn;
  const exercise = mode === "exercise";
  const projection = feed.projection;
  const selectedMissionId = selection && projection.missions.has(selection) ? selection : projection.missions.keys().next().value;
  const selectedMission = selectedMissionId ? projection.missions.get(selectedMissionId) : undefined;
  const selectedRoute = selectedMission?.route;
  const slaState = missionSlaState(projection, selectedMissionId, now);
  const evaluation = missionEvaluation(projection, selectedMissionId);
  const failedEdges = exercise ? new Set(state.failedRoad ? ["E3"] : []) : projection.failedEdges;
  const edgeRisks = exercise ? new Map(state.predictedRisk ? [["E3", 0.973]] : []) : activePredictedRisks(projection, now);
  const edgeIds = exercise ? (state.failedRoad || state.predictedRisk ? ["E6", "E7"] : ["E1", "E3"]) : selectedRoute?.edgeIds ?? [];
  const eta = exercise ? (state.failedRoad || state.predictedRisk ? 200 : 65) + (state.vehicleDelayed ? 18 : 0) : hasFeasibleRoute(selectedRoute) ? selectedRoute?.etaMinutes : undefined;
  const routeMode = exercise ? (state.failedRoad || state.predictedRisk ? "TRANSPORT_MODE_WATERWAY" : "TRANSPORT_MODE_ROAD") : selectedRoute?.mode;
  const transport = routeMode === "TRANSPORT_MODE_WATERWAY" ? say("Boat", "নৌযান") : routeMode === "TRANSPORT_MODE_AIRWAY" ? say("Drone", "ড্রোন") : routeMode === "TRANSPORT_MODE_ROAD" ? say("Truck", "ট্রাক") : say("Unknown mode", "যানের ধরন অজানা");
  const routeLabel = edgeIds.length ? `${transport} • ${edgeIds.join(" → ")}` : !exercise && selectedRoute ? hasFeasibleRoute(selectedRoute) ? say("Origin and destination coincide · handoff still required", "উৎস ও গন্তব্য একই · হস্তান্তর এখনো প্রয়োজন") : say("No feasible route in this plan", "এই পরিকল্পনায় ব্যবহারযোগ্য পথ নেই") : say("No route received", "কোনো পথ পাওয়া যায়নি");
  const connected = state.observerConnected && observerStatus === "live";
  const observerLabel = !state.observerConnected ? t.observerLost : observerStatus === "live" ? t.observer : observerStatus === "connecting" ? t.observerConnecting : say("Reconnecting observer", "পর্যবেক্ষক পুনঃসংযোগ হচ্ছে");
  return { language, setLanguage, state, dispatch, projection, observations: feed.recent, observerStatus, observerLabel, connected, observerUrl, mode, setMode, exercise, isReplaying, setIsReplaying, failedEdges, edgeRisks, edgeIds, eta, routeLabel, t, say, selectedMissionId, selectedMission, selectedRoute, selectMission, slaState, evaluation, now };
}

const OperationsContext = createContext<ReturnType<typeof useOperationsState> | null>(null);
export function OperationsProvider({ children, ...options }: OperationsOptions & { children: ReactNode }) {
  const value = useOperationsState(options);
  return <OperationsContext.Provider value={value}>{children}</OperationsContext.Provider>;
}
export function useOperations() {
  const state = useContext(OperationsContext);
  if (!state) throw new Error("OperationsProvider is required");
  return state;
}
