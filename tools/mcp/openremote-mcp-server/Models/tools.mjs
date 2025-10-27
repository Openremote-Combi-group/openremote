export default {
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

export async function handleToolCall(toolName, args, getPool) {
  let result;

  if (toolName === "sql_query") {
    const { sql, params } = args;
    const queryResult = await executeSqlOperation(sql, params ?? [], ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']);
    result = {
      content: [{
        type: "text",
        text: JSON.stringify(queryResult)
      }]
    };
  } else if (toolName === "sql_insert") {
    const { sql, params } = args;
    const queryResult = await executeSqlOperation(sql, params ?? [], ['INSERT']);
    result = {
      content: [{
        type: "text",
        text: JSON.stringify(queryResult)
      }]
    };
  } else if (toolName === "sql_update") {
    const { sql, params } = args;
    const queryResult = await executeSqlOperation(sql, params ?? [], ['UPDATE']);
    result = {
      content: [{
        type: "text",
        text: JSON.stringify(queryResult)
      }]
    };
  } else if (toolName === "sql_delete") {
    const { sql, params } = args;
    const queryResult = await executeSqlOperation(sql, params ?? [], ['DELETE']);
    result = {
      content: [{
        type: "text",
        text: JSON.stringify(queryResult)
      }]
    };
  } else if (toolName === "sql_create") {
    const { sql, params } = args;
    const queryResult = await executeSqlOperation(sql, params ?? [], ['CREATE']);
    result = {
      content: [{
        type: "text",
        text: JSON.stringify(queryResult)
      }]
    };
  } else if (toolName === "health") {
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
    throw new Error(`Unknown tool: ${toolName}`);
  }

  return result;
}
