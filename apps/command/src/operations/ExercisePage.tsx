"use client";
import { FlaskConical, Pause, Play, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Alert, AlertTitle, AlertDescription } from "@/components/ui/alert";
import { useOperations } from "./OperationsProvider";
import { ActivityList, MapPanel, PageHeading } from "./WorkspaceParts";
export function ExercisePage() {
  const { say, state, dispatch, exercise, setMode, isReplaying, setIsReplaying, t } = useOperations();
  return <div className="hq-page"><PageHeading eyebrow={say("TRAINING ONLY", "শুধু প্রশিক্ষণ")} title={say("Practice under pressure.", "চাপের মধ্যেই অনুশীলন।")} description={say("Deterministic scenario controls, isolated from the field log. Nothing here dispatches a real mission.", "ফিল্ড লগ থেকে আলাদা নির্ধারিত মহড়া নিয়ন্ত্রণ। এখানে বাস্তব মিশন পাঠানো হয় না।")} />
    <Alert><FlaskConical /><AlertTitle>{say("Simulated environment and vehicles", "সিমুলেটেড পরিবেশ ও যানবাহন")}</AlertTitle><AlertDescription>{say("These controls demonstrate dashboard reactions. They are not evidence of cryptographic verification or physical delivery.", "এই নিয়ন্ত্রণ ড্যাশবোর্ডের প্রতিক্রিয়া প্রদর্শন করে। এগুলো ক্রিপ্টোগ্রাফিক যাচাই বা বাস্তব সরবরাহের প্রমাণ নয়।")}</AlertDescription></Alert>
    {!exercise && <Button onClick={() => setMode("exercise")}>{say("Switch to exercise data", "মহড়ার তথ্য চালু করুন")}</Button>}
    <div className="hq-exercise-grid"><Card><CardHeader><CardTitle>{say("Scenario controls", "মহড়া নিয়ন্ত্রণ")}</CardTitle><CardDescription>{say(`Step ${state.step + 1} of 6 · Seed 20260412`, `ধাপ ${state.step + 1} / ৬ · বীজ 20260412`)}</CardDescription></CardHeader><CardContent><div className="hq-lab-controls"><Button disabled={!exercise} onClick={() => dispatch({ type: "STEP" })}><Play data-icon="inline-start" />{t.step}</Button><Button disabled={!exercise} variant="outline" onClick={() => setIsReplaying(!isReplaying)}>{isReplaying ? <Pause data-icon="inline-start" /> : <Play data-icon="inline-start" />}{isReplaying ? t.pauseReplay : t.autoReplay}</Button><Button variant="outline" onClick={() => { setIsReplaying(false); dispatch({ type: "RESET" }); }}><RotateCcw data-icon="inline-start" />{t.reset}</Button>
      <label>{t.rainfall}<strong>{state.rainfallMmPerHour} mm/h</strong><input aria-label={t.rainfall} disabled={!exercise} type="range" min={0} max={140} value={state.rainfallMmPerHour} onChange={(event) => dispatch({ type: "RAINFALL", value: Number(event.target.value) })} /></label>
      <label>{t.saturation}<strong>{state.soilSaturationPercent}%</strong><input aria-label={t.saturation} disabled={!exercise} type="range" min={0} max={100} value={state.soilSaturationPercent} onChange={(event) => dispatch({ type: "SATURATION", value: Number(event.target.value) })} /></label>
      <Button disabled={!exercise} variant="outline" aria-pressed={state.failedRoad} onClick={() => dispatch({ type: "FLOOD" })}>{t.road}</Button><Button disabled={!exercise} variant="outline" aria-pressed={state.vehicleDelayed} onClick={() => dispatch({ type: "DELAY" })}>{t.delayBoat}</Button><Button disabled={!exercise} variant="outline" aria-pressed={state.predictedRisk} onClick={() => dispatch({ type: "RISK" })}>{t.risk}</Button>
    </div></CardContent></Card><MapPanel /></div><Card><CardHeader><CardTitle>{say("Exercise timeline", "মহড়ার সময়রেখা")}</CardTitle></CardHeader><CardContent><ActivityList /></CardContent></Card>
  </div>;
}
