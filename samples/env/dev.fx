# Development overrides
include "base.fx"

app {
  logging { level: "debug" }

  database {
    host: "localhost"
    name: "myapp_dev"
  }

  cache { host: "localhost" }

  rate_limit { enabled: false }

  # Extra dev-only features
  debug {
    profile: true
    trace_sql: true
    hot_reload: true
  }
}
