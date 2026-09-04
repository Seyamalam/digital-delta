export type ObserverStatus = "connecting" | "live" | "reconnecting";

export type PresentationObservation = {
  sequence: number;
  sourceNodeId: string;
  eventId: string;
  kind: string;
  occurredAtUnixMs: number;
  simulated: boolean;
  scenarioSeed?: string;
  presentation?: Record<string, unknown>;
};

export interface ObserverSource {
  onopen: (() => void) | null;
  onerror: (() => void) | null;
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void;
  close(): void;
}

export type ObserverConnectOptions = {
  url: string;
  createSource?: (url: string) => ObserverSource;
  readCursor?: () => string | undefined | null;
  writeCursor?: (value: string) => void;
  onStatus: (status: ObserverStatus) => void;
  onObservation: (event: PresentationObservation) => void;
};

const cursorKey = "digital-delta-observer-sequence";

export function connectObserver(options: ObserverConnectOptions): () => void {
  // The dashboard is a disposable projection. Rebuild it from the durable observer
  // log after a page reload instead of persisting a cursor without its projection.
  const readCursor = options.readCursor ?? (() => null);
  const writeCursor = options.writeCursor ?? (() => undefined);
  const createSource = options.createSource ?? ((url) => new EventSource(url));
  let cursor = parseCursor(readCursor());
  const separator = options.url.includes("?") ? "&" : "?";
  const streamUrl = cursor > 0 ? `${options.url}${separator}after=${cursor}` : options.url;

  options.onStatus("connecting");
  const source = createSource(streamUrl);
  source.onopen = () => options.onStatus("live");
  source.onerror = () => options.onStatus("reconnecting");
  source.addEventListener("observation", (message) => {
    const observation = parseObservation(message.data);
    if (!observation || observation.sequence <= cursor) return;
    cursor = observation.sequence;
    writeCursor(String(cursor));
    options.onObservation(observation);
  });
  return () => source.close();
}

function parseCursor(value: string | undefined | null): number {
  if (!value) return 0;
  const cursor = Number(value);
  return Number.isSafeInteger(cursor) && cursor >= 0 ? cursor : 0;
}

function parseObservation(value: string): PresentationObservation | null {
  try {
    const candidate = JSON.parse(value) as Partial<PresentationObservation>;
    if (!Number.isSafeInteger(candidate.sequence) || Number(candidate.sequence) <= 0) return null;
    if (typeof candidate.sourceNodeId !== "string" || candidate.sourceNodeId.length === 0) return null;
    if (typeof candidate.eventId !== "string" || candidate.eventId.length === 0) return null;
    if (typeof candidate.kind !== "string" || candidate.kind.length === 0) return null;
    if (typeof candidate.occurredAtUnixMs !== "number") return null;
    if (typeof candidate.simulated !== "boolean") return null;
    return candidate as PresentationObservation;
  } catch {
    return null;
  }
}
