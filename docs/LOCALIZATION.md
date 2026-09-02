# Bangla and English field interface requirements

## Policy

Bangla and English ship together in the application bundle. Neither language requires internet, account access, or a later download. Bangla receives the same design, testing, accessibility, and release status as English.

The first-run screen lists `বাংলা` before `English` because the initial field setting is Bangladesh. This ordering does not make English incomplete or secondary.

## Language behavior

- Users can change language from first run, sign-in, and settings.
- The selected language persists per device.
- A language change updates visible text immediately.
- A language change does not clear navigation state, forms, drafts, queues, routes, or cryptographic sessions.
- Protocol payloads use language-neutral codes. User interfaces translate those codes locally.
- User-authored notes retain their original language.
- The receiver may view user-authored text as entered. Runtime machine translation is not required and must not be implied.

## Field terminology

The glossary is authoritative. Translators should prefer common, short field language over literal technical translations.

| Code or English | Bangla interface | Notes |
|---|---|---|
| Offline | অফলাইন | Keep the common borrowed term |
| Nearby connected | কাছাকাছি সংযুক্ত | Use with connection icon |
| Syncing | সিঙ্ক হচ্ছে | Short status text |
| Conflict detected | দ্বন্দ্ব শনাক্ত | Explain the conflicting values below |
| Verified | যাচাইকৃত | Applies after signature or identity verification |
| Verification failed | যাচাই ব্যর্থ | Always include the reason |
| Action required | পদক্ষেপ প্রয়োজন | Avoid vague warning-only text |
| Emergency request | জরুরি অনুরোধ | Cargo or assistance request |
| Priority | অগ্রাধিকার | Keep P0 to P3 unchanged |
| Critical medical | জরুরি চিকিৎসা | P0 label |
| High priority | উচ্চ অগ্রাধিকার | P1 label |
| Standard | সাধারণ | P2 label |
| Low priority | নিম্ন অগ্রাধিকার | P3 label |
| Route | পথ | Use `রুট` only when space or convention requires it |
| Road | সড়কপথ | Transport edge |
| Waterway | নৌপথ | Transport edge |
| Airway | আকাশপথ | Transport edge |
| Boat | নৌযান | Use `নৌকা` for simple field copy when appropriate |
| Drone | ড্রোন | Always add simulated state in this fair build |
| Delivery | সরবরাহ | Context may require `পৌঁছে দেওয়া` |
| Proof of delivery | সরবরাহ গ্রহণের প্রমাণ | Long form for help text |
| Chain of custody | হস্তান্তরের ধারাবাহিক রেকর্ড | Avoid unexplained legal wording |
| Relay | রিলে | Help text explains that another phone carries the message |
| Message expired | বার্তার মেয়াদ শেষ | Include retry or recreate action |
| Replay rejected | পুনরায় ব্যবহার প্রত্যাখ্যাত | Explain that the code was already used |
| Flooded | প্লাবিত | Confirmed or simulated edge state |
| Predicted risk | পূর্বাভাসকৃত ঝুঁকি | Never shorten to confirmed flood |
| Simulated | সিমুলেটেড | Must remain visible on simulated feeds and vehicles |
| Human confirmation | মানুষের অনুমোদন | Use `সমন্বয়কারীর অনুমোদন` for coordinator actions |

## Numbers, codes, and dates

- Keep P0 to P3 unchanged in both languages.
- Keep coordinates, hashes, key fingerprints, delivery IDs, and protocol versions in Latin digits and ASCII.
- Display local date and time in a readable interface format, then show ISO 8601 in audit details.
- Avoid converting identifiers into Bengali numerals because operators may need to read them across radios, documents, and systems.
- Translate units and surrounding labels, not machine identifiers.

## Typography

- Bundle a Bengali font that renders যুক্তাক্ষর, কার, ফলা, and combining marks correctly.
- Record the font license in the repository.
- Test normal, bold, and large accessibility sizes.
- Do not use artificial letter spacing on Bangla text.
- Allow at least 35 percent text expansion compared with the English design.
- Avoid fixed-height controls for translated labels.
- Use line height that prevents upper and lower marks from clipping.

## Layout and responsive behavior

Test every critical screen at:

- 360 by 800 mobile;
- 412 by 915 mobile;
- 768 by 1024 tablet;
- 1366 by 768 laptop and projector;
- 1920 by 1080 projector.

At each size:

- primary action remains visible or reachable without hidden gestures;
- status does not overlap the title or map;
- Bangla wraps at sensible word boundaries;
- modal actions remain fully visible with large text;
- left-to-right numbers and IDs remain readable inside Bangla sentences.

## Translation workflow

1. Developers add a stable Android string-resource key, English source, context, and screenshot reference.
2. A fluent Bangla reviewer translates and checks field meaning.
3. The build rejects missing critical keys.
4. Screenshot tests render both languages.
5. A human reviews truncation and unintended ambiguity.
6. Release notes identify changed safety-critical translations.

Suggested localization record:

```csv
key,english,bangla,context,critical,reviewer,status
status.offline,Offline,অফলাইন,global connectivity state,true,,draft
```

The Android source of truth uses `values/strings.xml` and `values-bn/strings.xml`. Proto DataStore persists the selected language. Compose screens receive localized resources through the Android resource system rather than storing translated display text in Room or mesh events.

## Critical-screen release gate

The release fails if any of these screens has a missing, untranslated, clipped, or misleading string:

- language selection;
- identity provisioning and sign-in;
- emergency request creation;
- priority and SLA warning;
- route failure and route change;
- conflict resolution;
- QR generation and verification;
- tamper and replay rejection;
- custody transfer;
- human confirmation;
- simulated-data labels;
- offline, syncing, and action-required states.

## Bangla review questions

- Would a field volunteer understand the action without reading the English version?
- Does the translation state what happened and what to do next?
- Is a borrowed technical term more familiar than its formal translation?
- Could the wording make a prediction sound confirmed?
- Could the wording hide that a vehicle or environmental feed is simulated?
- Does the screen remain usable for someone reading slowly under pressure?
