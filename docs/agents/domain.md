# Domain docs

How the engineering skills consume this repository's domain documentation.

## Before exploring

Read these when they exist:

- `CONTEXT.md` at the repository root.
- `docs/adr/` entries relevant to the area being changed.

If they do not exist, proceed silently. The domain-modeling workflow creates them only when resolved terminology or decisions need durable documentation.

## Layout

This is a single-context repository:

```text
/
|-- CONTEXT.md
|-- docs/adr/
`-- app/
```

## Vocabulary

Use the terms defined in `CONTEXT.md`. If a required concept is missing, reconsider whether the change is introducing unnecessary language or record the genuine gap for domain modeling.

## ADR conflicts

Surface any conflict with an existing ADR explicitly instead of silently overriding it.
