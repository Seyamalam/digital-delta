import { describe, expect, it } from "vitest";
import { activePredictedRisks, projectObservations } from "./projection";
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
