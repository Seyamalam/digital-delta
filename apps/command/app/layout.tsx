import "@fontsource-variable/space-grotesk";
import "@fontsource-variable/noto-sans-bengali";
import "@fontsource-variable/jetbrains-mono";
import "maplibre-gl/dist/maplibre-gl.css";
import "./globals.css";

export const metadata = {
  title: "Delta Command | Disaster Operations Headquarters",
  description: "Offline-first disaster logistics command dashboard for Bangladesh.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="bn">
      <body>{children}</body>
    </html>
  );
}
