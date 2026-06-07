# Post 2: Planning before writing a single line of code

**Angle:** Docs-first as a discipline, not a formality.

---

Before I wrote any code for Brevia, I wrote the docs.

Not a README. Actual architecture decisions — a folder of short files called ADRs (Architecture Decision Records). Each one asks: what did I decide, what were the options, and why did I choose this?

It felt slow at first. But it forced me to notice how many things I hadn't actually thought through. Like: what database should I use and why? What happens when a link expires — do I delete it or keep it? What framework makes sense for this scale?

Writing it down before building means I'm making those choices consciously instead of by accident. And if I look back in three months and think "why did I do it this way," I have an answer.

This is apparently a real practice used in engineering teams. I found out about it, thought it was overkill for a solo project, and then tried it anyway. I was wrong — it's been the most useful thing I've done.

Thinking before building sounds obvious. Turns out it needs practice.

---

*Next: one of the smallest decisions with the most options — how to generate short codes.*
