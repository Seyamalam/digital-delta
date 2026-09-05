import type { PresentationObservation } from "./observer";

export type ProjectedRoute = {
  eventId: string;
  sourceNodeId: string;
  occurredAtUnixMs: number;
  explanationCode?: string;
  vehicleId?: string;
  mode?: string;
  edgeIds: string[];
  etaMinutes?: number;
  simulated: boolean;
};

export type ProjectedMission = {
  request?: PresentationObservation;
  route?: ProjectedRoute;
  rendezvous?: ProjectedRendezvous;
};

export type MissionSlaState = "WITHIN_SLA" | "BREACH" | "NO_ROUTE" | "STALE" | "CLOCK_SKEW" | "UNKNOWN";

export function hasFeasibleRoute(route: ProjectedRoute | undefined): boolean {
  return !!route && (route.edgeIds.length > 0 || (route.explanationCode === "ALREADY_AT_DESTINATION" && route.etaMinutes === 0));
}

export type ProjectedRendezvous = {
  candidateId?: string;
  latitudeDegrees?: number;
  longitudeDegrees?: number;
};

export type ObserverProjection = {
  latestSequence: number;
  includesSimulated: boolean;
  failedEdges: Set<string>;
  edgeRisks: Map<string, number>;
  riskEvidence: Map<string, { probability: number; threshold: number; occurredAtUnixMs: number }>;
  delayedVehicleIds: Set<string>;
  route?: ProjectedRoute;
  rendezvous?: ProjectedRendezvous;
  requests: Map<string, PresentationObservation>;
  sources: Map<string, number>;
  missions: Map<string, ProjectedMission>;
  // Route IDs bind evaluations even when publication order differs from creation order.
  evaluations: Map<string, PresentationObservation>;
};

export function projectObservations(observations: PresentationObservation[], previous?: ObserverProjection): ObserverProjection {
  const projection: ObserverProjection = {
    ...previous,
    latestSequence: previous?.latestSequence ?? 0,
    includesSimulated: previous?.includesSimulated ?? false,
    failedEdges: new Set(previous?.failedEdges),
    edgeRisks: new Map(previous?.edgeRisks),
    riskEvidence: new Map(previous?.riskEvidence),
    delayedVehicleIds: new Set(previous?.delayedVehicleIds),
    requests: new Map(previous?.requests),
    sources: new Map(previous?.sources),
    missions: new Map(previous?.missions),
    evaluations: new Map(previous?.evaluations),
  };
  const ordered = [...observations].sort((left, right) => left.sequence - right.sequence);
  for (const observation of ordered) {
    if (observation.sequence <= projection.latestSequence) continue;
    projection.latestSequence = Math.max(projection.latestSequence, observation.sequence);
    projection.includesSimulated ||= observation.simulated;
    projection.sources.set(observation.sourceNodeId, observation.occurredAtUnixMs);
    const value = observation.presentation ?? {};
    if (observation.kind === "reliefRequestCreated") {
      const requestId = asString(value.requestId) ?? observation.eventId;
      projection.requests.set(requestId, observation);
      projection.missions.set(requestId, { ...projection.missions.get(requestId), request: observation });
    }
    if (observation.kind === "edgeStatusChanged") {
      const edgeId = asString(value.edgeId);
      if (edgeId) {
        if (value.failed === true) projection.failedEdges.add(edgeId);
        if (value.failed === false) projection.failedEdges.delete(edgeId);
      }
    }
    if (observation.kind === "edgeRiskPredicted") {
      const edgeId = asString(value.edgeId);
      const probability = asNumber(value.probability);
      const threshold = asNumber(value.threshold) ?? 0.65;
      if (edgeId && probability !== undefined && probability >= 0 && probability <= 1 && threshold >= 0 && threshold <= 1) {
        projection.edgeRisks.set(edgeId, probability);
        projection.riskEvidence.set(edgeId, { probability, threshold, occurredAtUnixMs: observation.occurredAtUnixMs });
      }
    }
    if (observation.kind === "routePlanned") {
      projection.route = {
        eventId: observation.eventId,
        sourceNodeId: observation.sourceNodeId,
        occurredAtUnixMs: observation.occurredAtUnixMs,
        explanationCode: asString(value.explanationCode),
        vehicleId: asString(value.vehicleId),
        mode: asString(value.mode),
        edgeIds: asStrings(value.edgeIds),
        etaMinutes: asNumber(value.etaMinutes),
        simulated: observation.simulated,
      };
      const missionId = asString(value.missionId);
      if (missionId) projection.missions.set(missionId, { ...projection.missions.get(missionId), route: projection.route });
    }
    if (observation.kind === "slaEvaluated") {
      const routeEventId = asString(value.routeEventId);
      if (routeEventId) projection.evaluations.set(evaluationKey(observation.sourceNodeId, routeEventId), observation);
    }
    if (observation.kind === "rendezvousPlanned") {
      projection.rendezvous = {
        candidateId: asString(value.candidateId),
        latitudeDegrees: asNumber(value.latitudeDegrees),
        longitudeDegrees: asNumber(value.longitudeDegrees),
      };
      const missionId = asString(value.missionId);
      if (missionId) projection.missions.set(missionId, { ...projection.missions.get(missionId), rendezvous: projection.rendezvous });
    }
    if (observation.kind === "vehicleStateChanged") {
      const vehicleId = asString(value.vehicleId);
      const stateCode = asString(value.stateCode);
      if (vehicleId) {
        if (stateCode?.startsWith("DELAYED")) projection.delayedVehicleIds.add(vehicleId);
        else projection.delayedVehicleIds.delete(vehicleId);
      }
    }
  }
  return projection;
}

