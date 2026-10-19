// config/db.js
// Sets up the database connection for the Msebenzi API.
//
// - In production (Render, or any host that gives you a DATABASE_URL),
//   we connect to PostgreSQL, exactly as designed in the Part 1 document.
// - In local development, if no DATABASE_URL is set, we fall back to a
//   local SQLite file so the API can be run and tested without first
//   provisioning a Postgres server.
const { Sequelize } = require("sequelize");
require("dotenv").config();

let sequelize;

if (process.env.DATABASE_URL) {
  // Hosted Postgres (e.g. Render, Railway, Supabase, ElephantSQL, etc.)
  sequelize = new Sequelize(process.env.DATABASE_URL, {
    dialect: "postgres",
    protocol: "postgres",
    logging: false,
    dialectOptions: {
      ssl:
        process.env.DB_SSL === "false"
          ? false
          : {
              require: true,
              rejectUnauthorized: false,
            },
    },
  });
} else {
  // Local fallback so the project can be demoed / tested without Postgres.
  sequelize = new Sequelize({
    dialect: "sqlite",
    storage: process.env.SQLITE_PATH || "./msebenzi.sqlite",
    logging: false,
  });
  console.log(
    "[db] No DATABASE_URL found — using local SQLite file for development."
  );
}

module.exports = sequelize;
