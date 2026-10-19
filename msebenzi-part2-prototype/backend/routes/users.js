// routes/users.js
// Profile and settings endpoints — backs the app's Settings screen
// (change language, skills) and the Worker Profile & Verification feature.
const express = require("express");
const bcrypt = require("bcryptjs");
const crypto = require("crypto");
const User = require("../models/User");
const Job = require("../models/Job");
const Media = require("../models/Media");
const Notification = require("../models/Notification");
const publicUser = require("../utils/publicUser");
const { requireAuth } = require("../middleware/auth");

const router = express.Router();

// GET /api/users/me
router.get("/me", requireAuth, async (req, res) => {
  try {
    const user = await User.findByPk(req.user.id);
    if (!user) return res.status(404).json({ error: "User not found" });
    return res.json(publicUser(user));
  } catch (err) {
    console.error("[users] Fetch profile error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

// GET /api/users/:id — view another user's public profile (e.g. a
// household viewing a worker's profile before/after a job).
router.get("/:id", requireAuth, async (req, res) => {
  try {
    if (!/^\d+$/.test(req.params.id)) return res.status(400).json({ error: "Invalid user id" });
    const user = await User.findByPk(req.params.id);
    if (!user) return res.status(404).json({ error: "User not found" });
    return res.json(publicUser(user));
  } catch (err) {
    console.error("[users] Fetch public profile error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

// PUT /api/users/me  — used by the Settings and Profile screens
router.put("/me", requireAuth, async (req, res) => {
  try {
    const { name, language, skills, bio, avatarMediaId } = req.body;
    const user = await User.findByPk(req.user.id);
    if (!user) return res.status(404).json({ error: "User not found" });

    if (name) user.name = name;
    if (avatarMediaId != null) {
      // The photo must exist and belong to this user (BLOB storage - see routes/media.js).
      const media = await Media.findByPk(avatarMediaId);
      if (!media || media.ownerId !== user.id) {
        return res.status(400).json({ error: "Unknown image" });
      }
      const previous = user.avatarMediaId;
      user.avatarMediaId = media.id;
      if (previous && previous !== media.id) {
        await Media.destroy({ where: { id: previous, ownerId: user.id } }); // drop the old photo
      }
    }
    if (language && ["en", "af", "xh"].includes(language)) user.language = language;
    if (typeof skills === "string") user.skills = skills;
    if (typeof bio === "string") {
      if (bio.length > 500) {
        return res.status(400).json({ error: "Bio must be 500 characters or fewer" });
      }
      user.bio = bio;
    }

    await user.save();
    console.log(`[users] Profile updated for ${user.email}`);

    return res.json({ message: "Profile updated", user: publicUser(user) });
  } catch (err) {
    console.error("[users] Update profile error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

// PUT /api/users/me/request-verification
// Feature: Worker profile and verification.
// A worker asks to be verified; status moves to "pending" until an
// admin approves it (see PUT /api/admin/users/:id/verify below).
router.put("/me/request-verification", requireAuth, async (req, res) => {
  try {
    const user = await User.findByPk(req.user.id);
    if (!user) return res.status(404).json({ error: "User not found" });
    if (user.role !== "worker") {
      return res.status(400).json({ error: "Only worker accounts can request verification" });
    }
    if (user.verificationStatus === "verified") {
      return res.status(409).json({ error: "This account is already verified" });
    }

    user.verificationStatus = "pending";
    await user.save();
    console.log(`[users] Verification requested by ${user.email}`);

    return res.json({ message: "Verification requested", user: publicUser(user) });
  } catch (err) {
    console.error("[users] Request-verification error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

// PUT /api/users/me/fcm-token
// REAL-TIME PUSH: the app registers its Firebase Cloud Messaging device token here so the
// server can send push notifications to this phone (see utils/push.js).
router.put("/me/fcm-token", requireAuth, async (req, res) => {
  try {
    const { token } = req.body;
    if (typeof token !== "string" || token.length < 10 || token.length > 512) {
      return res.status(400).json({ error: "A valid device token is required" });
    }
    // One phone, one account: remove the token from any other user who used this phone before.
    await User.update({ fcmToken: null }, { where: { fcmToken: token } });
    await User.update({ fcmToken: token }, { where: { id: req.user.id } });
    return res.json({ message: "Device registered for push notifications" });
  } catch (err) {
    console.error("[users] Register FCM token error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

// DELETE /api/users/me/fcm-token - called on log-out so pushes stop going to this phone
router.delete("/me/fcm-token", requireAuth, async (req, res) => {
  try {
    await User.update({ fcmToken: null }, { where: { id: req.user.id } });
    return res.json({ message: "Device unregistered" });
  } catch (err) {
    console.error("[users] Clear FCM token error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

// DELETE /api/users/me - delete the account (required by Google Play).
// Personal data is erased: name, email, bio, skills, photo, device token, alerts and
// uploaded images are removed. The row itself is kept only as an anonymous "Deleted user"
// so that the other person's job history and ratings stay intact.
router.delete("/me", requireAuth, async (req, res) => {
  try {
    const user = await User.findByPk(req.user.id);
    if (!user) return res.status(404).json({ error: "User not found" });

    await Media.destroy({ where: { ownerId: user.id } });
    await Notification.destroy({ where: { userId: user.id } });
    await Job.update({ photoMediaId: null }, { where: { postedById: user.id } });
    await Job.update({ status: "cancelled" }, { where: { postedById: user.id, status: "open" } });

    user.name = "Deleted user";
    user.email = `deleted-${user.id}@msebenzi.invalid`;
    user.passwordHash = await bcrypt.hash(crypto.randomBytes(32).toString("hex"), 10);
    user.bio = null;
    user.skills = null;
    user.avatarMediaId = null;
    user.fcmToken = null;
    user.googleId = null;
    user.verificationStatus = "unverified";
    await user.save();

    console.log(`[users] Account ${user.id} deleted and anonymised`);
    return res.json({ message: "Account deleted" });
  } catch (err) {
    console.error("[users] Delete account error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

module.exports = router;
