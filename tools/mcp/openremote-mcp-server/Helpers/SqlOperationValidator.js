export default function validateSqlOperation(sql, allowedOperations = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE']) {
  const trimmed = sql.trim();
  if (trimmed.includes(';')) throw new Error('Only single statements without semicolons are allowed');

  const sqlUpper = trimmed.toUpperCase();
  const operation = sqlUpper.split(' ')[0];

  if (!allowedOperations.includes(operation)) {
    throw new Error(`Operation '${operation}' is not allowed. Allowed operations: ${allowedOperations.join(', ')}`);
  }

  const forbiddenPatterns = [
    /\b(CREATE|DROP|ALTER|TRUNCATE|GRANT|REVOKE)\b/i,
    /\b(VACUUM|ANALYZE)\b/i,
    /\b(MERGE|CALL|EXECUTE)\b/i
  ];

  for (const pattern of forbiddenPatterns) {
    if (pattern.test(trimmed)) {
      throw new Error(`Forbidden SQL operation detected: ${pattern.source}`);
    }
  }
}