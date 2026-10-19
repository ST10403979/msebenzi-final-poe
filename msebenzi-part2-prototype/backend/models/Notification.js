// models/Notification.js
// Feature: Targeted job alerts (in-app version — no external push service).
// Rows here back the app's "Alerts" tab. They are created server-side at
// key events (see routes/jobs.js and routes/ratings.js) rather than
// broadcast to every user, which is what makes the alerts "targeted"
// rather than a generic announcement feed.
const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const Notification = sequelize.define(
  "Notification",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    userId: {
      // who the alert is for
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    type: {
      type: DataTypes.ENUM(
        "JOB_MATCH",
        "JOB_ACCEPTED",
        "JOB_COMPLETED",
        "RATING_RECEIVED"
      ),
      allowNull: false,
    },
    jobId: {
      type: DataTypes.INTEGER,
      allowNull: true,
    },
    message: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    isRead: {
      type: DataTypes.BOOLEAN,
      allowNull: false,
      defaultValue: false,
    },
  },
  {
    tableName: "notifications",
    timestamps: true,
  }
);

module.exports = Notification;
