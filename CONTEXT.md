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

## Finished

Every capability that can be completed in this repository is implemented and
verified, every remaining backend contract blocker is documented with an exact
contract and safe client fallback, and Android lifecycle or UI claims have
runtime evidence on an appropriate device or emulator.
