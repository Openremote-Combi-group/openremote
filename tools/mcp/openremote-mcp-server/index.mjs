import pkg from "pg";
import tools from "./Models/tools.mjs";
import validateSqlOperation from "./Helpers/SqlOperationValidator.js";

const { Pool } = pkg;

function getEnv(name, fallback) {
  const v = process.env[name];
  if (v === undefined || v === "") {
    if (fallback !== undefined) return fallback;
    throw new Error(`Missing required env var ${name}`);
  }
  return v;
}

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
    console.log("Postgres pool created with config:", {
      host: getEnv("PGHOST", "localhost"),
      port: Number(getEnv("PGPORT", "5432")),
      user: getEnv("PGUSER", "postgres"),
      database: getEnv("PGDATABASE", "openremote"),
      ssl: getEnv("PGSSL", "false")
    });
  }
  return pool;
}

async function executeSqlOperation(sql, params = [], allowedOperations = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']) {
  try {
    validateSqlOperation(sql, allowedOperations);
    console.log("Executing SQL:", sql, "Params:", params, "Allowed:", allowedOperations);

    const client = await getPool().connect();
    try {
      const res = await client.query(sql, params);
      console.log("Query result:", {
        rows: res.rows,
        rowCount: res.rowCount,
        fields: res.fields?.map(f => f.name),
        command: res.command
      });
      return {
        rows: res.rows,
        rowCount: res.rowCount,
        fields: res.fields?.map(f => f.name),
        command: res.command
      };
    } catch (error) {
      console.error("SQL execution failed:", error.message, "SQL:", sql);
      throw new Error(`SQL execution failed: ${error.message}. SQL: ${sql}`);
    } finally {
      client.release();
    }
  } catch (err) {
    console.error("Validation error:", err.message, "SQL:", sql);
    throw err;
  }
}

async function main() {
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
        console.error("JSON parse error:", e, "Input line:", line);
        continue;
      }

      console.log("Received request:", request);

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
        console.log("Sent initialize response");
      } else if (request.method === "notifications/initialized") {
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
        console.log("Sent tools/list response");
      } else if (request.method === "prompts/list") {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          result: { prompts: [] }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
        console.log("Sent prompts/list response");
      } else if (request.method === "resources/list") {
        const response = {
          jsonrpc: "2.0",
          id: request.id,
          result: { resources: [] }
        };
        process.stdout.write(JSON.stringify(response) + '\n');
        console.log("Sent resources/list response");
      } else if (request.method === "tools/call") {
        const { name, arguments: args } = request.params;
        try {
          let result;
          if (name === "sql_query") {
            const { sql, params } = args;
            result = {
              content: [{
                type: "text",
                text: JSON.stringify(await executeSqlOperation(sql, params ?? [], ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']))
              }]
            };
          } else if (name === "sql_insert") {
            const { sql, params } = args;
            result = {
              content: [{
                type: "text",
                text: JSON.stringify(await executeSqlOperation(sql, params ?? [], ['INSERT']))
              }]
            };
          } else if (name === "sql_update") {
            const { sql, params } = args;
            result = {
              content: [{
                type: "text",
                text: JSON.stringify(await executeSqlOperation(sql, params ?? [], ['UPDATE']))
              }]
            };
          } else if (name === "sql_delete") {
            const { sql, params } = args;
            result = {
              content: [{
                type: "text",
                text: JSON.stringify(await executeSqlOperation(sql, params ?? [], ['DELETE']))
              }]
            };
          } else if (name === "sql_create") {
            const { sql, params } = args;
            result = {
              content: [{
                type: "text",
                text: JSON.stringify(await executeSqlOperation(sql, params ?? [], ['CREATE']))
              }]
            };
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
            console.error("Unknown tool attempted:", name);
            throw new Error(`Unknown tool: ${name}`);
          }

          const response = {
            jsonrpc: "2.0",
            id: request.id,
            result
          };
          process.stdout.write(JSON.stringify(response) + '\n');
          console.log("Sent tools/call response:", response);
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
          console.error("Sent error response:", response);
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
        console.error("Method not found error:", response);
      }
    }
  });
}

main().catch((err) => {
  console.error("openremote-mcp-server error:", err?.stack || err);
  process.exit(1);
});
