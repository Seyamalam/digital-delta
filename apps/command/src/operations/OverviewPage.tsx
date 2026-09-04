"use client";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useOperations } from "./OperationsProvider";
import { ActivityList, MapPanel, Metrics, MissionSummary, PageHeading } from "./WorkspaceParts";

export function OverviewPage() {
  const { say } = useOperations();
  return <div className="hq-page"><PageHeading eyebrow={say("COMMAND OVERVIEW", "কমান্ড সারসংক্ষেপ")} title={say("Every response starts here.", "প্রতিটি সাড়ার শুরু এখানেই।")} description={say("The situation, the priority, and the next decision. One shared operating picture.", "পরিস্থিতি, অগ্রাধিকার ও পরবর্তী সিদ্ধান্ত। সবার জন্য একই কার্যচিত্র।")} action={<Button asChild><Link href="/map">{say("Open live map", "লাইভ মানচিত্র খুলুন")}<ArrowUpRight data-icon="inline-end" /></Link></Button>} />
    <Metrics />
    <div className="hq-overview-grid"><MapPanel /><MissionSummary /></div>
    <Card><CardHeader><CardTitle>{say("Latest activity", "সাম্প্রতিক কার্যক্রম")}</CardTitle><CardDescription>{say("The latest three observations. Open Activity log for the searchable timeline.", "সর্বশেষ তিনটি পর্যবেক্ষণ। অনুসন্ধান করতে কার্যক্রম লগ খুলুন।")}</CardDescription></CardHeader><CardContent><ActivityList limit={3} /><Button asChild variant="link"><Link href="/activity">{say("View activity log", "কার্যক্রম লগ দেখুন")}<ArrowUpRight data-icon="inline-end" /></Link></Button></CardContent></Card>
  </div>;
}
