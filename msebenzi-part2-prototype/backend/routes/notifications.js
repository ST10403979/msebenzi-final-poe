// routes/notifications.js
// Backs the app's "Alerts" tab. Notifications are created server-side by
// utils/notify.js at key events (job posted, accepted, completed, rated) —
// this route only ever reads/updates them.
const express = require("express");
const Notification = require("../models/Notification");
const { requireAuth } = require("../middleware/auth");

const router = express.Router();

// GET /api/notifications — newest first
router.get("/", requireAuth, async (req, res) => {
  try {
    const notifications = await Notification.findAll({
      where: { userId: req.user.id },
      order: [["createdAt", "DESC"]],
      limit: 100,
    });
    return res.json(notifications);
  } catch (err) {
    console.error("[notifications] List error:", err.message);
    return res.status(500).json({ error: "Server error fetching notifications" });
  }
});

// GET /api/notifications/unread-count — for a badge/counter in the UI
router.get("/unread-count", requireAuth, async (req, res) => {
  try {
    const count = await Notification.count({
      where: { userId: req.user.id, isRead: false },
    });
    return res.json({ count });
  } catch (err) {
    console.error("[notifications] Count error:", err.message);
    return res.status(500).json({ error: "Server error counting notifications" });
  }
});

// PUT /api/notifications/:id/read
router.put("/:id/read", requireAuth, async (req, res) => {
  try {
    const notification = await Notification.findByPk(req.params.id);
    if (!notification || notification.userId !== req.user.id) {
      return res.status(404).json({ error: "Notification not found" });
    }
    notification.isRead = true;
    await notification.save();
    return res.json(notification);
  } catch (err) {
    console.error("[notifications] Mark-read error:", err.message);
    return res.status(500).json({ error: "Server error updating notification" });
  }
});

module.exports = router;
