// routes/admin.js
// A deliberately minimal "admin" surface for the prototype: approving a
// worker's verification request. There's no admin UI in scope for this
// assignment, so this is called directly (e.g. via curl or Postman) using
// a shared secret rather than a full admin role/login system.
//
// For your demo video: show a `curl` call (or a Postman request) hitting
// this endpoint with your ADMIN_TOKEN, then reload the worker's profile
// in the app to show the "Verified" badge appear.
const express = require("express");
const User = require("../models/User");

const router = express.Router();

function requireAdminToken(req, res, next) {
  const token = req.headers["x-admin-token"];
  const expected = process.env.ADMIN_TOKEN || "dev_admin_token_change_me";
  if (!token || token !== expected) {
    return res.status(401).json({ error: "Invalid or missing admin token" });
  }
  next();
}

// PUT /api/admin/users/:id/verify
router.put("/users/:id/verify", requireAdminToken, async (req, res) => {
  try {
    const user = await User.findByPk(req.params.id);
    if (!user) return res.status(404).json({ error: "User not found" });

    user.verificationStatus = "verified";
    await user.save();

    console.log(`[admin] User ${user.id} (${user.email}) marked verified`);
    return res.json({ message: "User verified", user: { id: user.id, verificationStatus: user.verificationStatus } });
  } catch (err) {
    console.error("[admin] Verify error:", err.message);
    return res.status(500).json({ error: "Server error" });
  }
});

module.exports = router;
