# Demo video script (target 6-8 minutes) - record on your PHONE with voice-over

The brief requires: voice-over, professional, every feature, **and the data stored in the hosted authentication service, API and database**.
Prepare **a laptop beside you** showing: (a) the Render dashboard / `https://msebenzi-api.onrender.com/api/jobs` (b) the database table view
(Render → PostgreSQL → *Connect* → a client such as pgAdmin/TablePlus/DBeaver) (c) the GitHub repo + Actions tab.
Wake the API first (open its URL a minute earlier).

| Time | Show on phone | Say (example) |
|---|---|---|
| 0:00 | Launch splash → login screen | "Hi, I'm ___. This is Msebenzi, an app that connects households with trusted local workers in South Africa. This is the final PoE for Open Source Coding." |
| 0:30 | **Register** a household (show a validation error first) | "Registration validates input. The password is sent over HTTPS and stored only as a bcrypt hash." |
| 1:15 | Laptop: **users table** → show `passwordHash` | "Here is the hosted PostgreSQL database - the password is hashed, never plain text." |
| 1:45 | Log out → **Continue with Google** (SSO) → choose role | "Single sign-on with Google: the app gets a Google ID token, my API verifies it with Google and issues its own JWT." |
| 2:30 | **Home screen**, scroll the **services gallery**, tap Painting | "These are the services we offer, each with its own picture. Tapping one filters the live job feed." |
| 3:00 | **Post a job** with a photo | "Households post jobs with a service, price and a photo. The photo is uploaded as a **BLOB** to the API." |
| 3:30 | Laptop: **media table** + `/api/jobs` JSON | "The image is stored as a binary object in the media table; the job row only keeps its id." |
| 4:00 | Second phone/login as worker: **alert arrives** (push), tap it, **accept** job | "The worker's skills matched, so a real-time push notification arrived - targeted, not broadcast." |
| 4:45 | **Offline mode**: airplane mode → accept/post a job → banner + "Waiting to sync" → airplane off → syncs | "Offline mode with sync: actions are queued in a local Room database and replayed automatically when the connection returns." |
| 5:45 | **Complete + rate**; open **Profile** (photo, rating, verified badge, skills) | "Trusted ratings, worker profiles and verification are my three user-defined features from Part 1." |
| 6:30 | **Settings**: switch to isiXhosa then Afrikaans; notifications switch; Sync now | "Multi-language support: English, Afrikaans and isiXhosa switch instantly." |
| 7:00 | Laptop: **GitHub** repo, README, release notes, commits, **Actions** green run, `final-poe` tag | "Version control, automated tests and builds run on GitHub Actions on every push." |
| 7:30 | Show the signed APK + Play Console screenshot | "The app targets Android 16, is signed, and is uploaded to the Play Console for publication." |
