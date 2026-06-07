# Post 5: Link expiry — how do you decide when something dies?

**Angle:** Designing for edge cases teaches you what your system is actually for.

---

Should short links expire? If yes, when?

My first instinct was: never expire them. Simple. But then I thought about what "never" actually means — a database slowly filling with links no one has clicked in five years, pointing to pages that no longer exist.

So I designed expiry. Here's how it works in Brevia:

By default, a link expires after **30 days of no clicks**. Not 30 days from creation — 30 days of inactivity. If someone uses it regularly, it stays alive. That felt more honest to how links actually get used.

If you have an account, you can also set a hard expiry date — a specific day the link dies no matter what. Useful for a sale, an event, a campaign.

The other decision: what happens when someone clicks an expired link. I return a **410 Gone** response instead of a 404. The difference matters — 404 means "I don't know what you're talking about," 410 means "this existed and it's gone now." More accurate, and better for anyone debugging the problem.

Designing for the end of something's life taught me a lot about what the thing is actually for.

---

*Last post: what I learned from planning all this before writing any code.*
