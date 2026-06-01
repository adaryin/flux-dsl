# Default database configuration
connection {
  host: "localhost"
  port: 5432
  database: "myapp"
  user: "app_user"
  password: "changeme"
  ssl: "disable"
}

pool {
  min: 2
  max: 10
  acquire_timeout_ms: 30000
  idle_timeout_ms: 600000
}

replica {
  enabled: false
  hosts: []
  read_only: true
}

init: [
  "CREATE EXTENSION IF NOT EXISTS pgcrypto"
  "CREATE EXTENSION IF NOT EXISTS uuid-ossp"
]
