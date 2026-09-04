"use client";
import { useState } from "react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useOperations } from "./OperationsProvider";
import { MissionSummary, NoData, PageHeading } from "./WorkspaceParts";
export function MissionsPage() {
  const { say, exercise, projection } = useOperations();
  const [query, setQuery] = useState("");
  const requests = exercise ? [{ id: "EXERCISE-P0-001", source: "N4", destination: "N4", simulated: true }] : [...projection.requests].map(([id, observation]) => ({ id, source: observation.sourceNodeId, destination: typeof observation.presentation?.destinationNodeId === "string" ? observation.presentation.destinationNodeId : "—", simulated: observation.simulated }));
  const filtered = requests.filter((request) => `${request.id} ${request.source} ${request.destination}`.toLowerCase().includes(query.toLowerCase()));
  return <div className="hq-page"><PageHeading eyebrow={say("MISSION CONTROL", "মিশন নিয়ন্ত্রণ")} title={say("Priorities, not paperwork.", "কাগজ নয়, অগ্রাধিকার।")} description={say("Review incoming requests and the latest route. Dispatch stays on authorized field devices.", "প্রাপ্ত অনুরোধ ও সর্বশেষ পথ দেখুন। অনুমোদিত ফিল্ড ডিভাইস থেকেই প্রেরণ করা হয়।")} />
    <MissionSummary />
    <Card><CardHeader><CardTitle>{say("Relief request register", "ত্রাণ অনুরোধ তালিকা")}</CardTitle><CardDescription>{say("Search by request ID or node. No inferred completion statuses.", "অনুরোধ আইডি বা নোড দিয়ে খুঁজুন। সমাপ্তির অবস্থা অনুমান করা হয় না।")}</CardDescription></CardHeader><CardContent><label className="hq-search-label" htmlFor="mission-search">{say("Find a request", "অনুরোধ খুঁজুন")}</label><Input id="mission-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={say("Request ID or node…", "অনুরোধ আইডি বা নোড…")} />
      {filtered.length ? <Table><TableHeader><TableRow><TableHead>{say("Request", "অনুরোধ")}</TableHead><TableHead>{say("Source", "উৎস")}</TableHead><TableHead>{say("Destination", "গন্তব্য")}</TableHead><TableHead>{say("Provenance", "তথ্যের ধরন")}</TableHead></TableRow></TableHeader><TableBody>{filtered.map((request) => <TableRow key={request.id}><TableCell><code>{request.id}</code></TableCell><TableCell>{request.source}</TableCell><TableCell>{request.destination}</TableCell><TableCell><Badge variant="secondary">{request.simulated ? say("Simulated", "সিমুলেটেড") : say("Field event", "ফিল্ড তথ্য")}</Badge></TableCell></TableRow>)}</TableBody></Table> : <NoData title={say("No matching requests", "কোনো অনুরোধ মেলেনি")} detail={say("Waiting for a request from the observer, or try a different search.", "পর্যবেক্ষকের অনুরোধের অপেক্ষায়, অথবা অন্যভাবে খুঁজুন।")} />}</CardContent></Card>
  </div>;
}
