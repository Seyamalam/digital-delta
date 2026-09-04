import { describe, expect, it, vi } from "vitest";
import { connectObserver, type ObserverSource } from "./observer";

class FakeSource implements ObserverSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private listeners = new Map<string, (event: MessageEvent<string>) => void>();
  close = vi.fn();

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(type, listener);
  }

  emit(type: string, data: unknown) {
    this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent<string>);
  }
}

describe("observer connection", () => {
  it("resumes from the saved sequence, reports state, and ignores duplicates", () => {
    const source = new FakeSource();
    const factory = vi.fn(() => source);
    const statuses: string[] = [];
    const observations: number[] = [];
    const storage = new Map<string, string>([["digital-delta-observer-sequence", "4"]]);

    const disconnect = connectObserver({
      url: "http://127.0.0.1:7071/observer/events",
      createSource: factory,
      readCursor: () => storage.get("digital-delta-observer-sequence"),
      writeCursor: (value) => storage.set("digital-delta-observer-sequence", value),
      onStatus: (status) => statuses.push(status),
      onObservation: (event) => observations.push(event.sequence),
    });

    expect(factory).toHaveBeenCalledWith("http://127.0.0.1:7071/observer/events?after=4");
    source.onopen?.();
    source.emit("observation", {
      sequence: 5,
      sourceNodeId: "field-n4",
      eventId: "route-5",
      kind: "routePlanned",
      occurredAtUnixMs: 1_774_000_000_000,
      simulated: false,
      presentation: { etaMinutes: 65, mode: "TRANSPORT_MODE_ROAD", edgeIds: ["E1", "E3"] },
    });
    source.emit("observation", {
      sequence: 5,
      sourceNodeId: "field-n4",
      eventId: "route-5",
      kind: "routePlanned",
      occurredAtUnixMs: 1_774_000_000_000,
      simulated: false,
    });
    source.onerror?.();
    disconnect();

    expect(statuses).toEqual(["connecting", "live", "reconnecting"]);
    expect(observations).toEqual([5]);
    expect(storage.get("digital-delta-observer-sequence")).toBe("5");
    expect(source.close).toHaveBeenCalledOnce();
  });
});
