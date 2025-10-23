#!/usr/bin/env node
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
  // Simple JSON-RPC server implementation
  const tools = {
    sql_query: {
      description: "Run a read-only SQL SELECT query against the OpenRemote PostgreSQL database.",
      inputSchema: {
        type: "object",
        properties: {
          sql: {
            type: "string",
            description: "A single SELECT statement without semicolons"
          },
          params: {
            type: "array",
            description: "Optional positional parameters for parameterized queries",
            items: {}
          }
        },
        required: ["sql"]
      }
    },
    health: {
      description: "Check database connectivity",
      inputSchema: {
        type: "object",
        properties: {}
      }
    }
  };

  // Handle JSON-RPC requests (newline-delimited JSON)
  let buffer = "";
  process.stdin.on('data', async (data) => {
    buffer += data.toString();
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (!line.trim()) continue;
      let request;
      try {
        request = JSON.parse(line);
      } catch (e) {
        // Ignore malformed partial frames; client will retry
        continue;
      }
      
      if (request.method === "initialize") {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          result: {
            protocolVersion: "2025-06-18",
            capabilities: {
              tools: {}
            },
            serverInfo: {
              name: "openremote-mcp-server",
              version: "0.1.0"
            }
          }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
      } else if (request.method === "notifications/initialized") {
        // Notification: no response required
        continue;
      } else if (request.method === "tools/list") {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          result: {
            tools: Object.entries(tools).map(([name, tool]) => ({
              name,
              ...tool
            }))
          }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
      } else if (request.method === "prompts/list") {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          result: { prompts: [] }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
      } else if (request.method === "resources/list") {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          result: { resources: [] }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
      } else if (request.method === "tools/call") {
        const { name, arguments: args } = request.params;
        
        try {
          let result;
          if (name === "sql_query") {
            const { sql, params } = args;
            assertReadOnly(sql);
            const client = await getPool().connect();
            try {
              const res = await client.query(sql, params ?? []);
              result = { 
                content: [{ 
                  type: "text", 
                  text: JSON.stringify({ 
                    rows: res.rows, 
                    rowCount: res.rowCount, 
                    fields: res.fields?.map(f => f.name) 
                  }) 
                }] 
              };
            } finally {
              client.release();
            }
          } else if (name === "health") {
            const client = await getPool().connect();
            try {
              const r = await client.query("select 1 as ok");
              result = { 
                content: [{ 
                  type: "text", 
                  text: JSON.stringify({ ok: r.rows?.[0]?.ok === 1 }) 
                }] 
              };
            } finally {
              client.release();
            }
          } else {
            throw new Error(`Unknown tool: ${name}`);
          }
          
          const response = {
            jsonrpc: "2.0",
            id: request.id,
            result
          };
          process.stdout.write(JSON.stringify(response) + '\n');
        } catch (error) {
          const response = {
            jsonrpc: "2.0",
            id: request.id,
            error: {
              code: -32603,
              message: error.message
            }
          };
          process.stdout.write(JSON.stringify(response) + '\n');
        }
      } else {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          error: {
            code: -32601,
            message: `Method not found: ${request.method}`
          }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
      }
    }
  });
}

main().catch((err) => {
  console.error("openremote-mcp-server error:", err?.stack || err);
  process.exit(1);
});