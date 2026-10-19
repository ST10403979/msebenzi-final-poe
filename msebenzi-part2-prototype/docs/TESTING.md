# Testing evidence

## 1. Automated tests (run on every push by GitHub Actions)

| Suite | Command | What it covers |
|---|---|---|
| Backend API (Jest + Supertest) - **54 tests** | `cd backend && npm test` | register/login, hashed passwords, JWT, jobs, roles, ratings, verification, alerts, **media BLOB upload/download**, **Google SSO**, **push-token registration**, **offline-sync idempotency**, **account deletion**, privacy page |
| Android unit tests (JUnit + MockK) | `cd android && ./gradlew testDebugUnitTest` | offline-first repository (cache fallback, queueing, replay, conflict handling), input validators, models, service catalogue |
| Static project checks | `python3 tools/check_android_resources.py` | every `R.x.y` / `@x/y` resolves, every view-binding id exists, English/Afrikaans/isiXhosa string files are in sync |

GitHub Actions (`.github/workflows/build.yml`) runs all three and builds the debug APK. Screenshot the green ✅ run for your PoE.

## 2. Manual test checklist (on a real phone)  - tick as you test

**Accounts**
- [ ] Register as Household with an invalid email → friendly error, no crash
- [ ] Register with a 3-character password → error
- [ ] Register a Worker, log out, log in again
- [ ] Continue with Google (new user → asked Household/Worker; returning user → straight in)
- [ ] Wrong password → "Incorrect email or password"

**Jobs**
- [ ] Household: post a job with a photo → appears in the feed with the photo
- [ ] Post with empty fields / budget "abc" → friendly errors
- [ ] Worker: filter by tapping a service card; search by text
- [ ] Worker: accept job → household gets an alert; try accepting again as another worker → "no longer available"
- [ ] Mark complete → both can rate; rating shows on profile

**Offline + sync**
- [ ] Open the feed once online, switch on airplane mode → feed still shows, orange "You're offline" banner
- [ ] Offline: accept a job → "Saved offline", card shows *Waiting to sync*
- [ ] Offline: post a job → saved locally with *Waiting to sync*
- [ ] Switch airplane mode off → within seconds the banner clears and the job exists on the server (check the database in the video)

**Notifications**
- [ ] Android 13+: permission prompt appears once
- [ ] Worker with matching skill receives a notification when a household posts a job (app open, and app closed)
- [ ] Tapping the notification opens that job

**Settings / profile**
- [ ] Switch language to Afrikaans, then isiXhosa → every screen changes immediately; survives restarting the app
- [ ] Turn notifications off → no more system notifications
- [ ] Change profile photo → shows on profile
- [ ] Pick skills + bio → saved; Request verification → "Pending" (approve with the admin `curl` call)
- [ ] Delete account → returned to login; old password no longer works

**Robustness**
- [ ] Rotate the phone on every screen - no crash
- [ ] Kill the app and reopen - still logged in
- [ ] Phone in dark mode - app stays readable (light theme enforced)
