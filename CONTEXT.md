# Hermes Android domain glossary

## Desktop parity

The same user-observable Hermes capability that ships in the audited Hermes
Desktop and backend contract. Parity preserves outcomes and safety properties;
it does not require copying Electron-specific implementation details.

## Android-native adaptation

An Android platform expression of a Desktop capability, using Android lifecycle,
navigation, storage, sharing, notification, accessibility, and security
conventions while preserving Desktop parity.

## Backend contract blocker

A parity requirement that cannot be made reliable by the Android client alone
because Hermes does not yet expose the necessary server-owned protocol. Backend
contract work may be proposed or implemented in the audited Hermes source;
upstream Android implementations are not inputs to this repository.

## Product home

One of the five stable native navigation roots: Chats, Artifacts, Automations,
Manage, or App settings. A durable destination belongs to exactly one product
home even when an older persisted route is still accepted for recovery.

## Durable destination

A typed navigation identity containing only the backend, profile, and stable
resource identifiers required to restore a Hermes surface. Runtime process IDs,
credentials, transcripts, attachment payloads, and WebSocket state are never
route data.

## App settings

Preferences owned by this Android installation, such as appearance and secure
screen behavior. App settings never inherit backend or profile scope; remote
Hermes configuration belongs under Manage.

## Finished

Every capability that can be completed in this repository is implemented and
verified, every remaining backend contract blocker is documented with an exact
contract and safe client fallback, and Android lifecycle or UI claims have
runtime evidence on an appropriate device or emulator.
