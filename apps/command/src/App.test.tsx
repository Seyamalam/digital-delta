import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App, scenarioReducer } from "./App";

describe("Delta Command", () => {
  it("starts Bangla-first and keeps the scenario when language changes", () => {
    render(<App />);
    expect(screen.getByText("ডেল্টা কমান্ড")).toBeVisible();
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

  it("reset preserves the observer link choice but clears scenario effects", () => {
    const disconnected = scenarioReducer({ step: 5, observerConnected: false, failedRoad: true, predictedRisk: true, conflict: true, custodyVerified: true, droneBattery: 25 }, { type: "RESET" });
    expect(disconnected).toEqual({ ...scenarioReducer(disconnected, { type: "RESET" }), observerConnected: false });
    expect(disconnected.failedRoad).toBe(false);
    expect(disconnected.custodyVerified).toBe(false);
  });
});
