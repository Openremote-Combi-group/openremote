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

function validateSqlOperation(sql, allowedOperations = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']) {
  const trimmed = sql.trim();
  if (trimmed.includes(';')) throw new Error('Only single statements without semicolons are allowed');

  const sqlUpper = trimmed.toUpperCase();
  const operation = sqlUpper.split(' ')[0];

  if (!allowedOperations.includes(operation)) {
    throw new Error(`Operation '${operation}' is not allowed. Allowed operations: ${allowedOperations.join(', ')}`);
  }

  // Additional safety checks
  const forbiddenPatterns = [
    /\b(DROP|ALTER|TRUNCATE|GRANT|REVOKE)\b/i,
    /\b(VACUUM|ANALYZE)\b/i,
    /\b(MERGE|CALL|EXECUTE)\b/i
  ];

  for (const pattern of forbiddenPatterns) {
    if (pattern.test(trimmed)) {
      throw new Error(`Forbidden SQL operation detected: ${pattern.source}`);
    }
  }
}

async function executeSqlOperation(sql, params = [], allowedOperations = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']) {
  // Validate the SQL operation
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
    // Provide more detailed error information
    throw new Error(`SQL execution failed: ${error.message}. SQL: ${sql}`);
  } finally {
    client.release();
  }
}

async function main() {
  const tools = {
    sql_query: {
      description: "Run a SQL query against the OpenRemote PostgreSQL database (SELECT, INSERT, UPDATE, DELETE, CREATE operations supported).",
      inputSchema: {
        type: "object",
        properties: {
          sql: {
            type: "string",
            description: "A single SQL statement without semicolons (SELECT, INSERT, UPDATE, DELETE, CREATE)"
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
    sql_insert: {
      description: "Run an INSERT SQL statement to add new records to the database.",
      inputSchema: {
        type: "object",
        properties: {
          sql: {
            type: "string",
            description: "An INSERT statement without semicolons"
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
    sql_update: {
      description: "Run an UPDATE SQL statement to modify existing records in the database.",
      inputSchema: {
        type: "object",
        properties: {
          sql: {
            type: "string",
            description: "An UPDATE statement without semicolons"
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
    sql_delete: {
      description: "Run a DELETE SQL statement to remove records from the database.",
      inputSchema: {
        type: "object",
        properties: {
          sql: {
            type: "string",
            description: "A DELETE statement without semicolons"
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
    sql_create: {
      description: "Run a CREATE SQL statement to create tables, indexes, or other database objects.",
      inputSchema: {
        type: "object",
        properties: {
          sql: {
            type: "string",
            description: "A CREATE statement without semicolons"
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