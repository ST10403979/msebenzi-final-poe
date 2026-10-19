# AI usage disclosure

> ⚠️ **Student: read this and edit it so it is 100% true for you.** IIE requires an honest declaration of AI use; this file is a
> starting point written to match how the project was actually built. Submit it as-is only if it is accurate.

**Tool used:** Claude (Anthropic), via claude.ai chat.

**What it was used for**
- Part 3 code: Kotlin screens, offline-sync queue, notification handling, Google sign-in flow, media upload, language switching, API
  changes (media/BLOB storage, Google token verification, push sending, account deletion) and their automated tests.
- Generating the original vector artwork (service pictures, app icon, Play Store graphics) with a Python script (`tools/gen_assets.py`).
- Drafting documentation (README, release notes, setup guide, video script, privacy policy).

**What I did myself**
- The app idea, Part 1 design and the three user-defined features.
- Creating the Google Cloud / Firebase / Render / Play Console accounts and configuration.
- Building, running and testing the app on a physical phone, recording the video, and committing to GitHub.
- Reviewing the generated code; I can explain how each part works.

**How AI output was checked:** unit tests (backend + Android), the static resource checker, GitHub Actions builds and manual testing on a phone.
