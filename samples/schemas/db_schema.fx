# Database config validation schema
# All values are rule descriptors — no actual data here

connection {
  host: { type: "string", required: true }
  port: { type: "number", default: 5432, min: 1, max: 65535 }
  database: { type: "string", required: true }
  user: { type: "string", required: true }
  password: { type: "string", required: true }
  ssl { type: "string", enum: ["disable", "require", "verify-ca", "verify-full"], default: "disable" }
}

pool {
  min: { type: "number", default: 2, min: 0 }
  max: { type: "number", default: 10, min: 1, max: 100 }
  acquire_timeout_ms: { type: "number", default: 30000, min: 1000 }
  idle_timeout_ms: { type: "number", default: 600000, min: 0 }
}

replica { type: "object", properties {
  enabled: { type: "bool", default: false }
  hosts: { type: "list", items { type: "string" } }
  read_only: { type: "bool", default: true }
} }

init { type: "list", items { type: "string" } }
