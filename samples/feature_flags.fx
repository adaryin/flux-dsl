# Feature flags configuration with inline schema validation

_schema {
  flags { type: "object", properties {
    enabled: { type: "bool", required: true }
    rollout_percent: { type: "number", min: 0, max: 100, default: 0 }
    segments: { type: "list", items { type: "string", enum: ["internal", "beta", "stable", "all"] } }
    owner: { type: "string", required: true }
    description: { type: "string" }
  } }
}

flags {
  new_checkout_flow {
    enabled: true
    rollout_percent: 25
    segments: ["internal", "beta"]
    owner: "checkout-team"
    description: "Gradual rollout of redesigned checkout"
  }

  dark_mode {
    enabled: true
    rollout_percent: 100
    segments: ["all"]
    owner: "ux-team"
  }

  ai_recommendations {
    enabled: false
    segments: ["internal"]
    owner: "ml-team"
    description: "ML-based product recommendations on homepage"
  }

  payment_3ds2 {
    enabled: true
    rollout_percent: 5
    segments: ["internal", "beta"]
    owner: "payments-team"
    description: "3D Secure 2.0 authentication for card payments"
  }

  experimental_search {
    enabled: true
    rollout_percent: 1
    segments: ["internal"]
    owner: "search-team"
    description: "Trial new search indexing pipeline"
  }
}
