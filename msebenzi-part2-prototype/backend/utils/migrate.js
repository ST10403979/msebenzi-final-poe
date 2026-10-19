// utils/migrate.js
// sequelize.sync() creates missing TABLES but never adds new COLUMNS to tables that
// already exist. The Part 2 database on Render already has users/jobs tables, so this
// tiny migration adds the columns introduced in Part 3 when they are missing.
// It is safe to run on every start-up (it only adds what is absent).
const { DataTypes } = require("sequelize");

const NEW_COLUMNS = {
  users: {
    avatarMediaId: { type: DataTypes.INTEGER, allowNull: true },
    fcmToken: { type: DataTypes.STRING(512), allowNull: true },
    googleId: { type: DataTypes.STRING, allowNull: true },
  },
  jobs: {
    photoMediaId: { type: DataTypes.INTEGER, allowNull: true },
    clientRef: { type: DataTypes.STRING, allowNull: true },
  },
};

async function addMissingColumns(sequelize) {
  const qi = sequelize.getQueryInterface();
  for (const [table, columns] of Object.entries(NEW_COLUMNS)) {
    const existing = await qi.describeTable(table);
    for (const [name, definition] of Object.entries(columns)) {
      if (!existing[name]) {
        await qi.addColumn(table, name, definition);
        console.log(`[db] Added column ${table}.${name}`);
      }
    }
  }
}

module.exports = { addMissingColumns };
