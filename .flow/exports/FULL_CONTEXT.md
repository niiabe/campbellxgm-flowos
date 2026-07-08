# FULL CONTEXT — CampelXGM

## Project Overview
CampbellXGM Campbell Xtreme Gaming Mode for android. is gaming mode app that has access to act as an admin but the admin can later be disabled. The app will force stop everything and put the phone also in DND mode if requisted . The only apps that will be run on the phone will be the settings , phone core and the allowed game and allowed apps . the needed game is added in the app and there is a launch button in the app that launches the game. multiple games can be added to it , one has to select the game then launch the game in the app to run it . 

This will take effect only if the game is started . And nothing but the game runs. It takes over the ram and CPU and any resources needed for the game to run smoothly. This should be able to boost ms of the game also .

I am looking at building an app so It can manage everything in the app . Not like a launcher but a game mode app . 


it should be able to have full control and shut down anything not needed for the game to run. 


the structure of the app should allow future advancement which include but not limited to subsctibition to the app, and or watching a ads or ads showing in the app when open for free users.  this stucture should be inplace but not implemented in the creation stage since everything needs to be functional before implemented. 

the payment , subsctiption , authentication(login) structure should be in place but not implemented . this should make later implementation simple 

## Platform
mobile

## Architecture
offline-first

## Phases
- Initialization: Set up project foundation and development environment
- Architecture Setup: Establish project architecture and core infrastructure
- Authentication: Implement secure authentication and authorization system
- Data Layer: Implement data storage, retrieval, and management
- Business Logic: Implement core business logic and feature functionality
- Interface Layer: Build user interface and user-facing features

## Stack Recommendations
[
  {
    "category": "framework",
    "options": [
      {
        "name": "React Native",
        "reason": "Cross-platform, large community, code reuse"
      },
      {
        "name": "Flutter",
        "reason": "Excellent performance, single codebase, Material Design"
      },
      {
        "name": "Swift (iOS) / Kotlin (Android)",
        "reason": "Native performance, platform-specific features"
      }
    ]
  },
  {
    "category": "backend",
    "options": [
      {
        "name": "Firebase",
        "reason": "BAAS, real-time sync, auth included"
      },
      {
        "name": "Node.js + Express",
        "reason": "Full-stack JS, scalable"
      },
      {
        "name": "Python + FastAPI",
        "reason": "Type-safe, async-native"
      }
    ]
  },
  {
    "category": "database",
    "options": [
      {
        "name": "SQLite",
        "reason": "Embedded, zero-config, ideal for mobile"
      },
      {
        "name": "Firebase Firestore",
        "reason": "Real-time sync, offline support"
      },
      {
        "name": "Supabase",
        "reason": "PostgreSQL-based, real-time capabilities"
      }
    ]
  }
]

## Deferred Decisions
[
  {
    "field": "backend",
    "reason": "No backend preference specified",
    "options": [
      "node.js",
      "python",
      "java",
      "go",
      "firebase"
    ],
    "preferred-option": "node.js",
    "trade-off-summary": "Resolving backend requires evaluating trade-offs between node.js, python, java, go, firebase.",
    "status": "pending",
    "suggested": null
  },
  {
    "field": "database",
    "reason": "No database preference specified",
    "options": [
      "postgresql",
      "mongodb",
      "sqlite",
      "mysql",
      "firebase"
    ],
    "preferred-option": "sqlite",
    "trade-off-summary": "Resolving database requires evaluating trade-offs between postgresql, mongodb, sqlite, mysql, firebase.",
    "status": "pending",
    "suggested": "sqlite"
  }
]

## Rules
All projects must follow sequential phase execution
No feature is added outside blueprint scope
Unknown values must become deferred_resolution nodes
All decisions must be logged in project memory
Export always reflects current blueprint state
Required modules: payment, auth
