"use client";

import { useState } from "react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { missionSlaState } from "../projection";
import { useOperations } from "./OperationsProvider";
import { MissionSummary, NoData, PageHeading, SlaBadge } from "./WorkspaceParts";

export function MissionsPage() {
  const { say, exercise, projection, selectedMissionId, selectMission, now } = useOperations();
  const [query, setQuery] = useState("");
  const missions = exercise ? [{ id: "EXERCISE-P0-001", source: "N4", destination: "N4", simulated: true }] :
    [...projection.missions].map(([id, mission]) => ({
      id,
      source: mission.request?.sourceNodeId ?? mission.route?.sourceNodeId ?? "—",
      destination: typeof mission.request?.presentation?.destinationNodeId === "string" ? mission.request.presentation.destinationNodeId : "—",
      simulated: mission.request?.simulated,
    }));
  const filtered = missions.filter((mission) => `${mission.id} ${mission.source} ${mission.destination}`.toLowerCase().includes(query.toLowerCase()));
  return <div className="hq-page">
    <PageHeading eyebrow={say("MISSION CONTROL", "মিশন নিয়ন্ত্রণ")} title={say("Mission register", "মিশন তালিকা")}
      description={say("Select a mission to follow its route and SLA across headquarters. Dispatch stays on authorized field devices.", "একটি মিশন বেছে সদর দপ্তরের সব পাতায় তার পথ ও SLA দেখুন। প্রেরণ অনুমোদিত ফিল্ড ডিভাইসেই হয়।")} />
    <div className="grid items-start gap-6 2xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
    <Card className="min-w-0">
      <CardHeader><CardTitle>{say("Relief request register", "ত্রাণ অনুরোধ তালিকা")}</CardTitle>
        <CardDescription>{say("Destinations below are from the original request. A route may arrive before its request. No inferred completion statuses.", "নিচের গন্তব্য মূল অনুরোধের। অনুরোধের আগেও পথের হিসাব আসতে পারে। সমাপ্তির অবস্থা অনুমান করা হয় না।")}</CardDescription></CardHeader>
      <CardContent className="flex flex-col gap-6">
        <FieldGroup><Field><FieldLabel htmlFor="mission-search">{say("Find a mission", "মিশন খুঁজুন")}</FieldLabel>
          <Input id="mission-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={say("Mission ID or node…", "মিশন আইডি বা নোড…")} />
        </Field></FieldGroup>
        {filtered.length ? <Table><TableHeader><TableRow>
          <TableHead>{say("Mission", "মিশন")}</TableHead><TableHead>{say("Source", "উৎস")}</TableHead>
          <TableHead>{say("Requested destination", "অনুরোধের গন্তব্য")}</TableHead><TableHead>{say("SLA snapshot", "SLA হিসাব")}</TableHead>
          <TableHead>{say("Request provenance", "অনুরোধের ধরন")}</TableHead><TableHead>{say("Selection", "নির্বাচন")}</TableHead>
        </TableRow></TableHeader><TableBody>{filtered.map((mission) => <TableRow key={mission.id} data-state={!exercise && selectedMissionId === mission.id ? "selected" : undefined}>
          <TableCell><code>{mission.id}</code></TableCell><TableCell>{mission.source}</TableCell><TableCell>{mission.destination}</TableCell>
          <TableCell>{exercise ? <Badge variant="secondary">{say("Exercise", "মহড়া")}</Badge> : <SlaBadge state={missionSlaState(projection, mission.id, now)} />}</TableCell>
          <TableCell><Badge variant="secondary">{mission.simulated === undefined ? say("Request pending", "অনুরোধ আসেনি") : mission.simulated ? say("Simulated", "সিমুলেটেড") : say("Field event", "ফিল্ড তথ্য")}</Badge></TableCell>
          <TableCell>{!exercise && <Button variant="outline" aria-pressed={selectedMissionId === mission.id} aria-label={`${say("Select mission", "মিশন বাছুন")} ${mission.id}`} onClick={() => selectMission(mission.id)}>
            {selectedMissionId === mission.id ? say("Selected", "নির্বাচিত") : say("Select", "বাছুন")}
          </Button>}</TableCell>
        </TableRow>)}</TableBody></Table> : <NoData title={say("No matching missions", "কোনো মিশন মেলেনি")} detail={say("Waiting for field observations, or try a different search.", "ফিল্ড তথ্যের অপেক্ষায়, অথবা অন্যভাবে খুঁজুন।")} />}
      </CardContent>
    </Card>
    <MissionSummary linkToMap />
    </div>
  </div>;
}
