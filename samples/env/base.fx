# Base config — shared across all environments
app {
  name: "myapp"
  version: "2.1.0"

  logging {
    level: "info"
    format: "json"
    output: "stdout"
  }

  database {
    host: "localhost"
    port: 5432
    name: "myapp"
    pool { min: 2, max: 10 }
  }

  cache {
    host: "localhost"
    port: 6379
    ttl_sec: 3600
  }

  rate_limit {
    enabled: true
    requests_per_min: 100
  }
}
