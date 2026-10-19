# Msebenzi - Final PoE setup guide

Everything in the code is finished. These are the **account-level steps only you can do** (they need
*your* Google, Firebase, Render and Play accounts). Follow them in order - roughly 1-2 hours in total,
except step 6 (Google Play), which has a mandatory waiting period.

> ✅ = required for the PoE brief   ⭐ = optional but strongly recommended

---

## 0. Before you start - tidy the repository (5 min) ✅

The brief says **no zip files** and wants a clean repo. In the project root run:

```bash
git rm -r --cached .idea            # IDE files should not be committed
git rm --cached local.properties    # contains your PC's SDK path
# also delete the stray nested "msebenzi/" Android Studio project and "new.sh" from the repo folder
git add -A
git commit -m "Part 3: final PoE - clean-up, upgrade to API 36, new features"
```

Copy the contents of this folder over your repository (keep your `.git` folder), then commit.

---

## 1. Deploy the updated API to Render ✅ (10 min)

The API changed (media/BLOB storage, Google SSO, push, account deletion), so redeploy it:

1. `git push` - Render redeploys automatically if auto-deploy is on (otherwise **Manual Deploy → Deploy latest commit**).
2. On first start the API **adds the new database columns itself** (`utils/migrate.js`) - no manual SQL.
3. In Render → your web service → **Environment**, make sure these exist:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL`, `JWT_SECRET`, `ADMIN_TOKEN` | already set from Part 2 |
   | `GOOGLE_WEB_CLIENT_ID` | from step 2 (Google sign-in) |
   | `FIREBASE_SERVICE_ACCOUNT_JSON` | from step 3 (push) - optional |

4. Open `https://msebenzi-api.onrender.com/` → you should see `{"status":"ok", ...}`.
   Open `https://msebenzi-api.onrender.com/privacy` → your privacy policy page.
