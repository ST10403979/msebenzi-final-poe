// routes/auth.js
// Registration and login. Passwords are hashed with bcrypt (never stored
// or logged in plain text) and a JWT is issued on successful login.
const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");
const User = require("../models/User");
const publicUser = require("../utils/publicUser");
const googleAuth = require("../utils/googleAuth");

const router = express.Router();

const SALT_ROUNDS = 10;
const LANGUAGES = ["en", "af", "xh"];

// Emails are compared in lower case so "Thabo@Mail.com" and "thabo@mail.com" are one account.
function normaliseEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

// How long a login stays valid. 30 days so people aren't logged out mid-week.
const TOKEN_LIFETIME = process.env.JWT_EXPIRES_IN || "30d";

function signToken(user) {
  return jwt.sign(
    { id: user.id, email: user.email, role: user.role },
    process.env.JWT_SECRET || "dev_secret_change_me",
    { expiresIn: TOKEN_LIFETIME }
  );
}

// POST /api/auth/register
router.post("/register", async (req, res) => {
  try {
    const { name, password, role, language } = req.body;
    const email = normaliseEmail(req.body.email);

    if (!name || !email || !password) {
      return res.status(400).json({ error: "name, email and password are required" });
    }
    if (password.length < 6) {
      return res.status(400).json({ error: "Password must be at least 6 characters" });
    }

    const existing = await User.findOne({ where: { email } });
    if (existing) {
      return res.status(409).json({ error: "An account with that email already exists" });
    }

    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);

    const user = await User.create({
      name,
      email,
      passwordHash,
      role: role === "household" ? "household" : "worker",
      language: LANGUAGES.includes(language) ? language : "en",
    });

    console.log(`[auth] New user registered: ${user.email} (id=${user.id})`);

    // Return a token straight away so the app can log the new user in without a second call.
    return res.status(201).json({
      message: "Registration successful",
      token: signToken(user),
      user: publicUser(user),
    });
  } catch (err) {
    console.error("[auth] Register error:", err.message);
    return res.status(500).json({ error: "Server error during registration" });
  }
});

// POST /api/auth/login
router.post("/login", async (req, res) => {
  try {
    const { password } = req.body;
    const email = normaliseEmail(req.body.email);

    if (!email || !password) {
      return res.status(400).json({ error: "email and password are required" });
    }

    const user = await User.findOne({ where: { email } });
    if (!user) {
      return res.status(401).json({ error: "Invalid email or password" });
    }

    const match = await bcrypt.compare(password, user.passwordHash);
    if (!match) {
      return res.status(401).json({ error: "Invalid email or password" });
    }

    const token = signToken(user);

    console.log(`[auth] User logged in: ${user.email}`);

    return res.json({
      message: "Login successful",
      token,
      user: publicUser(user),
    });
  } catch (err) {
    console.error("[auth] Login error:", err.message);
    return res.status(500).json({ error: "Server error during login" });
  }
});

// POST /api/auth/google  - SINGLE SIGN-ON with a Google ID token.
// Existing accounts (matched by Google id, or by the same verified email) are logged in.
// A brand-new Google user must say whether they are a worker or a household: if `role`
// is missing we answer { needsRole: true } and the app asks, then calls this again.
router.post("/google", async (req, res) => {
  try {
    const { idToken, role, language } = req.body;
    if (!idToken) return res.status(400).json({ error: "idToken is required" });

    let profile;
    try {
      profile = await googleAuth.verifyGoogleIdToken(idToken);
    } catch (err) {
      console.error("[auth] Google token rejected:", err.message);
      return res.status(401).json({ error: "Invalid Google sign-in" });
    }

    let user = await User.findOne({ where: { googleId: profile.googleId } });
    if (!user) {
      user = await User.findOne({ where: { email: profile.email } });
      if (user) {
        // Same verified email as an existing password account: link the two.
        user.googleId = profile.googleId;
        await user.save();
      }
    }

    if (!user) {
      if (role !== "worker" && role !== "household") {
        return res.json({ needsRole: true, message: "Choose a role to finish creating your account" });
      }
      // Google users never log in with a password, so store a random unusable hash.
      const passwordHash = await bcrypt.hash(crypto.randomBytes(32).toString("hex"), SALT_ROUNDS);
      user = await User.create({
        name: profile.name,
        email: profile.email,
        passwordHash,
        role,
        language: LANGUAGES.includes(language) ? language : "en",
        googleId: profile.googleId,
      });
      console.log(`[auth] New Google user registered: ${user.email} (id=${user.id})`);
    } else {
      console.log(`[auth] Google user logged in: ${user.email}`);
    }

    return res.json({ message: "Login successful", token: signToken(user), user: publicUser(user) });
  } catch (err) {
    console.error("[auth] Google login error:", err.message);
    return res.status(500).json({ error: "Server error during Google sign-in" });
  }
});

module.exports = router;