function evaluationKey(source: string, routeEventId: string) { return JSON.stringify([source, routeEventId]); }

export function missionEvaluation(projection: ObserverProjection, missionId: string | undefined): PresentationObservation | undefined {
  const route = missionId ? projection.missions.get(missionId)?.route : undefined;
  const evaluation = route && projection.evaluations.get(evaluationKey(route.sourceNodeId, route.eventId));
  return evaluation && evaluation.presentation?.missionId === missionId && evaluation.simulated === route?.simulated ? evaluation : undefined;
}

/** A connection is not proof that a route estimate is current. Retain history without asserting safety. */
export function missionSlaState(projection: ObserverProjection, missionId: string | undefined, now: number): MissionSlaState {
  const route = missionId ? projection.missions.get(missionId)?.route : undefined;
  if (!route) return "UNKNOWN";
  if (route.occurredAtUnixMs > now) return "CLOCK_SKEW";
  if (now - route.occurredAtUnixMs >= 300_000) return "STALE";
  const evaluation = missionEvaluation(projection, missionId);
  if (!evaluation) return "UNKNOWN";
  if (evaluation.occurredAtUnixMs > now) return "CLOCK_SKEW";
  if (evaluation.occurredAtUnixMs < route.occurredAtUnixMs) return "UNKNOWN";
  const value = evaluation.presentation ?? {};
  if (value.stateCode === "NO_ROUTE") return !hasFeasibleRoute(route) ? "NO_ROUTE" : "UNKNOWN";
  const baseline = asNumber(value.baselineArrivalMinutes), slowed = asNumber(value.slowedArrivalMinutes), sla = asNumber(value.slaMinutes);
  if (!hasFeasibleRoute(route) || baseline === undefined || slowed === undefined || sla === undefined || baseline < 0 || slowed < baseline || sla <= 0) return "UNKNOWN";
  if (value.stateCode === "BREACH" && slowed > sla) return "BREACH";
  if (value.stateCode === "WITHIN_SLA" && slowed <= sla) return "WITHIN_SLA";
  return "UNKNOWN";
}

/** Two-hour forecast horizon; stale/future evidence stays in history, not active warnings. */
export function activePredictedRisks(projection: ObserverProjection, nowUnixMs: number): Map<string, number> {
  return new Map([...projection.riskEvidence].filter(([, risk]) =>
    risk.probability > 0 && risk.probability >= risk.threshold &&
    nowUnixMs >= risk.occurredAtUnixMs && nowUnixMs - risk.occurredAtUnixMs < 2 * 60 * 60 * 1000,
  ).map(([id, risk]) => [id, risk.probability]));
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function asStrings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}
