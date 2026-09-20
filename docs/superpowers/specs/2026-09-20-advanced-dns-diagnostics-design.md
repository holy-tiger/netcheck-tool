# Advanced DNS Diagnostics Design

## Goal

Run an evidence-based DNS decision tree for every Android diagnostic command
task whose `type` is `dns`. The client must distinguish local resolver faults,
suspected anomalous answers, DNSSEC breakage, delegation mismatches, and
unreachable authoritative servers while remaining compatible with existing
commands and reports.

The feature does not add DNS conclusions to the Android home or completion
screen. It uploads structured conclusions and detailed evidence to the FastAPI
backend, where support staff can review them.

## Scope

The feature covers:

- system DNS resolution on the Android device;
- comparison against public-recursive and authoritative evidence;
- Cloudflare validating and Checking Disabled (`CD`) queries;
- iterative authoritative-server discovery from the DNS root;
- parent-delegation and child-zone NS comparison;
- direct queries to every discovered authoritative server;
- TCP port 53 and ICMP reachability probes when authoritative queries time out;
- command generation, report parsing, and report presentation in the backend;
- deterministic Android and backend tests.

Other task types and the Android home and completion UI remain unchanged.

## Command Format

The complete JSON command continues to be Base64 encoded. Every existing DNS
task automatically uses the advanced diagnostic flow, so old commands remain
valid.

```json
{
  "report_url": "https://example.com/api/reports",
  "timeout_ms": 5000,
  "tasks": [
    {
      "type": "dns",
      "targets": ["www.example.com", "api.example.com"],
      "resolver": "9.9.9.9",
      "fallback_resolver": "149.112.112.112",
      "diagnostic_timeout_ms": 45000
    }
  ]
}
```

DNS task options are optional:

- `resolver` defaults to Cloudflare `1.1.1.1`;
- `fallback_resolver` defaults to Cloudflare `1.0.0.1`;
- `diagnostic_timeout_ms` defaults to 30,000 ms per target;
- top-level `timeout_ms` remains the per-operation timeout and continues to
  apply to other task types.

Resolver overrides must be literal IPv4 or IPv6 addresses. This prevents the
resolver setting itself from requiring a possibly compromised local DNS lookup.
The backend command API and generator page expose these options in a collapsed
advanced-DNS section.

## Architecture

The Android implementation uses the `dnsjava` DNS wire-format library and four focused
boundaries:

1. `DnsWireClient` performs UDP queries, retries truncated responses over TCP,
   selects record types, sets the `CD` flag, and returns normalized response
   metadata.
2. `DnsAuthorityDiscovery` starts with built-in root hints, follows referrals,
   consumes in-bailiwick glue records, and iteratively resolves out-of-bailiwick
   NS names. It does not use Android system DNS.
3. `DnsDiagnosticEngine` enforces the decision tree and per-target deadline. It
   depends on interfaces for system resolution, DNS wire queries, TCP probes,
   ping probes, and time so its branches can be tested without the network.
4. `NetworkEngine.executeDns` remains the public task entry point. It runs the
   system resolver stage, invokes the diagnostic engine, and maps the result to
   the existing task-report shape plus optional structured DNS fields.

`MainViewModel` retains task-scoped DNS options when expanding command targets,
passes them into `executeDns`, and serializes the returned structured fields.
All network work remains on `Dispatchers.IO`.

Cloudflare `1.1.1.1` and `1.0.0.1` are public recursive Anycast services. They
are not assumed to be the target domain's authoritative servers. The actual
authoritative NS names and addresses are discovered from DNS delegation. Every
discovered NS address is queried subject to the target-wide deadline.

## Target Normalization

The client trims a target, extracts the host from an HTTP or HTTPS URL when one
is supplied, converts internationalized labels to ASCII, lowercases names, and
removes a trailing root dot for comparisons. It rejects malformed hostnames
with a recorded, non-crashing result. A literal IP target is reported as not
requiring DNS resolution rather than entering authority discovery.

DNS names used in logs and reports retain a safe normalized display form. NS
set comparison is case-insensitive and ignores the final root dot.

## Diagnostic Flow

### System resolution succeeds

The client records all system A and AAAA answers and follows CNAME evidence from
wire queries. It obtains evidence sets from both configured public-recursive
endpoints and from every responsive authoritative server.

CDNs can legitimately return different addresses based on resolver location,
EDNS Client Subnet, time, load, and Anycast routing. Therefore exact set
equality is not required. A system answer is normal when it appears in any
collected authoritative or public-recursive evidence set. The client performs
two query rounds per authoritative server address, concurrently with a maximum
of eight in-flight requests, and merges the results.

If system answers include addresses absent from all sufficient reference
evidence, the result is `SUSPECTED_DNS_ANOMALY`, not a definitive hijacking
claim. Sufficient comparison evidence requires at least one successful public
recursive response and one successful direct authoritative response. Reserved
addresses, unexpected private addresses, NXDOMAIN rewriting, and incompatible
CNAME evidence are logged as stronger anomaly indicators. If reference
evidence is insufficient, the result is `INCOMPLETE` rather than an anomaly.

### System resolution fails

The client first sends an ordinary validating query to the configured public
resolver, then sends the equivalent query with `CD` set when needed.

- If the ordinary public query succeeds, the result is
  `LOCAL_RECURSOR_FAILURE`.
- If the ordinary query returns `SERVFAIL`, the `CD` query returns usable data,
  and DNSSEC records or status provide evidence of a signed, bogus chain, the
  result is `DNSSEC_BROKEN`.
