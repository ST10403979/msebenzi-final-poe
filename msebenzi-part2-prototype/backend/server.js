// server.js
// Entry point for the Msebenzi REST API.
require("dotenv").config();
const express = require("express");
const cors = require("cors");

const sequelize = require("./config/db");
const User = require("./models/User");
const Job = require("./models/Job");
const Rating = require("./models/Rating");
const Notification = require("./models/Notification");
const Media = require("./models/Media");
const { addMissingColumns } = require("./utils/migrate");

const authRoutes = require("./routes/auth");
const userRoutes = require("./routes/users");
const jobRoutes = require("./routes/jobs");
const ratingRoutes = require("./routes/ratings");
const notificationRoutes = require("./routes/notifications");
const adminRoutes = require("./routes/admin");
const mediaRoutes = require("./routes/media");
const path = require("path");

const app = express();
app.disable("x-powered-by");
app.use(cors());
app.use(express.json());

// Simple request logger — satisfies the "use logging to show your
// understanding of your code" submission requirement.
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.originalUrl}`);
  next();
});

app.get("/", (req, res) => {
  res.json({ status: "ok", message: "Msebenzi API is running" });
});

app.use("/api/auth", authRoutes);
app.use("/api/users", userRoutes);
app.use("/api/jobs", jobRoutes);
app.use("/api", ratingRoutes); // exposes /api/jobs/:id/rate and /api/users/:id/ratings
app.use("/api/notifications", notificationRoutes);
app.use("/api/admin", adminRoutes);
app.use("/api/media", mediaRoutes); // BLOB storage for profile + job photos

// Public pages Google Play asks for: a privacy policy and an account-deletion page.
app.get("/privacy", (req, res) => res.sendFile(path.join(__dirname, "public", "privacy.html")));
app.get("/delete-account", (req, res) => res.sendFile(path.join(__dirname, "public", "delete-account.html")));

// Generic error handler — makes sure the app never crashes the process
// on an unexpected error, and never leaks a stack trace to the client.
app.use((err, req, res, next) => {
  console.error("[server] Unhandled error:", err);
  res.status(500).json({ error: "Unexpected server error" });
});

const PORT = process.env.PORT || 3000;

async function start() {
  try {
    await sequelize.authenticate();
    console.log("[db] Connection established successfully.");

    // sync() creates tables if they don't exist yet (e.g. the new `media` table);
    // addMissingColumns() then adds the Part 3 columns to tables created in Part 2.
    // For a class project this is fine; a production app would use real migrations.
    await sequelize.sync();
    await addMissingColumns(sequelize);
    console.log("[db] Models synced.");

    app.listen(PORT, () => {
      console.log(`[server] Msebenzi API listening on port ${PORT}`);
    });
  } catch (err) {
    console.error("[server] Failed to start:", err.message);
    process.exit(1);
  }
}

// Only actually connect + listen when this file is run directly
// (`node server.js` / `npm start` / `npm run dev`). When a test file does
// `require("../server")`, we just want the configured Express app object,
// not a live listener — otherwise every test file that imports this
// module would try to bind the same port and crash with EADDRINUSE.
if (require.main === module) {
  start();
}

module.exports = app; // exported for automated tests (see tests/)
