# Registration page design QA

## Original brief

- Replace the existing invitation-heavy registration layout with a cleaner registration experience.
- Use the supplied Austerity WeChat QR image in the contact area.
- Remove the invitation code field, invitation instructions, and the sentence about following the public account.
- Keep registration as a standard username and password flow.

## Reference and implementation evidence

- Before reference: `C:\Users\l\AppData\Local\Temp\codex-clipboard-91420587-2f07-40e4-8936-168a437b7c57.png` at 764 × 1000.
- Supplied QR asset: `C:\Users\l\xwechat_files\wxid_9k943revooon22_84da\temp\RWTemp\2026-10\9e20f478899dc29eb19741386f9343c8\0bfe1b7725d55f34d58dc267f1e9fc45.jpg`.
- Implemented tablet capture: `C:\Users\l\.codex\visualizations\2026\09\08\01a080ad-b14e-7203-9540-e4faeab255cb\register-tablet.png` at 764 × 1000.
- Side-by-side comparison: `C:\Users\l\.codex\visualizations\2026\09\08\01a080ad-b14e-7203-9540-e4faeab255cb\register-comparison.png`.
- Desktop and mobile captures were also checked at 1440 × 1000 and 390 × 844. A separate crop was unnecessary because the QR card and every form control are readable in the full-size captures.

## Checkpoint comparison

| Checkpoint | Target | Result |
| --- | --- | --- |
| Content | No invitation workflow or public-account instruction | Passed: the page has username, password, and password confirmation only |
| QR asset | Use the exact supplied Austerity QR image | Passed: source and deployed files have the same SHA-256 hash |
| Layout | Contact area and form should feel balanced | Passed: 210 px contact card plus flexible form on desktop; contact card becomes horizontal on tablet and mobile |
| Hierarchy | Registration purpose and primary action remain clear | Passed: page title, section labels, and green primary button form a clear reading order |
| Responsiveness | No squeezed two-column tablet layout or horizontal overflow | Passed at 1440 px, 764 px, and 390 px |
| Interaction | Standard registration request and successful return to login | Passed: request body contained only `username` and `password`; success routed to password login |
| Runtime quality | No browser console or page errors in the verified light-theme flow | Passed |

## Intentional differences from the previous screen

- Removed the invite-code input, numbered invite steps, reply keyword, copy action, and invitation-only helper text at the user's request.
- Reframed the left area as a compact contact card instead of an invitation acquisition panel.
- Retained the existing KnowGround page identity, agreement links, and outer login shell.

## Issue matrix

| Severity | Count | Notes |
| --- | ---: | --- |
| P0 | 0 | No blocking rendering or registration-flow issue |
| P1 | 0 | No major structural or responsive mismatch |
| P2 | 0 | No visible content, spacing, or control issue remaining |
| P3 | 0 | No polish issue requiring another iteration |

Targeted ESLint checks passed for `register.vue` and `auth.ts`, and `mvn -q -DskipTests compile` passed after correcting the malformed application class name. The full frontend typecheck remains blocked by the existing `playwright-kb-column.spec.ts` dependency/type errors.

final result: passed
