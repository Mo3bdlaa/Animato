# Round-one mockups (Claude Design)

Open any `.dc.html` in a browser — the React runtime and `support.js` are local, so they render
offline; fonts fall back to system sans without a network.

**Locked:** Splash · Title page · Discover · Home v2 (with the lens-icon change below).

**Decisions applied to design.md / DESIGN_BRIEF.md / UX.md:**
- The lens is a top-bar icon, not chips: full outlined circle = All, the same circle
  **half-shaded and blue** = filtered. No text label — the icon itself is the state.
- Home's stat chips are removed. Home = Continue + Latest updates.
- The NEW pill stays Accent.

**Parked:** Reader and Player mocks — not in the brief's eleven; the shipped reader/player stay.

**Round two delivered everything owed** — Sources, Settings, Onboarding, Search, Downloads — plus
extras nobody ordered but that answer real questions: `Lens carry-over` (the same lens state on two
screens, the half-shaded circle in action), `Paper variant` (light mode), `Tracking hub`, and RTL
states across seven screens rather than one.

Round two also answered the round-one question: Library's chips are **user categories** —
*Ongoing · Backlog · Finished* are three defaults seeded at first run, renameable in
Settings › Library › Categories; derived states (Unread · Downloaded · Started · Tracked) stay in
the filter sheet. And it returned an amended **design.md v1.1** — every value the pass had to
invent, folded back into the rules file with a changelog — which is now the repo's copy.

**The design phase is closed.** These files are the spec the Compose port reads.
