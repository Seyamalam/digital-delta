"use client";

import { lazy, Suspense, type ReactNode } from "react";
import Link from "next/link";
import { ArrowUpRight, Clock3, RadioTower, Route, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Empty, EmptyHeader, EmptyTitle, EmptyDescription } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { useOperations } from "./OperationsProvider";

const GeographicMap = lazy(() => import("../OfflineDeltaMap").then((m) => ({ default: m.OfflineDeltaMap })));

export function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <header className="hq-page-heading"><div><span className="hq-eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{action}</header>;
}

export function NoData({ title, detail }: { title: string; detail: string }) {
  return <Empty><EmptyHeader><EmptyTitle>{title}</EmptyTitle><EmptyDescription>{detail}</EmptyDescription></EmptyHeader></Empty>;
}

export function MapPanel({ full = false }: { full?: boolean }) {
  const { language, edgeIds, failedEdges, edgeRisks, exercise, projection, routeLabel, say } = useOperations();
  return <Card className={full ? "hq-map-panel hq-map-panel-full" : "hq-map-panel"}>
    <CardHeader><CardTitle>{say("Sylhet operating area", "সিলেট কার্যক্রম এলাকা")}</CardTitle><CardDescription>{say("OpenStreetMap geography · locally bundled · not a flood forecast", "OpenStreetMap ভূগোল · স্থানীয় সংরক্ষণ · বন্যার পূর্বাভাস নয়")}</CardDescription></CardHeader>
    <CardContent><Suspense fallback={<Skeleton className="h-full min-h-96 w-full" />}><GeographicMap useWaterRoute={false} showRisk={false} language={language} exerciseOverlay={exercise} simulated={exercise || (projection.route?.simulated ?? false)} edgeIds={edgeIds} failedEdgeIds={[...failedEdges]} edgeRisks={Object.fromEntries(edgeRisks)} rendezvous={exercise ? undefined : projection.rendezvous} /></Suspense></CardContent>
    <CardFooter><span><Route aria-hidden="true" />{routeLabel}</span>{!full && <Button asChild variant="outline"><Link href="/map">{say("Open map", "মানচিত্র খুলুন")}<ArrowUpRight data-icon="inline-end" /></Link></Button>}</CardFooter>
  </Card>;
}

export function Metrics() {
  const { exercise, projection, eta, failedEdges, edgeRisks, say } = useOperations();
  const items = [
    { title: say("Relief requests", "ত্রাণ অনুরোধ"), value: exercise ? "01" : String(projection.requests.size).padStart(2, "0"), detail: exercise ? say("P0 · exercise mission", "P0 · মহড়ার মিশন") : say("Requests received by observer", "পর্যবেক্ষকে প্রাপ্ত অনুরোধ"), icon: Route },
    { title: say("Route ETA", "পথের আনুমানিক সময়"), value: eta === undefined ? "—" : `${eta}`, detail: say("Minutes · latest route estimate", "মিনিট · সর্বশেষ পথের হিসাব"), icon: Clock3 },
    { title: say("Attention needed", "মনোযোগ প্রয়োজন"), value: String(new Set([...failedEdges, ...edgeRisks.keys()]).size).padStart(2, "0"), detail: say("Closed or predicted-risk edges", "বন্ধ বা ঝুঁকি-পূর্বাভাসের পথ"), icon: TriangleAlert },
    { title: say("Reporting sources", "তথ্য প্রদানকারী উৎস"), value: exercise ? "03" : String(projection.sources.size).padStart(2, "0"), detail: say("Seen sources, not online presence", "প্রাপ্ত উৎস, অনলাইন উপস্থিতি নয়"), icon: RadioTower },
  ];
  return <section className="hq-metrics" aria-label={say("Operational summary", "কার্যক্রম সারসংক্ষেপ")}>{items.map(({ title, value, detail, icon: Icon }) => <Card key={title}><CardHeader><CardDescription>{title}</CardDescription><Icon className="hq-metric-icon" aria-hidden="true" /></CardHeader><CardContent><strong className="hq-metric-number">{value}</strong><p>{detail}</p></CardContent></Card>)}</section>;
}

