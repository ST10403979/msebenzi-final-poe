// routes/media.js
// BLOB STORAGE endpoints. The Android app uploads a (compressed) photo as raw bytes;
// we validate it really is a JPEG/PNG, store it as a BLOB in the `media` table and hand
// back its id. GET returns the bytes with the right Content-Type.
//
// Images are only served to logged-in users, so profile and job photos are not public.
const express = require("express");
const Media = require("../models/Media");
const { requireAuth } = require("../middleware/auth");

const router = express.Router();

const MAX_BYTES = 3 * 1024 * 1024; // 3 MB - the app shrinks photos to well under this
const MAX_IMAGES_PER_USER = 200;

/** Check the file's magic bytes so a renamed .exe can't be stored as an "image". */
function detectMimeType(buffer) {
  if (buffer.length > 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return "image/jpeg";
  }
  if (
    buffer.length > 8 &&
    buffer.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  ) {
    return "image/png";
  }
  return null;
}

// POST /api/media   (body = raw image bytes, Content-Type image/jpeg or image/png)
router.post(
  "/",
  requireAuth,
  express.raw({ type: ["image/jpeg", "image/png"], limit: MAX_BYTES }),
  async (req, res) => {
    try {
      if (!Buffer.isBuffer(req.body) || req.body.length === 0) {
        return res.status(415).json({ error: "Send the image as raw image/jpeg or image/png data" });
      }
      const mimeType = detectMimeType(req.body);
      if (!mimeType) {
        return res.status(415).json({ error: "File is not a valid JPEG or PNG image" });
      }

      const count = await Media.count({ where: { ownerId: req.user.id } });
      if (count >= MAX_IMAGES_PER_USER) {
        return res.status(429).json({ error: "Image upload limit reached" });
      }

      const media = await Media.create({
        ownerId: req.user.id,
        mimeType,
        size: req.body.length,
        data: req.body,
      });

      console.log(`[media] Stored ${mimeType} (${media.size} bytes) as media ${media.id} for user ${req.user.id}`);
      return res.status(201).json({ id: media.id, url: `/api/media/${media.id}` });
    } catch (err) {
      console.error("[media] Upload error:", err.message);
      return res.status(500).json({ error: "Server error storing image" });
    }
  }
);

// GET /api/media/:id
router.get("/:id", requireAuth, async (req, res) => {
  try {
    const media = await Media.findByPk(req.params.id);
    if (!media) return res.status(404).json({ error: "Image not found" });

    res.set("Content-Type", media.mimeType);
    res.set("Content-Length", String(media.size));
    // Ids are never reused, so an image never changes - let the phone cache it.
    res.set("Cache-Control", "private, max-age=86400, immutable");
    return res.send(Buffer.from(media.data));
  } catch (err) {
    console.error("[media] Fetch error:", err.message);
    return res.status(500).json({ error: "Server error fetching image" });
  }
});

module.exports = router;
