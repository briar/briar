# Harbor

## Offline-First Communication and Intentional Productivity Platform

---

## Executive Summary

Harbor is a work-in-progress Android platform exploring offline-first communication, intentional productivity, and distraction-minimized interaction design.

The project is being built around a Briar-inspired foundation, with the goal of creating a resilient communication layer that can eventually integrate with external platforms while preserving local-first behavior, user control, and privacy-oriented architecture.

Harbor is not yet a finished product. It is currently in active development, with the immediate engineering focus on adding Telegram as the first external connector.

Telegram support is being implemented through TDLib. The staged Telegram login flow is close to completion: the app already has connector seams, feature flags, login state wiring, placeholder/login UI flow, and TDLib-facing authentication abstractions. The remaining work is focused on completing real TDLib integration, validating the end-to-end login path, and connecting successful Telegram authentication into Harbor’s account/linking model.

---

## Current Status

Harbor is currently an experimental product and engineering workbench.

The active development focus is:

* Keeping Briar as the canonical identity, storage, and transport foundation
* Adding Telegram as the first external connector
* Using TDLib for Telegram authentication and future Telegram integration
* Keeping Telegram support default-off while the integration is being validated
* Building the login flow incrementally through small, reversible seams
* Preserving Harbor password sign-in as the fallback path
* Avoiding broad message-sync or chat-bridging work until login is reliable

The Telegram login flow is currently near-complete at the staged implementation level. The project already contains the core Harbor-owned authentication state model and TDLib-facing facade needed to keep raw TDLib types isolated from the app UI.

---

## Product Direction

Harbor aims to become a personal communication and productivity layer that reduces dependence on attention-extracting platforms.

The long-term direction includes:

* Offline-first communication
* Connector-based integration with external messaging platforms
* Intentional, goal-aligned interaction flows
* Reduced exposure to algorithmic feeds and engagement loops
* Local-first state management
* Privacy-oriented architecture
* AI-assisted decision support and summarization
* Structured communication instead of passive consumption

The project is intentionally being developed as an incremental system rather than a fully finished app.

---

## Problem Statement

Modern productivity and communication tools are fragmented and frequently optimized for engagement rather than user intention.

Users often need to:

* Track tasks in one system
* Manage information in another
* Communicate through engagement-optimized platforms
* Consume algorithmic feeds unrelated to active goals
* Deal with constant cognitive interruption

Harbor explores a different model: communication and productivity should be structured around user intent, not platform retention.

The project aims to reduce unnecessary context switching by building a focused interface over communication, planning, and eventually goal-aligned information flows.

---

## Communication Layer

Harbor’s communication architecture is being developed in two layers.

### 1. Briar-Native Foundation

Briar remains the canonical foundation for Harbor’s identity, storage, and transport direction.

This gives Harbor a privacy-oriented and resilient base while external connectors are added incrementally.

Core principles:

* Local-first behavior
* Peer-oriented communication model
* Reduced reliance on centralized infrastructure
* Privacy-aligned transport philosophy
* Strong separation between internal Harbor identity and external platform connectors

### 2. External Connectors

Harbor is being designed to support external communication platforms through connector abstractions.

Telegram is the first connector currently being implemented.

The current Telegram work includes:

* TDLib-backed login integration
* Harbor-owned Telegram auth state model
* Feature-flagged login entry point
* Startup/login flow integration
* Harbor password fallback
* Staged identity handoff
* Connector readiness checks
* Default-off internal testing path

The goal is to make external platform integration possible without allowing Telegram-specific assumptions to leak across the whole app.

---

## Telegram Integration Status

Telegram support is actively being developed.

The current implementation direction is:

* TDLib is used as the Telegram integration layer
* Raw TDLib types are kept behind a Harbor-owned facade
* The login flow is staged through Harbor-owned auth states
* The Telegram connector remains default-off by default
* Internal builds can enable the Telegram login entry point explicitly
* Harbor password sign-in remains the guaranteed fallback
* Real Telegram login E2E depends on the local TDLib Java/JNI artifact setup

