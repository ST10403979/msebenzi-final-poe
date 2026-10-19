// scripts/seed.js
// Fills the database with demo accounts and one job for each service so the app looks alive
// in the demo video. Safe to run more than once (it skips anything that already exists).
//
//   node scripts/seed.js            (uses DATABASE_URL, or the local SQLite file)
//
// Demo logins (password for all: Demo@1234)
//   household:  thandi@demo.msebenzi   worker: sipho@demo.msebenzi   worker: lerato@demo.msebenzi
require("dotenv").config();
const bcrypt = require("bcryptjs");
const sequelize = require("../config/db");
const User = require("../models/User");
const Job = require("../models/Job");
require("../models/Media");
require("../models/Rating");
require("../models/Notification");
const { addMissingColumns } = require("../utils/migrate");

const PASSWORD = "Demo@1234";

const JOBS = [
  ["Deep clean 3-bedroom house", "Kitchen, bathrooms and floors before family arrives.", "cleaning", "Gqeberha", 650],
  ["Trim hedges and mow lawn", "Front and back garden, about 200 m2.", "gardening", "Mthatha", 350],
  ["Fix kitchen cupboard doors", "Two hinges broken and a loose shelf.", "handyman", "East London", 280],
  ["Paint lounge walls", "One room, paint supplied. Two coats please.", "painting", "Cape Town", 1200],
  ["Leaking bathroom tap", "Tap drips constantly, washer probably worn.", "plumbing", "Gqeberha", 300],
  ["Install two plug sockets", "Replace old sockets in the study.", "electrical", "Durban", 450],
  ["Help moving a 2-bedroom flat", "Need two people and a bakkie on Saturday morning.", "moving", "Johannesburg", 900],
  ["Wash and iron school uniforms", "Five children, weekly.", "laundry", "Soweto", 220],
];

async function findOrCreate(name, email, role, extra = {}) {
  let user = await User.findOne({ where: { email } });
  if (!user) {
    user = await User.create({
      name, email, role,
      passwordHash: await bcrypt.hash(PASSWORD, 10),
      ...extra,
    });
    console.log(`Created ${role}: ${email}`);
  }
  return user;
}

(async () => {
  await sequelize.sync();
  await addMissingColumns(sequelize);

  const household = await findOrCreate("Thandi Nkosi", "thandi@demo.msebenzi", "household");
  await findOrCreate("Sipho Dlamini", "sipho@demo.msebenzi", "worker", {
    skills: "cleaning,gardening,handyman", bio: "Reliable and hard-working. 5 years' experience.",
  });
  await findOrCreate("Lerato Molefe", "lerato@demo.msebenzi", "worker", {
    skills: "painting,plumbing,electrical", bio: "Qualified tradesperson serving the Eastern Cape.",
    verificationStatus: "verified",
  });

  for (const [title, description, category, location, budget] of JOBS) {
    const exists = await Job.findOne({ where: { title, postedById: household.id } });
    if (!exists) {
      await Job.create({ title, description, category, location, budget, postedById: household.id });
      console.log(`Created job: ${title}`);
    }
  }
  console.log("Seed complete.");
  await sequelize.close();
})().catch((err) => {
  console.error("Seed failed:", err.message);
  process.exit(1);
});
