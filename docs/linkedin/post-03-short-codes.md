# Post 3: The tiny decisions that matter — short codes

**Angle:** Something that looks simple has a lot of surface area.

---

Every URL shortener has the same core question: how do you turn a long URL into something like `brevia.sh/abc123`?

That `abc123` part is called a short code. Picking how to generate it took longer than I expected.

First choice: what characters to allow. I landed on letters only — no numbers. Why? Because `0` and `O` look the same. So do `1`, `l`, and `I`. If someone ever has to type a short link manually, ambiguous characters are a real problem. So I use 52 characters: a–z and A–Z.

Second choice: how long. Six characters gives you over 19 billion possible combinations. More than enough for a long time. Short enough to be short.

Third choice: the display format. The code is stored as 6 characters internally, but shown as `XXX-XXX` with a dash in the middle. The dash is purely cosmetic — it's stripped before lookup. It just makes it slightly easier to read and share out loud.

None of these decisions are glamorous. But each one has a reason, and getting them wrong early would mean fixing them later under pressure.

---

*Next: a decision that seems technical but is actually about trust — 302 vs 301 redirects.*
