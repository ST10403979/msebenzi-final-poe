// models/Job.js
// A job posted by a household user. Matches the "transparent job card"
// concept from the Part 1 design: service, distance/location, time,
// budget and status must all be visible before a worker accepts.
const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");
const User = require("./User");

const Job = sequelize.define(
  "Job",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    title: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    description: {
      type: DataTypes.TEXT,
      allowNull: false,
    },
    category: {
      // e.g. "cleaning", "gardening", "handyman"
      type: DataTypes.STRING,
      allowNull: false,
    },
    location: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    budget: {
      type: DataTypes.FLOAT,
      allowNull: false,
    },
    status: {
      type: DataTypes.ENUM("open", "accepted", "completed", "cancelled"),
      allowNull: false,
      defaultValue: "open",
    },
    postedById: {
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    acceptedById: {
      type: DataTypes.INTEGER,
      allowNull: true,
    },
    photoMediaId: {
      // optional photo of the job - id of a row in the `media` table
      type: DataTypes.INTEGER,
      allowNull: true,
    },
    clientRef: {
      // random id generated on the phone; lets the API ignore a duplicate
      // when an offline "create job" is replayed more than once
      type: DataTypes.STRING,
      allowNull: true,
    },
  },
  {
    tableName: "jobs",
    timestamps: true,
  }
);

// A job belongs to the household that posted it, and (optionally) the
// worker who accepted it.
Job.belongsTo(User, { as: "postedBy", foreignKey: "postedById" });
Job.belongsTo(User, { as: "acceptedBy", foreignKey: "acceptedById" });

module.exports = Job;