- If ordinary and `CD` queries both fail to return usable data, the client
  discovers and directly queries the actual authoritative servers.

`CD` success alone is never sufficient to diagnose DNSSEC breakage because an
unsigned healthy domain can also answer a `CD` query.

### Direct authority and delegation checks

The client discovers the closest enclosing zone cut for a hostname and queries
every address of every delegated authoritative NS. Discovery evidence is kept
separate from direct-answer evidence.

When an authoritative server answers, the client compares the parent-zone NS
delegation with the child apex's self-published NS set:

- unequal normalized sets produce `REGISTRAR_NS_MISMATCH`;
- equal sets, combined with failed recursive resolution, produce
  `LOCAL_RECURSOR_FAILURE` with delegation and authority evidence.

When authoritative DNS requests time out, the client probes TCP port 53 and
ICMP reachability for every discovered NS address:

- no reachable NS produces `AUTHORITY_UNREACHABLE` and is described as a
  suspected block or black hole;
- partial reachability or reachable TCP/ICMP without DNS service produces
  `AUTHORITY_SERVICE_FAILURE`.

An authoritative `NXDOMAIN` is a valid DNS answer and produces
`DOMAIN_NOT_FOUND`. `REFUSED`, `SERVFAIL`, malformed replies, transport errors,
and timeouts remain distinct evidence events.

## Deadlines and Partial Evidence

The top-level `timeout_ms` is clamped to 1,000-20,000 ms and limits one network
operation. `diagnostic_timeout_ms` is clamped to 5,000-120,000 ms and limits the
complete flow for one DNS target. All loops check the remaining target deadline
before starting another query or probe.

When the target deadline expires, the engine preserves collected evidence and
returns `INCOMPLETE`. Cancellation propagates normally and sockets and processes
are closed. One failed or malformed DNS target does not abort later command
tasks.

## Result Contract

Existing fields remain present. DNS reports add optional structured fields:

```json
{
  "task": "dns|www.example.com",
  "status": "failed",
  "diagnosis_code": "DNSSEC_BROKEN",
  "diagnosis_summary": "DNSSEC configuration is broken",
  "raw_log": "...complete evidence...",
  "duration_ms": 1834
}
```

Supported diagnosis codes are:

- `NORMAL`
- `DOMAIN_NOT_FOUND`
- `SUSPECTED_DNS_ANOMALY`
- `DNSSEC_BROKEN`
- `LOCAL_RECURSOR_FAILURE`
- `REGISTRAR_NS_MISMATCH`
- `AUTHORITY_SERVICE_FAILURE`
- `AUTHORITY_UNREACHABLE`
- `INCOMPLETE`

`status` is `success` only for `NORMAL`; diagnostic failures, nonexistent
domains, and incomplete results use `failed`. `raw_log` records stage names,
server addresses, transports, record types, RCODEs, relevant flags, CNAMEs,
addresses, NS delegation differences, latencies, port checks, and ping results.

## Backend Behavior

The FastAPI report request model accepts a typed task-result object with the new DNS
fields optional and permits old task reports that contain only `task`, `status`,
and `raw_log`. Task result JSON remains stored in the current SQLite JSON text
column, so no database migration is required.

The report list continues to derive success totals from `status`. The detail
page adds a localized diagnosis badge, summary, and duration for DNS tasks and
keeps the raw evidence log below them. Unknown future diagnosis codes fall back
to their literal value. All report-derived text is HTML-escaped before display,
including task names, summaries, and raw logs.

The admin generation API validates resolver IP literals and safe timeout
ranges. It emits DNS options only on DNS tasks. The generator page supplies the
same optional values and shows the resulting JSON and Base64 as it does today.
The repository's Node development entry point in `server.js` mirrors report and
command-generation behavior, so `npm run dev` produces and preserves the same
new fields as FastAPI.

## Testing

Android unit tests use fake system resolvers, DNS transports, probes, and clocks
to cover every terminal diagnosis without accessing the public network. Tests
cover:

- old-command defaults and per-task overrides;
- normal local and authoritative agreement;
- CDN address rotation and CNAME chains without false definitive claims;
- anomalous, private, reserved, and NXDOMAIN-rewrite evidence;
- ordinary public success after local failure;
- `SERVFAIL` plus successful `CD` and DNSSEC evidence;
- the guard against classifying unsigned `CD` success as DNSSEC breakage;
- parent and child NS equality and mismatch normalization;
- all-authority iteration, partial reachability, and total unreachability;
- UDP truncation followed by TCP;
- malformed targets, operation timeouts, and the target-wide deadline.

Backend tests cover the FastAPI and Node entry points: default and overridden
command generation, invalid resolver and timeout rejection, old and new report
payload parsing, and preservation of structured fields on report retrieval.
Dashboard rendering helpers are tested for diagnosis labels, unknown-code
fallback, and HTML escaping.

Before completion, the focused tests run first, followed by the full Android
JVM suite and backend test suite. A debug APK build verifies packaging and
dependency integration.

## Compatibility and Security

Old Base64 commands trigger the new flow with defaults. Old reports render as
they do now, without a diagnosis badge. The API endpoint paths and SQLite schema
do not change.

Inputs are bounded by DNS hostname limits, a maximum of 50 targets per task,
timeout clamps, DNS message-size limits, a maximum referral depth of 32, and
visited-zone/server loop detection. Ping probes receive validated literal NS
addresses through an argument-safe process API rather than shell concatenation.
No credentials, device identifiers, or packet contents beyond necessary DNS
evidence are added to logs.
