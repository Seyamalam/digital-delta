export type LocationCategory = "hub" | "supply" | "shelter" | "medical" | "waypoint";
export type OperationalLocation = {
  id: string;
  name: { en: string; bn: string };
  description: { en: string; bn: string };
  coordinates: [number, number];
  category: LocationCategory;
};
const scenarioDescription = { en: "Project scenario location. Not an officially verified facility or shelter.", bn: "প্রকল্পের দৃশ্যের অবস্থান। সরকারি যাচাইকৃত স্থাপনা বা আশ্রয়কেন্দ্র নয়।" };
export const operationalLocations: OperationalLocation[] = [
  { id: "N1", name: { en: "Sylhet coordination hub", bn: "সিলেট সমন্বয় কেন্দ্র" }, coordinates: [91.8687, 24.8949], category: "hub", description: scenarioDescription },
  { id: "N2", name: { en: "Osmani supply point", bn: "ওসমানী সরবরাহ কেন্দ্র" }, coordinates: [91.8668, 24.9632], category: "supply", description: scenarioDescription },
  { id: "N3", name: { en: "Sunamganj exercise shelter", bn: "সুনামগঞ্জ মহড়ার আশ্রয়কেন্দ্র" }, coordinates: [91.4073, 25.0658], category: "shelter", description: scenarioDescription },
  { id: "N4", name: { en: "Companyganj outpost", bn: "কোম্পানীগঞ্জ ফিল্ড কেন্দ্র" }, coordinates: [91.7554, 25.0715], category: "medical", description: scenarioDescription },
  { id: "N5", name: { en: "Kanaighat waypoint", bn: "কানাইঘাট পথবিন্দু" }, coordinates: [92.2611, 24.9945], category: "waypoint", description: scenarioDescription },
  { id: "N6", name: { en: "Habiganj medical node", bn: "হবিগঞ্জ চিকিৎসা নোড" }, coordinates: [91.4169, 24.3840], category: "medical", description: scenarioDescription },
  { id: "N7", name: { en: "Haor exercise clinic", bn: "হাওর মহড়ার ক্লিনিক" }, coordinates: [91.68, 25.12], category: "medical", description: scenarioDescription },
];

// Hand-authored exercise geometry. Never use these shapes as actual safety advice.
export const exerciseZones = {
  type: "FeatureCollection" as const,
  features: [
    { type: "Feature" as const, properties: { id: "exercise-hazard", kind: "hazard", label: "Exercise flood-impact zone", simulated: true }, geometry: { type: "Polygon" as const, coordinates: [[[91.64, 25.02], [91.77, 25.035], [91.80, 25.13], [91.67, 25.16], [91.60, 25.09], [91.64, 25.02]]] } },
    { type: "Feature" as const, properties: { id: "exercise-assembly", kind: "assembly", label: "Exercise assembly area - safety unverified", simulated: true }, geometry: { type: "Polygon" as const, coordinates: [[[91.842, 24.875], [91.888, 24.877], [91.892, 24.917], [91.85, 24.922], [91.842, 24.875]]] } },
  ],
};
