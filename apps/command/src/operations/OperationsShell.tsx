"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, type CSSProperties, type ReactNode } from "react";
import { Activity, ArrowUpRight, FlaskConical, House, Languages, Map, Package, RadioTower, Route, ShieldCheck, Waves } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader, SidebarInset, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarTrigger, useSidebar } from "@/components/ui/sidebar";
import { useOperations } from "./OperationsProvider";

export const destinations = [
  { href: "/", en: "Overview", bn: "সারসংক্ষেপ", icon: House },
  { href: "/map", en: "Live map", bn: "লাইভ মানচিত্র", icon: Map },
  { href: "/missions", en: "Missions", bn: "মিশনসমূহ", icon: Route },
  { href: "/resources", en: "Resources & shelters", bn: "সম্পদ ও আশ্রয়কেন্দ্র", icon: Package },
  { href: "/network", en: "Field network", bn: "ফিল্ড নেটওয়ার্ক", icon: RadioTower },
  { href: "/activity", en: "Activity log", bn: "কার্যক্রম লগ", icon: Activity },
  { href: "/exercise", en: "Exercise lab", bn: "মহড়া পরীক্ষাগার", icon: FlaskConical },
] as const;

function Navigation() {
  const pathname = usePathname();
  const { language, say } = useOperations();
  const { setOpenMobile } = useSidebar();
  useEffect(() => { setOpenMobile(false); }, [pathname, setOpenMobile]);
  return <Sidebar collapsible="offcanvas" aria-label={say("Navigation", "নেভিগেশন")}>
    <SidebarHeader className="hq-brand-space">
      <Link href="/" className="hq-brand"><span className="hq-brand-mark"><Waves aria-hidden="true" /></span><span><strong>{say("Delta Command", "ডেল্টা কমান্ড")}</strong><small>{say("DISASTER RESPONSE", "দুর্যোগ মোকাবিলা")}</small></span></Link>
    </SidebarHeader>
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupLabel>{say("OPERATIONS", "কার্যক্রম")}</SidebarGroupLabel>
        <SidebarGroupContent><SidebarMenu>
          {destinations.slice(0, 6).map(({ href, en, bn, icon: Icon }) => <SidebarMenuItem key={href}>
            <SidebarMenuButton asChild size="lg" isActive={pathname === href}><Link href={href} aria-current={pathname === href ? "page" : undefined}><Icon /><span>{language === "en" ? en : bn}</span></Link></SidebarMenuButton>
          </SidebarMenuItem>)}
        </SidebarMenu></SidebarGroupContent>
      </SidebarGroup>
      <SidebarGroup><SidebarGroupLabel>{say("TRAINING", "প্রশিক্ষণ")}</SidebarGroupLabel><SidebarGroupContent><SidebarMenu><SidebarMenuItem>
        <SidebarMenuButton asChild size="lg" isActive={pathname === "/exercise"}><Link href="/exercise" aria-current={pathname === "/exercise" ? "page" : undefined}><FlaskConical /><span>{say("Exercise lab", "মহড়া পরীক্ষাগার")}</span></Link></SidebarMenuButton>
      </SidebarMenuItem></SidebarMenu></SidebarGroupContent></SidebarGroup>
    </SidebarContent>
    <SidebarFooter className="hq-sidebar-bottom"><div className="hq-independence"><ShieldCheck aria-hidden="true" /><strong>{say("Field-first. Always.", "ফিল্ডের কাজ আগে।")}</strong><p>{say("Phones keep working without this dashboard or the internet.", "ড্যাশবোর্ড বা ইন্টারনেট ছাড়াই ফোনের কাজ চলবে।")}</p></div><div className="hq-region"><span className="hq-region-dot" /><span>{say("Sylhet, Bangladesh", "সিলেট, বাংলাদেশ")}<small>24.8949°N · 91.8687°E</small></span></div></SidebarFooter>
  </Sidebar>;
}

export function OperationsShell({ children }: { children: ReactNode }) {
  const { language, setLanguage, say, observerLabel, connected, exercise, mode, setMode, projection } = useOperations();
  const pathname = usePathname();
  const destination = destinations.find((item) => item.href === pathname) ?? destinations[0];
  return <SidebarProvider className="hq-shell" data-language={language} style={{ "--sidebar-width": "17rem" } as CSSProperties}>
    <a className="skip-link" href="#workspace">{say("Skip to workspace", "মূল কার্যক্ষেত্রে যান")}</a>
    <Navigation />
    <SidebarInset className="min-w-0">
      <header className="hq-topbar"><div className="hq-breadcrumb"><SidebarTrigger aria-label={say("Toggle navigation", "নেভিগেশন খুলুন বা বন্ধ করুন")} /><span>{say("Sylhet operations", "সিলেট কার্যক্রম")}</span><span aria-hidden="true">/</span><strong>{language === "en" ? destination.en : destination.bn}</strong></div><div className="hq-topbar-actions"><Badge variant="outline"><RadioTower data-icon="inline-start" />{observerLabel}</Badge><Button className="language" variant="outline" onClick={() => setLanguage(language === "bn" ? "en" : "bn")}><Languages data-icon="inline-start" />{language === "bn" ? "English" : "বাংলা"}</Button></div></header>
      <div className="hq-source-bar" data-source={exercise ? "exercise" : connected ? "live" : "stale"}>
        <span><span className="hq-source-dot" /><strong>{exercise ? say("EXERCISE DATA", "মহড়ার তথ্য") : connected ? say("FIELD OBSERVATIONS", "ফিল্ড পর্যবেক্ষণ") : say("LAST RECEIVED STATE", "সর্বশেষ প্রাপ্ত অবস্থা")}</strong><span>{exercise ? say("Seeded scenario. Not a live emergency.", "নির্ধারিত দৃশ্য। বাস্তব জরুরি অবস্থা নয়।") : say(`Sequence ${projection.latestSequence} · Read-only headquarters`, `ক্রম ${projection.latestSequence} · শুধু পর্যবেক্ষণ`)}</span></span>
        <Button variant="ghost" onClick={() => setMode(mode === "field" ? "exercise" : "field")}>{exercise ? say("View field data", "ফিল্ডের তথ্য দেখুন") : say("View exercise", "মহড়া দেখুন")}<ArrowUpRight data-icon="inline-end" /></Button>
      </div>
      <div id="workspace" tabIndex={-1} className="hq-workspace">{children}</div>
      <footer className="hq-footer"><span>{say("Observer only. Field devices remain the source of truth.", "শুধু পর্যবেক্ষণ। ফিল্ড ডিভাইসেই মূল তথ্য সংরক্ষিত।")}</span><span>{say("Bangla + English · Offline-ready maps", "বাংলা + English · অফলাইন মানচিত্র")}</span></footer>
    </SidebarInset>
  </SidebarProvider>;
}
