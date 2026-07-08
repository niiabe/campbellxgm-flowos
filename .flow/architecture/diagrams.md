---
tags: [flowos, blueprint]
aliases: []
---

# Architecture Diagrams

## High-Level Architecture
```mermaid
graph TD
    Client[mobile Client] --> Frontend[react / vue (with service worker)]
    Frontend --> Backend[local-first (IndexedDB / SQLite)]
    Backend --> DB[(local storage / IndexedDB / SQLite)]
```
