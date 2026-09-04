"use client";
import { useState } from "react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useOperations } from "./OperationsProvider";
import { ActivityList, PageHeading } from "./WorkspaceParts";
export function ActivityPage() {
  const { say, projection } = useOperations();
  const [query, setQuery] = useState("");
  return <div className="hq-page"><PageHeading eyebrow={say("EVENT HISTORY", "ইভেন্ট ইতিহাস")} title={say("Every update, in order.", "প্রতিটি হালনাগাদ, ক্রমানুসারে।")} description={say("Search the latest 100 events. Older events remain in the observer log and their projected state is retained.", "সর্বশেষ ১০০টি তথ্য খুঁজুন। পুরোনো তথ্য পর্যবেক্ষকের লগে এবং তার ফল বর্তমান অবস্থায় থাকে।")} /><Card><CardHeader><CardTitle>{say("Activity timeline", "কার্যক্রমের সময়রেখা")}</CardTitle><CardDescription>{say(`Latest field sequence: ${projection.latestSequence}`, `সর্বশেষ ফিল্ড ক্রম: ${projection.latestSequence}`)}</CardDescription></CardHeader><CardContent><label htmlFor="activity-search" className="hq-search-label">{say("Search events", "ইভেন্ট খুঁজুন")}</label><Input id="activity-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={say("Event ID, source, or type…", "ইভেন্ট আইডি, উৎস বা ধরন…")} /><ActivityList query={query} /></CardContent></Card></div>;
}