export function MissionSummary() {
  const { exercise, projection, routeLabel, eta, failedEdges, edgeRisks, say } = useOperations();
  return <Card><CardHeader><CardDescription>{say("PRIORITY WORK", "অগ্রাধিকার কাজ")}</CardDescription><CardTitle>{say("Relief delivery", "ত্রাণ সরবরাহ")}</CardTitle></CardHeader><CardContent>
    {!exercise && !projection.route && !projection.requests.size ? <NoData title={say("No mission received", "কোনো মিশন পাওয়া যায়নি")} detail={say("Waiting for field observations. Seeded missions are kept separate.", "ফিল্ড পর্যবেক্ষণের অপেক্ষায়। মহড়ার মিশন আলাদা রাখা হয়।")} /> : <div className="hq-mission-summary"><Badge variant="secondary">{exercise ? say("P0 · SIMULATED", "P0 · সিমুলেটেড") : projection.includesSimulated ? say("SIMULATED OBSERVATIONS", "সিমুলেটেড পর্যবেক্ষণ") : say("FIELD RECORD", "ফিল্ড রেকর্ড")}</Badge><h3>{exercise ? say("Blood & medicine to Companyganj", "কোম্পানীগঞ্জে রক্ত ও ওষুধ") : projection.route?.vehicleId ?? say("Incoming relief request", "প্রাপ্ত ত্রাণ অনুরোধ")}</h3><p>{routeLabel}</p><dl><div><dt>{say("ETA", "আনুমানিক সময়")}</dt><dd>{eta === undefined ? "—" : `${eta} ${say("min", "মিনিট")}`}</dd></div><div><dt>{say("Closures", "বন্ধ পথ")}</dt><dd>{failedEdges.size}</dd></div><div><dt>{say("Predicted risks", "পূর্বাভাসিত ঝুঁকি")}</dt><dd>{edgeRisks.size}</dd></div></dl></div>}
  </CardContent><CardFooter><Button asChild variant="outline"><Link href="/missions">{say("Review missions", "মিশন দেখুন")}<ArrowUpRight data-icon="inline-end" /></Link></Button></CardFooter></Card>;
}

export function ActivityList({ limit = 100, query = "" }: { limit?: number; query?: string }) {
  const { exercise, observations, state, say, language } = useOperations();
  const seeded = [
    { eventId: "exercise-request", kind: "reliefRequestCreated", sourceNodeId: "N4", sequence: 1, simulated: true, occurredAtUnixMs: Date.UTC(2026, 3, 12, 2, 1, 4) },
    { eventId: "exercise-route", kind: "routePlanned", sourceNodeId: "N1", sequence: 2, simulated: true, occurredAtUnixMs: Date.UTC(2026, 3, 12, 2, 1, 6) },
    ...(state.failedRoad ? [{ eventId: "exercise-closure", kind: "edgeStatusChanged", sourceNodeId: "N2", sequence: 3, simulated: true, occurredAtUnixMs: Date.UTC(2026, 3, 12, 2, 1, 22) }] : []),
  ];
  const names: Record<string, string> = {
    reliefRequestCreated: say("Relief request received", "ত্রাণ অনুরোধ গ্রহণ করা হয়েছে"), routePlanned: say("Route plan received", "পথের পরিকল্পনা গ্রহণ করা হয়েছে"), edgeStatusChanged: say("Edge status changed", "পথের অবস্থা পরিবর্তিত"), edgeRiskPredicted: say("Route risk prediction", "পথের ঝুঁকি পূর্বাভাস"), vehicleStateChanged: say("Vehicle state changed", "যানের অবস্থা পরিবর্তিত"), rendezvousPlanned: say("Handoff plan received", "হস্তান্তর পরিকল্পনা গ্রহণ করা হয়েছে"),
  };
  const events = (exercise ? seeded : observations).filter((item) => `${item.eventId} ${item.sourceNodeId} ${item.kind} ${names[item.kind] ?? ""}`.toLowerCase().includes(query.toLowerCase())).slice(-limit).reverse();
  if (!events.length) return <NoData title={say("No matching events", "কোনো মিল পাওয়া যায়নি")} detail={say("Events will appear here after the observer receives them.", "পর্যবেক্ষকে তথ্য এলে এখানে দেখা যাবে।")} />;
  return <ol className="hq-activity-list">{events.map((event) => <li key={event.eventId}><span className="hq-event-mark"><RadioTower aria-hidden="true" /></span><div><strong>{names[event.kind] ?? say("Field event received", "ফিল্ড তথ্য গ্রহণ করা হয়েছে")}</strong><span>{event.sourceNodeId} · #{event.sequence} · {event.simulated ? say("SIMULATED", "সিমুলেটেড") : say("FIELD EVENT", "ফিল্ড ইভেন্ট")}</span><code>{event.eventId}</code></div><time dateTime={new Date(event.occurredAtUnixMs).toISOString()}>{new Date(event.occurredAtUnixMs).toLocaleTimeString(language === "bn" ? "bn-BD" : "en-GB", { hour: "2-digit", minute: "2-digit", timeZone: "Asia/Dhaka" })}</time></li>)}</ol>;
}
