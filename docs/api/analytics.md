# API — Analytics

Endpoints for retrieving click analytics. Only authenticated link owners can access analytics data.

See [features/analytics.md](../features/analytics.md) for the full analytics specification including what data is collected and why 302 is used.

---

## Get Analytics for a Link

```
GET /api/v1/links/:code/analytics
```

Returns aggregated click analytics for a single link. Authentication as the link owner is required.

**Path parameter:** `:code` — 6-character code without dash

**Query parameters:**

| Parameter | Default | Description |
|---|---|---|
| `from` | 30 days ago | Start of the date range (ISO 8601 date or datetime) |
| `to` | now | End of the date range (ISO 8601 date or datetime) |
| `granularity` | `day` | Bucket size for the time series: `hour`, `day`, `week`, `month` |

**Response `200 OK`:**

```json
{
  "data": {
    "code": "aBcDeF",
    "total_clicks": 1247,
    "unique_visitors": 893,
    "time_series": [
      { "bucket": "2026-06-01", "clicks": 142 },
      { "bucket": "2026-06-02", "clicks": 98 },
      { "bucket": "2026-06-03", "clicks": 207 }
    ],
    "top_referrers": [
      { "referrer": "https://twitter.com", "clicks": 412 },
      { "referrer": null, "clicks": 381 },
      { "referrer": "https://news.ycombinator.com", "clicks": 201 }
    ],
    "countries": [
      { "country_code": "US", "clicks": 634 },
      { "country_code": "DE", "clicks": 187 },
      { "country_code": "GB", "clicks": 143 }
    ]
  }
}
```

`referrer: null` in `top_referrers` represents direct navigation or referrers that stripped the header.

`unique_visitors` is an approximation based on distinct `ip_hash` values in the requested date range.

**Errors:**
- `401 UNAUTHORIZED` — no valid token
- `403 FORBIDDEN` — authenticated but not the link owner
- `404 NOT_FOUND` — code does not exist
- `410 GONE` — link has expired (analytics are still accessible for expired links; this should not occur on this endpoint)
- `400 VALIDATION_ERROR` — invalid date range or granularity value

---

## Get Analytics Summary for All Owned Links

```
GET /api/v1/users/me/analytics
```

Returns a summary of analytics across all links owned by the authenticated user.

**Query parameters:**

| Parameter | Default | Description |
|---|---|---|
| `from` | 30 days ago | Start of the date range |
| `to` | now | End of the date range |

**Response `200 OK`:**

```json
{
  "data": {
    "total_clicks": 8431,
    "total_links": 47,
    "active_links": 39,
    "expired_links": 8,
    "top_links": [
      {
        "code": "aBcDeF",
        "display_code": "aBc-DeF",
        "short_url": "https://breviare.sh/aBc-DeF",
        "destination": "https://example.com",
        "clicks_in_range": 1247
      }
    ],
    "time_series": [
      { "bucket": "2026-06-01", "clicks": 892 },
      { "bucket": "2026-06-02", "clicks": 741 }
    ]
  }
}
```

`top_links` returns the top 5 links by click count within the requested date range.

**Errors:**
- `401 UNAUTHORIZED` — no valid token
