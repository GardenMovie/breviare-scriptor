# Post 4: Why I chose 302 over 301 redirects

**Angle:** A trade-off with a real consequence, explained plainly.

---

When you click a short link, your browser gets sent to the real URL. That "sending" is a redirect. There are two main types: 301 and 302.

The difference sounds dry, but the consequences aren't.

A **301** means "this page has moved permanently." Browsers remember it. After the first click, your browser skips the short link server entirely and goes straight to the destination — forever, until the user clears their cache.

A **302** means "this is a temporary redirect." The browser checks the short link server every single time.

For Breviare, I always use 302.

The reason: analytics. If I use 301, I lose click data after the first visit per browser. I can't tell how many people clicked, where they came from, or when. The whole point of a URL shortener is partially to track that. 301 silently breaks it.

The cost of 302 is a tiny bit of extra server load — your browser makes one more request per click. That's a real trade-off. I decided the analytics were worth it.

This kind of decision — where both options are valid, but they serve different goals — is most of software engineering.

---

*Next: what happens when a link gets old? How I designed link expiry.*