Current login states include:

* Identifier entry
* Code entry
* Password / 2FA entry
* Ready
* Recoverable error
* Missing TDLib artifact handling

The staged login flow is close to completion, but the integration is still considered work in progress until real TDLib login is validated end to end.

---

## Intentional Timeline Direction

Harbor may eventually include a structured timeline designed around active goals rather than passive scrolling.

This timeline is not the current implementation focus, but remains part of the broader product direction.

Possible future timeline items include:

* AI-curated updates relevant to current work
* Flashcards generated from learning goals
* Short task prompts derived from larger objectives
* Quick execution micro-tasks
* Expert-curated updates from selected sources

The purpose would be to redirect engagement toward useful progress rather than infinite consumption.

---

## Mental Resilience Direction

Harbor is being designed with mental resilience and attention protection in mind.

Design principles include:

* Avoiding infinite-scroll interaction patterns
* Separating communication from entertainment loops
* Prioritizing user intention over engagement
* Encouraging short, actionable progress cycles
* Reducing unnecessary cognitive interruption

This is a product direction rather than a completed feature set.

---

## Architecture Overview

Harbor follows a layered Android architecture focused on local-first behavior, connector abstraction, and incremental development.

### Current Stack Direction

* Kotlin
* Java where required by legacy Briar modules
* Jetpack Compose support being introduced
* MVVM-style state handling
* Coroutines and Flow where appropriate
* Briar-based foundation
* Connector-based external platform integration
* TDLib for Telegram support
* Local persistence as the preferred source of truth

### Architectural Principles

* Keep Briar canonical
* Keep external connectors isolated
* Keep raw TDLib types behind Harbor-owned wrappers
* Prefer feature flags for unfinished connector work
* Prefer small reversible seams over broad rewrites
* Keep UI driven by explicit state
* Avoid direct UI dependency on external network behavior
* Validate through targeted tests and end-to-end checks where useful

---

## Offline-First Strategy

Harbor is being built around local-first assumptions.

The intended model is:

* Local data drives UI state
* Network and connector operations are asynchronous
* External integrations should not block core app behavior
* Harbor password sign-in remains available even while connector login evolves
* Connector state should be explicit, recoverable, and reversible

This is especially important for Telegram integration, where Harbor should not become dependent on Telegram as the only access path.

---

## AI Layer Direction

Harbor may eventually include AI-assisted features as a decision-support layer.

Possible areas include:

* Conversation summarization
* Task extraction from messages
* Context-aware reminders
* Goal-aligned information filtering
* Structured review of communication history
* Personal workflow support

The goal would be to improve clarity and execution, not to add another engagement loop.

---

## Engineering Focus

Harbor currently serves as a production-oriented Android engineering project focused on:

* Briar-based architecture
* Connector abstraction
* TDLib integration
* Android startup and login flows
* Feature-flagged rollout
* Local-first state handling
* Incremental migration toward Kotlin and Compose
* Testable seams around external platform integration
* Careful control of PR size and reviewability

The immediate engineering priority is completing and validating Telegram login while keeping the implementation narrow, reversible, and compatible with Harbor’s Briar-native foundation.

---

## Scalability Direction

Planned evolution includes:

* More complete Telegram connector support
* Account linking between Harbor identity and external identities
* Message-path experiments after login is reliable
* Feature-level modularization
* Expanded connector model
* Improved local sync and conflict handling
* AI-assisted workflow features
* Cross-platform strategy

Harbor is intentionally being built step by step so these expansions do not require rewriting the foundation.

---

## Development Status Summary

Harbor is not yet a finished product.

Current state:

* Briar-native foundation: in place
* Telegram connector: in progress
* TDLib login flow: near-complete staged implementation
* Real Telegram login E2E: pending final TDLib artifact setup and validation
* Telegram message syncing: not yet the focus
* Compose migration: early support being introduced
* Kotlin migration: incremental, constrained by legacy module build setup

The project should be understood as an active engineering system, not a completed application.