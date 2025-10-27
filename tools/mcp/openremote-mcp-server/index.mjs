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
  }
  return pool;
}

async function executeSqlOperation(sql, params = [], allowedOperations = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']) {
  validateSqlOperation(sql, allowedOperations);

  const client = await getPool().connect();
  try {
    const res = await client.query(sql, params);
    return {
      rows: res.rows,
      rowCount: res.rowCount,
      fields: res.fields?.map(f => f.name),
      command: res.command
    };
  } catch (error) {
    throw new Error(`SQL execution failed: ${error.message}. SQL: ${sql}`);
  } finally {
    client.release();
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
              const queryResult = await executeSqlOperation(sql, params ?? [], ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']);
              result = {
                content: [{
                  type: "text",
                  text: JSON.stringify(queryResult)
                }]
              };
            } else if (name === "sql_insert") {
              const { sql, params } = args;
              const queryResult = await executeSqlOperation(sql, params ?? [], ['INSERT']);
              result = {
                content: [{
                  type: "text",
                  text: JSON.stringify(queryResult)
                }]
              };
            } else if (name === "sql_update") {
              const { sql, params } = args;
              const queryResult = await executeSqlOperation(sql, params ?? [], ['UPDATE']);
              result = {
                content: [{
                  type: "text",
                  text: JSON.stringify(queryResult)
                }]
              };
            } else if (name === "sql_delete") {
              const { sql, params } = args;
              const queryResult = await executeSqlOperation(sql, params ?? [], ['DELETE']);
              result = {
                content: [{
                  type: "text",
                  text: JSON.stringify(queryResult)
                }]
              };
            } else if (name === "sql_create") {
              const { sql, params } = args;
              const queryResult = await executeSqlOperation(sql, params ?? [], ['CREATE']);
              result = {
                content: [{
                  type: "text",
                  text: JSON.stringify(queryResult)
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