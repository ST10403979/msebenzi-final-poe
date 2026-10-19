// utils/notify.js
// Small helper used by routes/jobs.js and routes/ratings.js to create
// targeted in-app alerts without duplicating the same Notification.create
// call in five different places.
const Notification = require("../models/Notification");
const User = require("../models/User");
const push = require("./push");

// Deleted accounts are anonymised to an address ending in this (see DELETE /api/users/me).
const DELETED_EMAIL_SUFFIX = "@msebenzi.invalid";

/**
 * Creates the in-app alert (shown on the Alerts tab) and, if the user has a registered
 * device and push is configured, also sends it as a real-time push notification.
 */
async function createAlert({ userId, type, jobId = null, message }) {
  const notification = await Notification.create({ userId, type, jobId, message });
  // Fire-and-forget: a push failure must never break the API request.
  push
    .sendToUser(userId, {
      title: "Msebenzi",
      body: message,
      data: { notificationId: notification.id, type, jobId: jobId || "" },
    })
    .catch(() => {});
  return notification;
}

/**
 * Notifies every worker whose comma-separated `skills` field contains the
 * job's category. This is the "targeted" part of "targeted job alerts" —
 * it does not notify every worker, only ones with a matching skill.
 * A worker with no skills set is treated as open to every category
 * (useful for brand-new accounts that haven't filled in a profile yet).
 */
async function notifyMatchingWorkers(job) {
  const workers = await User.findAll({ where: { role: "worker" } });

  const matches = workers.filter((w) => {
    if (w.email.endsWith(DELETED_EMAIL_SUFFIX)) return false; // deleted accounts
    if (!w.skills) return true; // no skills set yet -> see everything
    const skillList = w.skills.split(",").map((s) => s.trim().toLowerCase());
    return skillList.includes(job.category.toLowerCase());
  });

  await Promise.all(
    matches.map((worker) =>
      createAlert({
        userId: worker.id,
        type: "JOB_MATCH",
        jobId: job.id,
        message: `New ${job.category} job near ${job.location}: "${job.title}" (R${job.budget})`,
      })
    )
  );

  console.log(`[notify] Job ${job.id} matched to ${matches.length} worker(s)`);
}

async function notifyJobAccepted(job, workerName) {
  await createAlert({
    userId: job.postedById,
    type: "JOB_ACCEPTED",
    jobId: job.id,
    message: `${workerName} accepted your job "${job.title}"`,
  });
}

async function notifyJobCompleted(job) {
  // Let both parties know a rating is now possible.
  await createAlert({
    userId: job.postedById,
    type: "JOB_COMPLETED",
    jobId: job.id,
    message: `"${job.title}" is marked complete. You can now rate the worker.`,
  });
  if (job.acceptedById) {
    await createAlert({
      userId: job.acceptedById,
      type: "JOB_COMPLETED",
      jobId: job.id,
      message: `"${job.title}" is marked complete. You can now rate the household.`,
    });
  }
}

async function notifyRatingReceived(rateeId, score, jobTitle) {
  await createAlert({
    userId: rateeId,
    type: "RATING_RECEIVED",
    message: `You received a ${score}-star rating for "${jobTitle}"`,
  });
}

module.exports = {
  createAlert,
  notifyMatchingWorkers,
  notifyJobAccepted,
  notifyJobCompleted,
  notifyRatingReceived,
};
