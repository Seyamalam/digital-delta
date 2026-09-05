import { describe, expect, it } from "vitest";
import { activePredictedRisks, missionSlaState, projectObservations } from "./projection";
import type { PresentationObservation } from "./observer";

const observation = (sequence: number, kind: string, presentation: Record<string, unknown>): PresentationObservation => ({
  sequence,
  sourceNodeId: "field-n4",
  eventId: `event-${sequence}`,
  kind,
  occurredAtUnixMs: 1_774_000_000_000 + sequence,
  simulated: true,
  scenarioSeed: "fair-pass-01",
  presentation,
});

describe("observer projection", () => {
  const route = (sequence: number, missionId = "M1") => observation(sequence, "routePlanned", { missionId, edgeIds: ["E5"], etaMinutes: 120 });
  const sla = (sequence: number, routeSequence: number, stateCode = "BREACH", missionId = "M1") => observation(sequence, "slaEvaluated", {
    missionId, routeEventId: `event-${routeSequence}`, stateCode, priority: "PRIORITY_CLASS_P0",
    baselineArrivalMinutes: 90, slowedArrivalMinutes: stateCode === "BREACH" ? 130 : 117, slaMinutes: 120,
  });
  it("isolates mission routes and rendezvous without mutating earlier snapshots", () => {
    const first = projectObservations([route(1)]);
    const next = projectObservations([route(2, "M2"), observation(3, "rendezvousPlanned", { missionId: "M2", candidateId: "R2" })], first);
    expect(next.missions.get("M1")?.route?.eventId).toBe("event-1");
    expect(next.missions.get("M1")?.rendezvous).toBeUndefined();
    expect(next.missions.get("M2")?.rendezvous?.candidateId).toBe("R2");
    expect(first.missions.size).toBe(1);
    const legacy = projectObservations([observation(4, "routePlanned", { edgeIds: ["E1"] })], next);
    expect(legacy.missions.get("M1")?.route?.edgeIds).toEqual(["E5"]);
  });
  it("binds warning recovery to the same mission, route and publisher", () => {
    let state = projectObservations([route(1), sla(2, 1)]);
    const now = observation(20, "", {}).occurredAtUnixMs;
    expect(missionSlaState(state, "M1", now)).toBe("BREACH");
    state = projectObservations([route(3), sla(4, 1, "WITHIN_SLA")], state);
    expect(missionSlaState(state, "M1", now)).toBe("UNKNOWN");
    state = projectObservations([{ ...sla(5, 3, "WITHIN_SLA"), sourceNodeId: "other" }, sla(6, 3, "WITHIN_SLA", "M2")], state);
    expect(missionSlaState(state, "M1", now)).toBe("UNKNOWN");
    state = projectObservations([sla(7, 3, "WITHIN_SLA")], state);
    expect(missionSlaState(state, "M1", now)).toBe("WITHIN_SLA");
    expect(missionSlaState(state, "M1", now + 300_000)).toBe("STALE");
    expect(missionSlaState(state, "M1", now - 100)).toBe("CLOCK_SKEW");
  });
  it("replays evaluations arriving before their routes and clears no-route ETA", () => {
    const early = sla(1, 2, "WITHIN_SLA");
    const later = { ...route(2), occurredAtUnixMs: early.occurredAtUnixMs };
    let state = projectObservations([early, later]);
    expect(missionSlaState(state, "M1", early.occurredAtUnixMs)).toBe("WITHIN_SLA");
    state = projectObservations([
      observation(3, "routePlanned", { missionId: "M1", edgeIds: [], etaMinutes: 0 }),
      observation(4, "slaEvaluated", { missionId: "M1", routeEventId: "event-3", stateCode: "NO_ROUTE" }),
    ], state);
    expect(missionSlaState(state, "M1", observation(4, "", {}).occurredAtUnixMs)).toBe("NO_ROUTE");
  });
  it("distinguishes a zero-length feasible journey from an unreachable destination", () => {
    const state = projectObservations([
      observation(1, "routePlanned", { missionId: "M1", edgeIds: [], etaMinutes: 0, explanationCode: "ALREADY_AT_DESTINATION" }),
      observation(2, "slaEvaluated", { missionId: "M1", routeEventId: "event-1", stateCode: "WITHIN_SLA", baselineArrivalMinutes: 0, slowedArrivalMinutes: 0, slaMinutes: 120 }),
    ]);
    expect(missionSlaState(state, "M1", observation(2, "", {}).occurredAtUnixMs)).toBe("WITHIN_SLA");
  });
  it("separates threshold, age, boat selection, closure and reopening", () => {
    const risk = observation(1, "edgeRiskPredicted", { edgeId: "E3", probability: 0.9, threshold: 0.8 });
    let state = projectObservations([risk, observation(2, "routePlanned", { mode: "TRANSPORT_MODE_WATERWAY", edgeIds: ["E6"] })]);
    expect(state.failedEdges.size).toBe(0);
    expect(activePredictedRisks(state, risk.occurredAtUnixMs + 1).has("E3")).toBe(true);
    expect(activePredictedRisks(state, risk.occurredAtUnixMs + 7_200_000).size).toBe(0);
    expect(activePredictedRisks(state, risk.occurredAtUnixMs - 1).size).toBe(0);
    state = projectObservations([observation(3, "edgeRiskPredicted", { edgeId: "E3", probability: 0, threshold: 0 }), observation(4, "edgeStatusChanged", { edgeId: "E3", failed: true })], state);
    expect(activePredictedRisks(state, risk.occurredAtUnixMs + 100).size).toBe(0);
    expect(state.failedEdges.has("E3")).toBe(true);
    state = projectObservations([observation(5, "edgeStatusChanged", { edgeId: "E3", failed: false })], state);
    expect(state.failedEdges.size).toBe(0);
  });
  it("rebuilds ordered route, hazard, rendezvous, and vehicle state", () => {
    const projection = projectObservations([
      observation(6, "vehicleStateChanged", { vehicleId: "boat-02", stateCode: "DELAYED_18_MIN" }),
      observation(2, "edgeStatusChanged", { edgeId: "E3", failed: true }),
      observation(5, "rendezvousPlanned", { candidateId: "R3", latitudeDegrees: 25.02, longitudeDegrees: 91.7 }),
      observation(3, "routePlanned", { mode: "TRANSPORT_MODE_WATERWAY", edgeIds: ["E6", "E7"], etaMinutes: 171 }),
      observation(1, "edgeRiskPredicted", { edgeId: "E3", probability: 0.973 }),
      observation(7, "edgeStatusChanged", { edgeId: "E3", failed: false }),
    ]);

    expect(projection.latestSequence).toBe(7);
    expect(projection.route?.edgeIds).toEqual(["E6", "E7"]);
    expect(projection.route?.etaMinutes).toBe(171);
    expect(projection.failedEdges.has("E3")).toBe(false);
    expect(projection.edgeRisks.get("E3")).toBeCloseTo(0.973);
    expect(projection.rendezvous?.candidateId).toBe("R3");
    expect(projection.delayedVehicleIds.has("boat-02")).toBe(true);
  });
});
