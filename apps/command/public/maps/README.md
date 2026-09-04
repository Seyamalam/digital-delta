# Offline Sylhet map region

`sylhet.pmtiles` is an offline cutout of the Protomaps v4 OpenStreetMap-derived basemap build dated 2026-09-02. It covers the Sylhet mission latitude band plus enough surrounding Bangladesh and northeast India geography to fill a 16:9 projector map (`89.0,24.1,94.6,25.3`) through zoom 13.

Runtime use requires no internet connection or commercial map API. Map data is © OpenStreetMap contributors and distributed under the Open Database License. The dashboard keeps the required attribution visible.

Reproduction command:

```bash
go install github.com/protomaps/go-pmtiles@v1.31.2
go-pmtiles extract \
  https://build.protomaps.com/20260902.pmtiles \
  apps/command/public/maps/sylhet.pmtiles \
  --bbox=89.0,24.1,94.6,25.3 \
  --maxzoom=13
go-pmtiles verify apps/command/public/maps/sylhet.pmtiles
```

Reviewed SHA-256:

```text
f45649f195b99106b3a851b456c629f0e1efd589093f80b304859ed54e3bdebc  sylhet.pmtiles
```
