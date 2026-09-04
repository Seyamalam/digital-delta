import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OperationsProvider, emptyFeed, receiveObservation } from "./operations/OperationsProvider";
import { OperationsShell } from "./operations/OperationsShell";
import { OverviewPage } from "./operations/OverviewPage";
import { MissionsPage } from "./operations/MissionsPage";
import { NetworkPage } from "./operations/NetworkPage";
import { ActivityPage } from "./operations/ActivityPage";
import { ExercisePage } from "./operations/ExercisePage";
import { ResourcesPage } from "./operations/ResourcesPage";
import { scenarioReducer, initialScenario } from "./operations/scenario";
import type { ObserverConnectOptions, PresentationObservation } from "./observer";

vi.mock("next/navigation", () => ({ usePathname: () => "/" }));
vi.mock("./OfflineDeltaMap", () => ({ OfflineDeltaMap: () => <div data-testid="map">Geographic map</div> }));
Object.defineProperty(window, "matchMedia", { writable: true, value: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }) });
const idle = () => () => undefined;
const event = (sequence: number, kind = "vehicleStateChanged", presentation: Record<string, unknown> = {}): PresentationObservation => ({ sequence, kind, presentation, eventId: `event-${sequence}`, sourceNodeId: "N4", simulated: false, occurredAtUnixMs: 1774000000000 });

function english() { fireEvent.click(screen.getByRole("button", { name: "English" })); }
function workspace(page: React.ReactNode, connect: (options: ObserverConnectOptions) => () => void = idle) { return <OperationsProvider observerConnect={connect}><OperationsShell>{page}</OperationsShell></OperationsProvider>; }

describe("routed headquarters", () => {
  it("offers actual sidebar URLs and keeps the overview focused", () => {
    render(workspace(<OverviewPage />));
    expect(document.documentElement.lang).toBe("bn");
    english();
    expect(document.documentElement.lang).toBe("en");
    for (const [name, href] of [["Live map", "/map"], ["Missions", "/missions"], ["Resources & shelters", "/resources"], ["Field network", "/network"], ["Activity log", "/activity"], ["Exercise lab", "/exercise"]]) expect(screen.getByRole("link", { name })).toHaveAttribute("href", href);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Every response starts here.");
    expect(screen.queryByRole("slider")).not.toBeInTheDocument();
    expect(screen.queryByText("Scenario controls")).not.toBeInTheDocument();
  });

  it("preserves language and observer connection when route content changes", () => {
    const cleanup = vi.fn();
    const connector = vi.fn(() => cleanup);
    const view = render(workspace(<OverviewPage />, connector));
    english();
    view.rerender(workspace(<MissionsPage />, connector));
    expect(document.documentElement.lang).toBe("en");
    expect(screen.getByText("Relief request register")).toBeVisible();
    expect(connector).toHaveBeenCalledTimes(1);
    expect(cleanup).not.toHaveBeenCalled();
  });

  it("puts faults in the exercise page and keeps rehearsal deterministic", () => {
    vi.useFakeTimers();
    render(workspace(<ExercisePage />)); english();
    fireEvent.click(screen.getByRole("button", { name: "Fail road E3" }));
    expect(screen.getByRole("button", { name: "Fail road E3" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "Reset to seed" }));
    expect(screen.getByRole("button", { name: "Fail road E3" })).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(screen.getByRole("button", { name: "Auto replay" }));
    act(() => vi.advanceTimersByTime(1750));
    expect(screen.getByText(/Step 2 of 6/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Pause replay" }));
    act(() => vi.advanceTimersByTime(3500));
    expect(screen.getByText(/Step 2 of 6/)).toBeVisible();
    vi.useRealTimers();
  });

  it("disconnects the dashboard without claiming the field stopped", () => {
    render(workspace(<NetworkPage />)); english();
    fireEvent.click(screen.getByRole("button", { name: "Disconnect dashboard" }));
    expect(screen.getByRole("status")).toHaveTextContent("Field work continues");
    expect(screen.getByRole("button", { name: "Reconnect dashboard" })).toBeVisible();
  });

  it("separates live observations from exercise state and labels airway correctly", () => {
    const connector = (options: ObserverConnectOptions) => { options.onStatus("live"); options.onObservation(event(1, "routePlanned", { mode: "TRANSPORT_MODE_AIRWAY", edgeIds: ["A2"], etaMinutes: 8 })); return () => undefined; };
    render(workspace(<OverviewPage />, connector)); english();
    expect(screen.getAllByText("Drone • A2")).toHaveLength(2);
    expect(screen.queryByText("P0 · SIMULATED")).not.toBeInTheDocument();
    expect(screen.queryByText("Blood & medicine to Companyganj")).not.toBeInTheDocument();
    expect(screen.getByText("FIELD OBSERVATIONS")).toBeVisible();
  });

  it("searches missions and activity independently", () => {
    const view = render(workspace(<MissionsPage />)); english();
    fireEvent.change(screen.getByRole("textbox", { name: "Find a request" }), { target: { value: "missing" } });
    expect(screen.getByText("No matching requests")).toBeVisible();
    view.rerender(workspace(<ActivityPage />));
    fireEvent.change(screen.getByRole("textbox", { name: "Search events" }), { target: { value: "exercise-route" } });
    expect(screen.getByText("Route plan received")).toBeVisible();
    expect(screen.queryByText("Relief request received")).not.toBeInTheDocument();
  });

  it("does not present scenario shelters as verified facilities", () => {
    render(workspace(<ResourcesPage />)); english();
    expect(screen.getByRole("alert")).toHaveTextContent("Shelter safety is not verified");
    expect(screen.getAllByText("Unverified capacity").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Locate on map" })[0]).toHaveAttribute("href", "/map?location=N2");
    fireEvent.click(screen.getByRole("button", { name: "View field data" }));
    expect(screen.getByText("No verified resource feed connected")).toBeVisible();
    expect(screen.queryByText("Sunamganj exercise shelter")).not.toBeInTheDocument();
  });

  it("retains old projected routes and closures beyond the visible 100-event window", () => {
    let feed = emptyFeed();
    feed = receiveObservation(feed, { ...event(1, "routePlanned", { edgeIds: ["E5"], mode: "TRANSPORT_MODE_ROAD" }), simulated: true });
    feed = receiveObservation(feed, event(2, "edgeStatusChanged", { edgeId: "E3", failed: true }));
    for (let sequence = 3; sequence <= 105; sequence++) feed = receiveObservation(feed, event(sequence));
    expect(feed.recent).toHaveLength(100);
    expect(feed.projection.includesSimulated).toBe(true);
    expect(feed.projection.route?.edgeIds).toEqual(["E5"]);
    expect(feed.projection.failedEdges.has("E3")).toBe(true);
    const duplicate = receiveObservation(feed, event(1, "routePlanned", { edgeIds: ["E1"] }));
    expect(duplicate).toBe(feed);
  });

  it("resets scenario effects without changing the connection choice", () => {
    expect(scenarioReducer({ ...initialScenario, observerConnected: false, failedRoad: true }, { type: "RESET" })).toEqual({ ...initialScenario, observerConnected: false });
  });
});
