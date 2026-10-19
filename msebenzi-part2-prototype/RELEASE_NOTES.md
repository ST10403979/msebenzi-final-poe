# Release notes

## Version 3.0.0 - Final PoE (October 2026)

Everything that changed since the Part 2 prototype (v2). 🌟 marks the **innovative features** added in this version.

### 🌟 New in this version
| Feature | What it does |
|---|---|
| 🌟 **Services gallery with pictures** | Eight original illustrated service cards (Cleaning, Gardening, Handyman, Painting, Plumbing, Electrical, Moving, Laundry) on the home screen. Tap one to filter the live job feed. Every job card, job detail and the post-a-job dropdown uses the same catalogue. |
| 🌟 **Offline mode with sync - now for everything** | In v2 only "accept job" worked offline. Now accepting, completing, rating **and posting a job (with its photo)** work with no signal. Actions wait in a local Room outbox, show a *Waiting to sync* tag and an offline banner, and sync **the moment the connection returns**. Replays are safe: the API ignores duplicates (`clientRef`). |
| 🌟 **Real-time push notifications** | Firebase Cloud Messaging delivers targeted job alerts instantly (even with the app closed). A built-in fallback polls the API (every 20 s while open, every 15 min in the background) so alerts work even before Firebase is configured. Android 13+ permission handled; unread-count badge on the Alerts tab. |
| 🌟 **Google single sign-on (SSO)** | "Continue with Google" through Android Credential Manager. The API verifies the Google ID token (signature, audience, issuer, expiry, verified email) and issues its own JWT. New Google users choose Household or Worker. |
| 🌟 **Photos stored as BLOBs** | Profile photos and job photos are compressed on the phone, uploaded to the API and stored as binary objects in the database (`media` table). Images are only served to signed-in users. |
| 🌟 **Truly multi-language** | English, Afrikaans and isiXhosa now switch **instantly and persistently** (v2 saved the choice but never applied it). Registered with Android's per-app-language support. |
| **Account deletion** | *Settings → Delete account* erases personal data (required by Google Play); public privacy-policy and deletion pages are served by the API. |
| **Skill chips** | Workers pick their services from chips instead of typing free text, so targeted alerts can't be broken by typos. |
| **Search** | Search jobs by title, place, description or service. |
| **Brand new look** | Green/gold palette, gradient headers, rounded cards, service pictures, status chips, app icon (adaptive + themed), splash screen. |

### Improved
- Targets **Android 16 (API 36)**, as Google Play now requires; edge-to-edge layouts with correct system-bar and keyboard insets.
- Role rules enforced on the server: only households post jobs, only workers accept (and never their own job).
- Logins last 30 days; an expired session returns the user to the login screen with a clear message instead of failing silently.
- Registration returns a token, so signing up logs you straight in.
- Notification tap opens the matching job.
- Release builds can be signed from `local.properties` / GitHub secrets; CI also builds the signed release AAB on a tag.
- Cleartext (http) traffic disabled; encrypted session excluded from backups.

### Fixed
- Language setting saved but did not change the UI.
- After opening Alerts/Profile/Settings, the bottom-bar tab stayed highlighted and could not be tapped again.
- Offline "accept" gave no visual feedback; the job now shows as accepted immediately and *Waiting to sync*.
- Category typos ("Gardening " vs "gardening") could silently stop workers receiving alerts - categories are now normalised.
- Database columns added in Part 3 are created automatically on the existing hosted database (no manual SQL).

### Tests
Backend: 54 automated tests. Android: unit tests for the repository (cache fallback, queueing, replay, conflicts), validators and models. All run on GitHub Actions.

---

## Version 2.0.0 - Part 2 prototype
Register/login with bcrypt + JWT, job feed with Room cache, post/accept jobs, English/Afrikaans/isiXhosa strings, ratings and reviews, worker profile and verification, targeted in-app alerts, hosted REST API on Render, GitHub Actions build.
