// tests/auth.test.js
// Automated tests for the authentication endpoints, run locally and by
// the GitHub Actions workflow on every push.
process.env.SQLITE_PATH = "./test.sqlite"; // isolated test database

const request = require("supertest");
const fs = require("fs");
const app = require("../server");
const sequelize = require("../config/db");

beforeAll(async () => {
  await sequelize.sync({ force: true });
});

afterAll(async () => {
  await sequelize.close();
  if (fs.existsSync("./test.sqlite")) fs.unlinkSync("./test.sqlite");
});

describe("Auth endpoints", () => {
  const testUser = {
    name: "Test Worker",
    email: "testworker@example.com",
    password: "password123",
    role: "worker",
  };

  test("registers a new user with a hashed password", async () => {
    const res = await request(app).post("/api/auth/register").send(testUser);
    expect(res.statusCode).toBe(201);
    expect(res.body.user.email).toBe(testUser.email);
    // The password itself must never be returned by the API
    expect(res.body.user.password).toBeUndefined();
    expect(res.body.user.passwordHash).toBeUndefined();
  });

  test("rejects duplicate registration", async () => {
    const res = await request(app).post("/api/auth/register").send(testUser);
    expect(res.statusCode).toBe(409);
  });

  test("rejects registration with a short password", async () => {
    const res = await request(app)
      .post("/api/auth/register")
      .send({ ...testUser, email: "short@example.com", password: "123" });
    expect(res.statusCode).toBe(400);
  });

  test("logs in with correct credentials and returns a token", async () => {
    const res = await request(app).post("/api/auth/login").send({
      email: testUser.email,
      password: testUser.password,
    });
    expect(res.statusCode).toBe(200);
    expect(res.body.token).toBeDefined();
  });

  test("rejects login with wrong password", async () => {
    const res = await request(app).post("/api/auth/login").send({
      email: testUser.email,
      password: "wrongpassword",
    });
    expect(res.statusCode).toBe(401);
  });
});
