# Staging overrides
include "base.fx"

app {
  logging { level: "info" }

  database {
    host: "db-staging.internal"
    port: 5432
    name: "myapp_staging"
    pool { min: 3, max: 15 }
  }

  cache {
    host: "redis-staging.internal"
    ttl_sec: 1800
  }

  rate_limit { requests_per_min: 500 }
}
