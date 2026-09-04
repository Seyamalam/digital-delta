"use client";
import { useOperations } from "./OperationsProvider";
import { MapPanel, PageHeading } from "./WorkspaceParts";
export function MapPage() {
  const { say } = useOperations();
  return <div className="hq-page"><PageHeading eyebrow={say("GEOSPATIAL WORKSPACE", "ভৌগোলিক কার্যক্ষেত্র")} title={say("See the whole operation.", "পুরো কার্যক্রম দেখুন।")} description={say("Explore routes, locations and exercise zones. Select a location to inspect its source and coordinates.", "পথ, অবস্থান ও মহড়ার অঞ্চল দেখুন। উৎস ও স্থানাঙ্ক জানতে একটি অবস্থান নির্বাচন করুন।")} /><MapPanel full /></div>;
}
