# Native shell direction — 2026-08-07

## Selected direction

Use the session-atlas-first drawer structure from direction A as the compact
navigation model, the flat categorized Manage hierarchy from direction C, and
the conversation content treatments from direction B without keeping a crowded
five-item bottom bar visible inside a focused conversation.

This preserves the current cobalt/cream Hermes identity while making hierarchy,
back behaviour, and destination ownership Android-native. It also maps directly
to the typed route and adaptive shell decisions in Wayfinder issues #7 and #9.

## References

- [Direction A — session atlas and modal drawer](native-shell-directions/a-session-atlas-drawer.png)
- [Direction B — conversation content and artifact treatment](native-shell-directions/b-conversation-bottom-navigation.png)
- [Direction C — categorized Manage hierarchy](native-shell-directions/c-manage-three-destination.png)

## Constraints carried into implementation

- Preserve Hermes skins, artwork, type character, cream-on-cobalt contrast, and
  restrained technical texture.
- Compact navigation uses a modal drawer and route stack. Expanded navigation
  uses a permanent rail/drawer with list-detail panes.
- Keep the primary product hierarchy to Chats, Artifacts, Automations, Manage,
  and App settings without forcing all five into a cramped bottom bar.
- Avoid generic purple/pink AI palettes, glassmorphism, dashboard tile spam,
  nested cards, tiny text, and iOS navigation conventions.
- All interactive targets remain at least 48dp and every structural decision
  must survive large text, RTL, reduced motion, and predictive back.
