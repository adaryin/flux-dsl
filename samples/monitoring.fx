# Monitoring configuration — Prometheus alerts + Grafana dashboards

project: "myapp"
environment: "production"

prometheus {
  global {
    scrape_interval: "15s"
    evaluation_interval: "30s"
  }

  rules {
    groups [
      {
        name: "myapp_alerts"
        interval: "30s"
        rules [
          {
            alert: "HighErrorRate"
            # Pipe block — full PromQL, no escaping needed
            expr: {
              |
              rate(http_requests_total{service="myapp", status=~"5.."}[5m])
              /
              rate(http_requests_total{service="myapp"}[5m])
              > 0.05
            }
            for: "5m"
            labels { severity: "critical" }
            annotations {
              summary: "High error rate on myapp"
              description: "Error rate is {{ $value | humanizePercentage }} — threshold 5%"
            }
          }

          {
            alert: "HighLatency"
            expr: {
              |
              histogram_quantile(0.95,
                rate(http_request_duration_seconds_bucket{service="myapp"}[5m])
              ) > 0.5
            }
            for: "10m"
            labels { severity: "warning" }
            annotations {
              summary: "P95 latency exceeds 500ms"
              description: "Current P95: {{ $value }}s"
            }
          }
        ]
      }
    ]
  }
}

grafana {
  dashboards {
    overview {
      title: "MyApp Overview"
      refresh: "30s"
      timezone: "UTC"

      panels [
        {
          title: "Requests per second"
          type: "timeseries"
          datasource: "Prometheus"
          targets [
            {
              expr: {
                |
                sum(rate(http_requests_total{service="myapp"}[5m]))
                by (status)
              }
            }
          ]
        }

        {
          title: "CPU / Memory"
          type: "stat"
          datasource: "Prometheus"
          targets [
            { expr: "sum(container_cpu_usage_seconds_total{app='myapp'})" }
            { expr: "sum(container_memory_usage_bytes{app='myapp'})" }
          ]
        }
      ]
    }
  }

  datasources [
    {
      name: "Prometheus"
      type: "prometheus"
      url: "http://prometheus:9090"
      access: "proxy"
      is_default: true
    }
  ]
}

# Note: All { | ... } pipe blocks are literal — no escaping of {}, [], /, or # inside them
