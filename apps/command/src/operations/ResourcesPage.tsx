"use client";
import Link from "next/link";
import { MapPin, ShieldAlert } from "lucide-react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertTitle, AlertDescription } from "@/components/ui/alert";
import { useOperations } from "./OperationsProvider";
import { NoData, PageHeading } from "./WorkspaceParts";
import { operationalLocations } from "./mapLayers";
export function ResourcesPage() {
  const { say, language, exercise } = useOperations();
  return <div className="hq-page"><PageHeading eyebrow={say("RESOURCE DIRECTORY", "সম্পদ তালিকা")} title={say("Know where help can be found.", "সহায়তা কোথায় আছে জানুন।")} description={say("Supply points, medical nodes and exercise shelters, with explicit verification status.", "সরবরাহ কেন্দ্র, চিকিৎসা নোড ও মহড়ার আশ্রয়কেন্দ্র, যাচাইয়ের অবস্থা সহ।")} />
    <Alert><ShieldAlert /><AlertTitle>{say("Shelter safety is not verified", "আশ্রয়কেন্দ্রের নিরাপত্তা যাচাই করা হয়নি")}</AlertTitle><AlertDescription>{say("These are project scenario locations, not an official shelter directory. Capacity, access and safety must be confirmed in the field.", "এগুলো প্রকল্পের দৃশ্যের অবস্থান, সরকারি আশ্রয়কেন্দ্রের তালিকা নয়। ধারণক্ষমতা, প্রবেশপথ ও নিরাপত্তা মাঠে যাচাই করতে হবে।")}</AlertDescription></Alert>
    {!exercise ? <NoData title={say("No verified resource feed connected", "যাচাইকৃত সম্পদের উৎস সংযুক্ত নেই")} detail={say("Switch to exercise data to explore the labelled demonstration directory.", "চিহ্নিত প্রদর্শনী তালিকা দেখতে মহড়ার তথ্য চালু করুন।")} /> : <div className="hq-resource-grid">{operationalLocations.filter((location) => ["shelter", "medical", "supply"].includes(location.category)).map((location) => <Card key={location.id}><CardHeader><CardDescription>{location.id} · {say("EXERCISE LOCATION", "মহড়ার অবস্থান")}</CardDescription><CardTitle>{location.name[language]}</CardTitle></CardHeader><CardContent><Badge variant="outline">{say("Unverified capacity", "ধারণক্ষমতা যাচাই হয়নি")}</Badge><p className="hq-coordinate">{location.coordinates[1].toFixed(4)}°N, {location.coordinates[0].toFixed(4)}°E</p><p>{location.description[language]}</p></CardContent><CardFooter><Button asChild variant="outline"><Link href={`/map?location=${location.id}`}><MapPin data-icon="inline-start" />{say("Locate on map", "মানচিত্রে দেখুন")}</Link></Button></CardFooter></Card>)}</div>}
  </div>;
}
