import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { App, scenarioReducer } from "./App";
import type { ObserverConnectOptions } from "./observer";

describe("Delta Command", () => {
  it("starts Bangla-first and keeps the scenario when language changes", () => {
    render(<App />);
    expect(screen.getByText("ডেল্টা কমান্ড")).toBeVisible();
    expect(screen.getByText("রক্তের কুলার")).toBeVisible();
    expect(screen.getByText("সড়ক")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(screen.getByText("Delta Command")).toBeVisible();
    expect(screen.getByText("Truck • N1 → N2 → N4")).toBeVisible();
  });

  it("advances to a visibly simulated hybrid route and signed custody", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    const advance = screen.getByRole("button", { name: /Advance exercise/ });
    for (let step = 0; step < 5; step += 1) fireEvent.click(advance);
    expect(screen.getByText("Boat → R3 → simulated drone")).toBeVisible();
    expect(screen.getByText("Two-party signature verified")).toBeVisible();
    expect(screen.getByText("Drone custody verified")).toBeVisible();
    expect(screen.getAllByText(/SIMULATED/).length).toBeGreaterThan(0);
  });

  it("shows that field work continues when the observer disconnects", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(screen.getByRole("button", { name: /Disconnect dashboard/ }));
    expect(screen.getByRole("status")).toHaveTextContent("Observer disconnected");
    expect(screen.getByRole("status")).toHaveTextContent("Field work continues");
  });

  it("demonstrates observer sync and deterministic fault rejection", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(screen.getByRole("tab", { name: "Fault lab" }));
    fireEvent.click(screen.getByRole("button", { name: /Show syncing/ }));
    fireEvent.click(screen.getByRole("button", { name: /Reject duplicate/ }));
    fireEvent.click(screen.getByRole("button", { name: /Reject tampered QR/ }));
    expect(screen.getByText("Syncing local observer")).toBeVisible();
    expect(screen.getByText("Duplicate envelope rejected")).toBeVisible();
    expect(screen.getByText("Signature mismatch rejected")).toBeVisible();
  });

  it("keeps an offline relay queue and shows a simulated vehicle delay", () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(screen.getByRole("tab", { name: "Fault lab" }));
    fireEvent.click(screen.getByRole("button", { name: /Node B offline/ }));
    fireEvent.click(screen.getByRole("button", { name: /Delay boat/ }));
    expect(screen.getByText("offline • queue retained")).toBeVisible();
    expect(screen.getByText("Boat delayed by 18 min")).toBeVisible();
  });

  it("starts and pauses deterministic automatic replay", () => {
    vi.useFakeTimers();
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    fireEvent.click(screen.getByRole("button", { name: "Auto replay" }));
    act(() => vi.advanceTimersByTime(1_750));
    expect(screen.getByRole("button", { name: /Advance exercise02 \/ 06/ })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Pause replay" }));
    act(() => vi.advanceTimersByTime(3_500));
    expect(screen.getByRole("button", { name: /Advance exercise02 \/ 06/ })).toBeVisible();
    vi.useRealTimers();
  });

  it("applies a real route observation and identifies its live sequence", () => {
    const observerConnect = (options: ObserverConnectOptions) => {
      options.onStatus("live");
      options.onObservation({
        sequence: 9,
        sourceNodeId: "field-n4",
        eventId: "route-live-9",
        kind: "routePlanned",
        occurredAtUnixMs: 1_774_000_000_000,
        simulated: false,
        presentation: {
          vehicleId: "boat-02",
          mode: "TRANSPORT_MODE_WATERWAY",
          edgeIds: ["E6", "E7"],
          etaMinutes: 171,
        },
      });
      return () => undefined;
    };

    render(<App observerConnect={observerConnect} />);
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(screen.getByText("Live observer connected")).toBeVisible();
    expect(screen.getByText("Boat • E6 → E7")).toBeVisible();
    expect(screen.getByText("Live route received")).toBeVisible();
    expect(screen.getByText("field-n4 • SEQ 9 • LIVE EVENT")).toBeVisible();
  });

  it("reset preserves the observer link choice but clears scenario effects", () => {
    const disconnected = scenarioReducer({ step: 5, observerConnected: false, failedRoad: true, predictedRisk: true, conflict: true, custodyVerified: true, droneBattery: 25, syncing: true, nodeOffline: true, vehicleDelayed: true, duplicateRejected: true, tamperRejected: true }, { type: "RESET" });
    expect(disconnected).toEqual({ ...scenarioReducer(disconnected, { type: "RESET" }), observerConnected: false });
    expect(disconnected.failedRoad).toBe(false);
    expect(disconnected.custodyVerified).toBe(false);
  });
});
