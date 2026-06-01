# Multi-service deployment config — FluxDSL translation of a docker-compose-like format

project: "myapp"
version: "2.1.0"

services {
  api {
    image: "myapp/api:2.1.0"
    replicas: 3
    port: 8080

    env {
      DB_HOST: "db.internal"
      DB_PORT: 5432
      LOG_LEVEL: "info"
    }

    resources {
      cpu: "0.5"
      memory: "512Mi"
    }

    health_check {
      path: "/health"
      interval_sec: 15
      timeout_sec: 5
    }
  }

  worker {
    image: "myapp/worker:2.1.0"
    replicas: 2

    env: {
      QUEUE: "default"
      BATCH_SIZE: 100
    }

    resources {
      cpu: "1.0"
      memory: "1Gi"
    }
  }

  cache {
    image: "redis:7-alpine"
    replicas: 1
    port: 6379
    persist: true
  }
}

# Global settings
networks: ["internal", "monitoring"]

secrets {
  db_password { from_vault: "prod/db/password" }
  api_key    { from_vault: "prod/api/key" }
}

# Multi-line logging config — YAML/JSON inside FluxDSL, no escaping needed
logging: {
  |
  {
    "level": "info",
    "format": "json",
    "outputs": ["stdout", "file"],
    "file": {
      "path": "/var/log/myapp.log",
      "max_size_mb": 100,
      "max_files": 7
    }
  }
}
