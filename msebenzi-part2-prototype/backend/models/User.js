// models/User.js
// Represents a Msebenzi user. Passwords are NEVER stored in plain text —
// see routes/auth.js, which hashes them with bcrypt before saving.
const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const User = sequelize.define(
  "User",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    name: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    email: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true,
      validate: { isEmail: true },
    },
    passwordHash: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    role: {
      // "worker" or "household" — matches the two roles from the Part 1 design
      type: DataTypes.ENUM("worker", "household"),
      allowNull: false,
      defaultValue: "worker",
    },
    language: {
      // "en", "af" or "xh" — used by the app's Settings screen
      type: DataTypes.STRING,
      allowNull: false,
      defaultValue: "en",
    },
    skills: {
      // Comma-separated skills for workers, e.g. "gardening,cleaning"
      type: DataTypes.STRING,
      allowNull: true,
    },
    bio: {
      // Short worker profile description, shown on the Worker Profile screen
      type: DataTypes.TEXT,
      allowNull: true,
    },
    verificationStatus: {
      // "unverified" -> "pending" (worker requested it) -> "verified" (approved)
      type: DataTypes.ENUM("unverified", "pending", "verified"),
      allowNull: false,
      defaultValue: "unverified",
    },
    ratingAverage: {
      type: DataTypes.FLOAT,
      allowNull: false,
      defaultValue: 0,
    },
    ratingCount: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: 0,
    },
    avatarMediaId: {
      // profile photo - id of a row in the `media` table (BLOB storage)
      type: DataTypes.INTEGER,
      allowNull: true,
    },
    fcmToken: {
      // Firebase Cloud Messaging device token for real-time push notifications
      type: DataTypes.STRING(512),
      allowNull: true,
    },
    googleId: {
      // Google account id ("sub") for single sign-on users
      type: DataTypes.STRING,
      allowNull: true,
    },
  },
  {
    tableName: "users",
    timestamps: true,
  }
);

module.exports = User;
