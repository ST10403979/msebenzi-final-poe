// routes/ratings.js
// Feature: Trusted ratings and reviews.
// A rating can only be left on a completed job, by one of its two
// participants, about the other participant — this is what makes the
// resulting reputation "trusted" rather than an open free-for-all.
const express = require("express");
const Rating = require("../models/Rating");
const Job = require("../models/Job");
const User = require("../models/User");
const { requireAuth } = require("../middleware/auth");
const { notifyRatingReceived } = require("../utils/notify");

const router = express.Router();

// POST /api/jobs/:id/rate
router.post("/jobs/:id/rate", requireAuth, async (req, res) => {
  try {
    const { score, comment } = req.body;
    const job = await Job.findByPk(req.params.id);

    if (!job) return res.status(404).json({ error: "Job not found" });
    if (job.status !== "completed") {
      return res.status(409).json({ error: "You can only rate a completed job" });
    }

    const raterId = req.user.id;
    let rateeId;
    if (raterId === job.postedById) {
      rateeId = job.acceptedById;
    } else if (raterId === job.acceptedById) {
      rateeId = job.postedById;
    } else {
      return res.status(403).json({ error: "Only the two people on this job can rate each other" });
    }

    if (!rateeId) {
      return res.status(409).json({ error: "This job has no other party to rate yet" });
    }

    const numericScore = Number(score);
    if (!Number.isInteger(numericScore) || numericScore < 1 || numericScore > 5) {
      return res.status(400).json({ error: "Score must be a whole number from 1 to 5" });
    }
    if (comment && comment.length > 300) {
      return res.status(400).json({ error: "Comment must be 300 characters or fewer" });
    }

    const existing = await Rating.findOne({ where: { jobId: job.id, raterId } });
    if (existing) {
      return res.status(409).json({ error: "You have already rated this job" });
    }

    const rating = await Rating.create({
      jobId: job.id,
      raterId,
      rateeId,
      score: numericScore,
      comment: comment || null,
    });

    // Recalculate the ratee's running average — small dataset per user,
    // so a straight recompute is simpler and safer than an incremental
    // running-average formula.
    const ratee = await User.findByPk(rateeId);
    const allRatings = await Rating.findAll({ where: { rateeId } });
    const total = allRatings.reduce((sum, r) => sum + r.score, 0);
    ratee.ratingAverage = Number((total / allRatings.length).toFixed(2));
    ratee.ratingCount = allRatings.length;
    await ratee.save();

    console.log(`[ratings] User ${raterId} rated user ${rateeId} ${numericScore}/5 on job ${job.id}`);

    notifyRatingReceived(rateeId, numericScore, job.title).catch((e) =>
      console.error("[ratings] Failed to send rating notification:", e.message)
    );

    return res.status(201).json({ message: "Rating submitted", rating });
  } catch (err) {
    console.error("[ratings] Create error:", err.message);
    return res.status(500).json({ error: "Server error submitting rating" });
  }
});

// GET /api/users/:id/ratings — ratings a user has received (for their profile)
router.get("/users/:id/ratings", requireAuth, async (req, res) => {
  try {
    const ratings = await Rating.findAll({
      where: { rateeId: req.params.id },
      order: [["createdAt", "DESC"]],
      include: [{ model: User, as: "rater", attributes: ["id", "name"] }],
    });
    return res.json(ratings);
  } catch (err) {
    console.error("[ratings] List error:", err.message);
    return res.status(500).json({ error: "Server error fetching ratings" });
  }
});

module.exports = router;
