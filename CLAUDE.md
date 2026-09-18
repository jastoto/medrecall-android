# CLAUDE.md — MedRecall+ Android

Project-wide instructions for Claude when working in this repository.

## Policy conflict alert (standing rule)

At any point while working in this project — no matter how small the change or fix — if something being done, proposed, or already in the code could possibly conflict with, contradict, or go against MedRecall+'s Privacy Policy or Terms of Service (as published at medrecallplus.com, and as drafted in the "MedRecall+ Android" Claude Project docs `claude/privacy-policy.md` and `claude/terms-of-service.md`), **stop and alert Asok before proceeding.**

This applies regardless of severity — a one-line fix, a minor UI tweak, or a large feature all qualify equally. Do not make the change first and mention the concern afterward; raise it and wait for a decision before writing any code or making the change.

Examples of what should trigger this: a feature that would collect, store, share, or transmit data in a way the Privacy Policy doesn't describe; a change to what a connector (Google Drive, OneDrive, Health Connect, Calendar, Medicare Blue Button, Epic/FHIR, etc.) accesses or does with data; wording or behavior that contradicts a stated scope limit (e.g. the open item on whether the Epic/hospital connector should be scoped to billing data only); anything affecting data retention, deletion, or account/sign-in behavior described in the ToS.

When in doubt about whether something qualifies, treat it as qualifying and ask.
