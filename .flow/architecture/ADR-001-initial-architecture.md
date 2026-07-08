---
tags: [flowos, blueprint]
aliases: []
---

# ADR-001: Initial Architecture Selection

## Status
Accepted

## Context
We need to select the foundational architecture for CampelXGM.
Platform: mobile
App Type: native-app
Complexity: medium

## Decision
We chose the **offline-first** architecture.

## Consequences
- Requires adherence to payment, auth modules.
- Allows scaling up based on medium complexity.
