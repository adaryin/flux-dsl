# Production overrides
include "base.fx"

app {
  logging { level: "warn" }

  database {
    host: "db-prod.internal"
    port: 5432
    name: "myapp_prod"
    pool { min: 5, max: 30 }
  }

  cache {
    host: "redis-prod.internal"
    ttl_sec: 900
  }

  rate_limit { requests_per_min: 10000 }

  # Multi-region failover
  regions [
    { name: "us-east-1", weight: 60, database { host: "db-us-east.internal" } }
    { name: "eu-west-1", weight: 30, database { host: "db-eu-west.internal" } }
    { name: "ap-southeast-1", weight: 10, database { host: "db-ap-southeast.internal" } }
  ]
}
