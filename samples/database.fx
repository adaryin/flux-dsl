# Production database configuration
@schema "schemas/db_schema.fx"
include "defaults/db_base.fx"

connection {
  host: "db.prod.internal"
  port: 5432
  database: "myapp_prod"
  user: "myapp_svc"
  # password loaded from vault, not stored here
  ssl: "verify-full"
}

pool {
  min: 5
  max: 25
  acquire_timeout_ms: 15000
}

replica {
  enabled: true
  hosts: [
    "db-replica-1.prod.internal"
    "db-replica-2.prod.internal"
  ]
}

init: [
  {
    |
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version     TEXT PRIMARY KEY,
      applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    );
  }

  {
    |
    CREATE INDEX IF NOT EXISTS idx_migrations_applied
      ON schema_migrations (applied_at);
  }
]
