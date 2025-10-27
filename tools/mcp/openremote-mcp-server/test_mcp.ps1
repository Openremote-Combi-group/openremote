# 1. List some public tables (SELECT via sql_query)
Write-Output '{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "sql_query", "arguments": {"sql": "SELECT table_name FROM information_schema.tables WHERE table_schema = ''public'' LIMIT 3"}}}' | docker compose exec -T mcp node index.mjs

# 3. Insert test data (INSERT via sql_insert)
Write-Output '{"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "sql_insert", "arguments": {"sql": "INSERT INTO test_table (name) VALUES (''Test Item 1'')"}}}' | docker compose exec -T mcp node index.mjs

# 4. Update test data (UPDATE via sql_update)
Write-Output '{"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "sql_update", "arguments": {"sql": "UPDATE test_table SET name = ''Updated Test Item'' WHERE name = ''Test Item 1''"}}}' | docker compose exec -T mcp node index.mjs

# 5. Delete test data (DELETE via sql_delete)
Write-Output '{"jsonrpc": "2.0", "id": 5, "method": "tools/call", "params": {"name": "sql_delete", "arguments": {"sql": "DELETE FROM test_table WHERE name = ''Updated Test Item''"}}}' | docker compose exec -T mcp node index.mjs

# 6. Create another table (CREATE via sql_create)
Write-Output '{"jsonrpc": "2.0", "id": 6, "method": "tools/call", "params": {"name": "sql_create", "arguments": {"sql": "CREATE TABLE IF NOT EXISTS another_test_table (id SERIAL PRIMARY KEY, description TEXT)"}}}' | docker compose exec -T mcp node index.mjs

# 7. Health check
Write-Output '{"jsonrpc": "2.0", "id": 7, "method": "tools/call", "params": {"name": "health", "arguments": {}}}' | docker compose exec -T mcp node index.mjs
