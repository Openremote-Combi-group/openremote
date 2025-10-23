#!/usr/bin/env node
import { createServer, stdioServerTransport } from "@modelcontextprotocol/sdk/server/index.js";
import { z } from "@modelcontextprotocol/sdk/server/validation.js";
import pkg from "pg";

const { Pool } = pkg;

function getEnv(name, fallback) {
  const v = process.env[name];
  if (v === undefined || v === "") {
    if (fallback !== undefined) return fallback;
    throw new Error(`Missing required env var ${name}`);
  }
  return v;
}

// Create PostgreSQL pool lazily
let pool;
function getPool() {
  if (!pool) {
    pool = new Pool({
      host: getEnv("PGHOST", "localhost"),
      port: Number(getEnv("PGPORT", "5432")),
      user: getEnv("PGUSER", "postgres"),
      password: getEnv("PGPASSWORD", "postgres"),
      database: getEnv("PGDATABASE", "openremote"),
      ssl: getEnv("PGSSL", "false").toLowerCase() === "true" ? { rejectUnauthorized: false } : false
    });
  }
  return pool;
}

// Simple SELECT-only validator to keep read-only
function assertReadOnly(sql) {
  const trimmed = sql.trim();
  // Disallow semicolons to prevent batching
  if (trimmed.includes(";")) throw new Error("Only single SELECT statements without semicolons are allowed");
  const startsWithSelect = /^select\s/i.test(trimmed);
  if (!startsWithSelect) throw new Error("Only SELECT queries are allowed");
  const forbidden = /(insert|update|delete|drop|alter|create|grant|revoke|truncate|merge|call|execute|vacuum|analyze)\s/i;
  if (forbidden.test(trimmed)) throw new Error("Mutation or DDL keywords are not allowed");
}

async function main() {
  const transport = stdioServerTransport();
  const server = createServer({
    name: "openremote-mcp-server",
    version: "0.1.0",
    transport,
  });

  server.tool("sql_query", {
    description: "Run a read-only SQL SELECT query against the OpenRemote PostgreSQL database.",
    schema: z.object({
      sql: z.string().describe("A single SELECT statement without semicolons"),
      params: z.array(z.any()).optional().describe("Optional positional parameters for parameterized queries")
    }),
  }, async ({ sql, params }) => {
    assertReadOnly(sql);
    const client = await getPool().connect();
    try {
      const res = await client.query(sql, params ?? []);
      return { rows: res.rows, rowCount: res.rowCount, fields: res.fields?.map(f => f.name) };
    } finally {
      client.release();
    }
  });

  server.tool("health", {
    description: "Check database connectivity",
    schema: z.object({}),
  }, async () => {
    const client = await getPool().connect();
    try {
      const r = await client.query("select 1 as ok");
      return { ok: r.rows?.[0]?.ok === 1 };
    } finally {
      client.release();
    }
  });

  await server.start();
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error("openremote-mcp-server error:", err?.stack || err);
  process.exit(1);
});