5. **Edit `backend/public/privacy.html` and `delete-account.html`** and replace `REPLACE_WITH_YOUR_EMAIL` with a real address.
6. ⭐ Fill the demo data (so the feed isn't empty in your video). From the `backend` folder:
   ```bash
   # Windows PowerShell:  $env:DATABASE_URL="<External Database URL from Render>"; node scripts/seed.js
   DATABASE_URL="<External Database URL from Render>" npm run seed
   ```
   Demo logins (password `Demo@1234`): `thandi@demo.msebenzi` (household), `sipho@demo.msebenzi`, `lerato@demo.msebenzi` (workers).

> The free Render instance sleeps. Open the `/` URL a minute before you start recording.

---

## 2. Google single sign-on (SSO) ✅ (20 min)

The brief marks SSO as **PoE only**, so do this one.

1. Go to <https://console.cloud.google.com> → create a project **Msebenzi**.
2. **APIs & Services → OAuth consent screen**: *External*, app name **Msebenzi**, add your email, save. Add yourself (and your lecturer, if they will test) under **Test users**.
3. **Credentials → Create credentials → OAuth client ID** - create **two** clients:
   - **Web application** (name: *Msebenzi web*). Copy its **Client ID** - this is `GOOGLE_WEB_CLIENT_ID`.
   - **Android** (name: *Msebenzi Android*): package name `com.msebenzi.app` and the **SHA-1** of your signing key.
     Get your debug SHA-1 with `cd android && ./gradlew signingReport` (Windows: `gradlew.bat signingReport`).
     Create one Android client per key: **debug**, your **upload key** (step 5) and later Play's **app-signing key**
     (Play Console → App integrity).
4. Put the *Web* client ID in **two** places:
   - `android/local.properties`: `google.web.client.id=1234-abc.apps.googleusercontent.com`
   - Render environment variable `GOOGLE_WEB_CLIENT_ID` (same value).
5. Rebuild the app → **Continue with Google** now works. A new Google user is asked "Household or Worker?".

Without this step the button shows *"Google sign-in is not set up in this build"* (the app never crashes).

---

## 3. Push notifications with Firebase ✅ (15 min)

The app **always** delivers alerts (while open every ~20 s, in the background every ~15 min). Firebase makes them **instant**.

1. <https://console.firebase.google.com> → **Add project** (you can reuse the Google Cloud project from step 2).
2. **Add app → Android**: package name `com.msebenzi.app` → download **`google-services.json`** → put it in **`android/app/`**.
   (The Gradle build only applies the Firebase plugin when this file exists, so nothing breaks without it.)
3. **Project settings → Service accounts → Generate new private key** → open the downloaded JSON, copy **everything**, and paste it
   into Render as the environment variable `FIREBASE_SERVICE_ACCOUNT_JSON`. **Never commit that file** (`.gitignore` blocks it).
4. Test: log in as a worker on your phone (skills include *Plumbing*), then post a plumbing job as a household on another device
   or via the API → the phone buzzes within seconds, even with the app closed.

---

## 4. Run on a real phone ✅ (10 min)

The brief says the video must be recorded on **a phone, not an emulator**.

1. Phone → **Settings → About phone** → tap **Build number** 7× → **Developer options → USB debugging** on.
2. Connect by USB, accept the prompt, pick your phone in Android Studio and press **Run ▶**.
3. If Gradle asks to install *Android SDK Platform 36*, accept - it is required by the Play Store.
4. Record the video with the phone's built-in screen recorder (voice-over: see `docs/VIDEO_SCRIPT.md`).

---

## 5. Create the signed release (APK + AAB) ✅ (15 min)

Google Play needs an **.aab** bundle; your lecturer wants a **signed APK**. Build both.

1. Create your upload keystore **once** and **back it up** (lose it = you cannot update the app):
   ```bash
   keytool -genkeypair -v -keystore msebenzi-upload.jks -alias msebenzi -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add to `android/local.properties` (this file is git-ignored):
   ```properties
   msebenzi.keystore.path=C\:\\Users\\you\\keys\\msebenzi-upload.jks
   msebenzi.keystore.password=YOUR_STORE_PASSWORD
   msebenzi.key.alias=msebenzi
   msebenzi.key.password=YOUR_KEY_PASSWORD
   ```
3. Build:
   ```bash
   cd android
   ./gradlew assembleRelease bundleRelease
   ```
   - Signed APK → `app/build/outputs/apk/release/app-release.apk`
   - Play bundle → `app/build/outputs/bundle/release/app-release.aab`
4. **Or let GitHub build it**: add the repository secrets `MSEBENZI_KEYSTORE_BASE64` (`base64 -w0 msebenzi-upload.jks`),
   `MSEBENZI_KEYSTORE_PASSWORD`, `MSEBENZI_KEY_ALIAS`, `MSEBENZI_KEY_PASSWORD` (+ optional `GOOGLE_WEB_CLIENT_ID`,
   `GOOGLE_SERVICES_JSON`), then push the tag in step 7. Download the artifacts from the Actions run.

---

## 6. Google Play Console ✅ (start TODAY - there is a waiting period)

> ⚠️ **Timeline reality check.** A *personal* Play developer account created after 13 Nov 2023 must run a **closed test with at
> least 12 opted-in testers for 14 continuous days** before it can even apply for production (budget about **3 weeks** in total).
> Organisation accounts are exempt. The brief only asks for *"evidence of preparation for publication"* and a screenshot of the app
> **uploaded to the Play Console** - so start the closed test now and screenshot it; being live in the store is not required to earn the marks.

1. <https://play.google.com/console> → create a developer account (one-off US$25 fee, ID verification).
2. **Create app** → name *Msebenzi*, default language English (South Africa), App, Free.
3. **Main store listing** - use the files in `store-assets/` and the text in `docs/PLAY_STORE_LISTING.md`:
   icon `play-store-icon-512.png`, feature graphic `feature-graphic-1024x500.png`, and **at least 2 phone screenshots taken on your phone**.
4. **App content** (all required):
   - Privacy policy URL: `https://msebenzi-api.onrender.com/privacy`
   - **Data safety**: use the answers in `docs/PLAY_STORE_LISTING.md`
   - **Account deletion**: in-app (*Settings → Delete account*) and web page `https://msebenzi-api.onrender.com/delete-account`
   - Ads: **No**. Target audience: **18+**. Content rating questionnaire (user-generated content: yes).
   - If asked for a **test login** for reviewers, give `thandi@demo.msebenzi` / `Demo@1234` (run the seed in step 1).
5. **Testing → Closed testing → Create track** → upload `app-release.aab` → add **12+ tester Gmail addresses** (classmates are perfect) →
   send them the opt-in link. They must stay opted in for 14 days.
6. Screenshot the Console showing the uploaded release - that's your PoE evidence.
7. After 14 days the **Apply for production** button appears.

The app already meets Play's technical rules: **target API 36**, signed bundle, runtime notification permission,
in-app account deletion, privacy policy, no cleartext traffic, no ads.

---

## 7. Final GitHub steps ✅ (5 min)

```bash
git add -A
git commit -m "Final PoE: README, release notes, store assets"
git push origin main
git tag -a final-poe -m "Final PoE submission"
git push origin final-poe          # tags the final commit AND triggers the signed release build
```

Then in the README replace `[ADD YOUR VIDEO LINK HERE]`, add your phone screenshots under `docs/images/`, and tick every box in
`docs/TESTING.md` (that's your evidence of testing). The brief also says the repo must contain **no zip files**.
