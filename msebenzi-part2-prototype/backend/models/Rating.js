// models/Rating.js
// Feature: Trusted ratings and reviews.
// A rating is left on a completed job, by whichever party (household or
// worker) didn't post it about the other party. Ratings feed into the
// ratee's User.ratingAverage / User.ratingCount, which power the worker
// profile / reputation screen.
const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");
const User = require("./User");
const Job = require("./Job");

const Rating = sequelize.define(
  "Rating",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    jobId: {
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    raterId: {
      // the user who submitted the rating
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    rateeId: {
      // the user being rated
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    score: {
      type: DataTypes.INTEGER,
      allowNull: false,
      validate: { min: 1, max: 5 },
    },
    comment: {
      type: DataTypes.STRING,
      allowNull: true,
    },
  },
  {
    tableName: "ratings",
    timestamps: true,
    indexes: [
      // A rater can only rate a given job once (prevents duplicate ratings).
      { unique: true, fields: ["jobId", "raterId"] },
    ],
  }
);

Rating.belongsTo(Job, { foreignKey: "jobId" });
Rating.belongsTo(User, { as: "rater", foreignKey: "raterId" });
Rating.belongsTo(User, { as: "ratee", foreignKey: "rateeId" });

module.exports = Rating;
