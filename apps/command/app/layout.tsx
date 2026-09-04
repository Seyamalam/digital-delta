import "@fontsource-variable/geist";
import "@fontsource-variable/geist-mono";
import "@fontsource-variable/noto-sans-bengali";
import "maplibre-gl/dist/maplibre-gl.css";
import "./globals.css";
import { TooltipProvider } from "@/components/ui/tooltip";

export const metadata = {
  title: "Delta Command | Disaster Operations Headquarters",
  description: "Offline-first disaster logistics command dashboard for Bangladesh.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="bn">
      <body><TooltipProvider>{children}</TooltipProvider></body>
    </html>
  );
}
