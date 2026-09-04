import "@fontsource-variable/geist";
import "@fontsource-variable/geist-mono";
import "@fontsource-variable/noto-sans-bengali";
import "maplibre-gl/dist/maplibre-gl.css";
import "./globals.css";
import { TooltipProvider } from "@/components/ui/tooltip";
import { OperationsProvider } from "@/src/operations/OperationsProvider";
import { OperationsShell } from "@/src/operations/OperationsShell";

export const metadata = {
  title: "Delta Command | Disaster Operations Headquarters",
  description: "Offline-first disaster logistics command dashboard for Bangladesh.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="bn">
      <body><TooltipProvider><OperationsProvider><OperationsShell>{children}</OperationsShell></OperationsProvider></TooltipProvider></body>
    </html>
  );
}
