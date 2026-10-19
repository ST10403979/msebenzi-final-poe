// models/Media.js
// BLOB STORAGE: images (profile photos and job photos) are stored as binary
// large objects (BYTEA in PostgreSQL / BLOB in SQLite) in their own table and
// served back through GET /api/media/:id. The `jobs`/`users` tables only keep
// the media id, so listing jobs stays fast.
const { DataTypes } = require("sequelize");
const sequelize = require("../config/db");

const Media = sequelize.define(
  "Media",
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    ownerId: {
      // the user who uploaded the image
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    mimeType: {
      type: DataTypes.STRING,
      allowNull: false,
    },
    size: {
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    data: {
      type: DataTypes.BLOB("long"),
      allowNull: false,
    },
  },
  {
    tableName: "media",
    timestamps: true,
  }
);

module.exports = Media;
