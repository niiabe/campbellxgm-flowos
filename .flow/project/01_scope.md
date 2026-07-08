---
tags: [flowos, blueprint]
aliases: []
---

# Project Scope

## Core Features
- payments
- authentication
- offline-only
- offline-support
- login
- extreme-mode-to-force-stop-all-the-apps

## Architecture
- **Type:** offline-first
- **Required Modules:** payment, auth

## Stack Recommendations
### framework
- **React Native**: Cross-platform, large community, code reuse
- **Flutter**: Excellent performance, single codebase, Material Design
- **Swift (iOS) / Kotlin (Android)**: Native performance, platform-specific features

### backend
- **Firebase**: BAAS, real-time sync, auth included
- **Node.js + Express**: Full-stack JS, scalable
- **Python + FastAPI**: Type-safe, async-native

### database
- **SQLite**: Embedded, zero-config, ideal for mobile
- **Firebase Firestore**: Real-time sync, offline support
- **Supabase**: PostgreSQL-based, real-time capabilities
