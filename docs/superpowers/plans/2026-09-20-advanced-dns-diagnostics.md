# Advanced DNS Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute an evidence-based DNS diagnostic tree for every Android `dns` target and expose its structured conclusion through both backend entry points and the support dashboard.

**Architecture:** Add a testable Android DNS domain layer around `dnsjava`, separating wire queries, iterative authority discovery, reachability probes, and classification. Preserve the existing command/report envelope while adding task-scoped DNS options and optional result fields. Keep FastAPI, `server.js`, and the dashboard contract-compatible without changing SQLite schema.

**Tech Stack:** Kotlin, coroutines, dnsjava, JUnit 4, Robolectric, FastAPI, Pydantic 2, SQLite, Python unittest/TestClient, Node built-in test runner, vanilla JavaScript.

---

## File Map

**Android files to create**

- `app/src/main/java/com/example/core/dns/DnsModels.kt` — options, normalized evidence, diagnosis codes, and dependency interfaces.
- `app/src/main/java/com/example/core/dns/DnsTargetNormalizer.kt` — URL/IDN/domain/IP normalization.
- `app/src/main/java/com/example/core/dns/DnsJavaWireClient.kt` — UDP/TCP DNS queries using dnsjava.
- `app/src/main/java/com/example/core/dns/DnsAuthorityDiscovery.kt` — root-to-zone iterative referral traversal.
- `app/src/main/java/com/example/core/dns/DnsReachabilityProber.kt` — safe TCP/53 and ICMP probes.
- `app/src/main/java/com/example/core/dns/DnsDiagnosticEngine.kt` — deadline-aware decision tree.
- `app/src/main/java/com/example/core/DiagnosticCommandParser.kt` — command/task options independent of ViewModel state.
- `app/src/test/java/com/example/core/dns/DnsTargetNormalizerTest.kt`
- `app/src/test/java/com/example/core/dns/DnsJavaWireClientTest.kt`
- `app/src/test/java/com/example/core/dns/DnsAuthorityDiscoveryTest.kt`
- `app/src/test/java/com/example/core/dns/DnsDiagnosticEngineTest.kt`
- `app/src/test/java/com/example/core/DiagnosticCommandParserTest.kt`

**Android files to modify**

- `gradle/libs.versions.toml` and `app/build.gradle.kts` — dnsjava dependency.
- `app/src/main/java/com/example/core/NetworkEngine.kt` — real DNS dependencies and structured task result.
- `app/src/main/java/com/example/ui/MainViewModel.kt` — parsed DNS options and report serialization.
- `app/src/test/java/com/example/ExampleRobolectricTest.kt` — report JSON integration coverage.

**Backend files to create**

- `backend/requirements-dev.txt` — backend test-only dependencies.
- `backend/tests/test_dns_contract.py` — FastAPI generation and report compatibility tests.
- `backend/node_contract.js` — validation and command construction shared by `server.js`.
- `backend/tests/node_contract.test.js` — Node contract tests.
- `backend/templates/dashboard-utils.js` — browser-safe diagnosis labels and HTML escaping.
- `backend/tests/dashboard-utils.test.js` — dashboard utility tests.

**Backend files to modify**

- `backend/models.py` — typed optional DNS fields and validated generator options.
- `backend/api/admin.py` — emit task-scoped DNS configuration.
- `backend/api/report.py` — preserve typed/extra result fields.
- `backend/main.py` — serve the dashboard utility script.
- `backend/templates/index.html` — advanced DNS controls and diagnosis presentation.
- `server.js` — use the shared Node contract and serve the utility script.
- `package.json` — Node test command.

### Task 1: Add DNS Contracts and Target Normalization

**Files:**

- Create: `app/src/main/java/com/example/core/dns/DnsModels.kt`
- Create: `app/src/main/java/com/example/core/dns/DnsTargetNormalizer.kt`
- Create: `app/src/test/java/com/example/core/dns/DnsTargetNormalizerTest.kt`

- [ ] **Step 1: Write the failing normalization and defaults tests**

```kotlin
package com.example.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTargetNormalizerTest {
    @Test fun `defaults use Cloudflare and clamp timeouts`() {
        val options = DnsOptions(operationTimeoutMs = 100, diagnosticTimeoutMs = 999_999)
        assertEquals("1.1.1.1", options.resolver)
        assertEquals("1.0.0.1", options.fallbackResolver)
        assertEquals(1_000, options.clampedOperationTimeoutMs)
        assertEquals(120_000, options.clampedDiagnosticTimeoutMs)
    }

    @Test fun `normalizes URL IDN case and trailing dot`() {
        assertEquals(
            NormalizedDnsTarget.Host("xn--bcher-kva.example"),
            DnsTargetNormalizer.normalize("https://BÜCHER.example./path")
        )
    }

    @Test fun `recognizes literal address and rejects malformed host`() {
        assertEquals(
            NormalizedDnsTarget.Address("2001:db8::1"),
            DnsTargetNormalizer.normalize("2001:db8::1")
        )
        assertTrue(DnsTargetNormalizer.normalize("bad host!") is NormalizedDnsTarget.Invalid)
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsTargetNormalizerTest`

Expected: compilation fails because the DNS contracts and normalizer do not exist.

- [ ] **Step 3: Implement the domain contracts and normalizer**

Define these exact public contracts in `DnsModels.kt`:

```kotlin
package com.example.core.dns

data class DnsOptions(
    val resolver: String = "1.1.1.1",
    val fallbackResolver: String = "1.0.0.1",
    val operationTimeoutMs: Long = 20_000,
    val diagnosticTimeoutMs: Long = 30_000
) {
    val clampedOperationTimeoutMs = operationTimeoutMs.coerceIn(1_000, 20_000).toInt()
    val clampedDiagnosticTimeoutMs = diagnosticTimeoutMs.coerceIn(5_000, 120_000)
}

enum class DiagnosisCode {
    NORMAL, DOMAIN_NOT_FOUND, SUSPECTED_DNS_ANOMALY, DNSSEC_BROKEN,
    LOCAL_RECURSOR_FAILURE, REGISTRAR_NS_MISMATCH, AUTHORITY_SERVICE_FAILURE,
    AUTHORITY_UNREACHABLE, INCOMPLETE
}

enum class DnsRecordType { A, AAAA, CNAME, NS, SOA, DS, DNSKEY, RRSIG }
enum class DnsResponseCode { NOERROR, NXDOMAIN, SERVFAIL, REFUSED, TIMEOUT, MALFORMED, NETWORK_ERROR }

data class DnsRecordValue(val owner: String, val type: DnsRecordType, val value: String, val ttl: Long)
data class DnsEvidence(
    val server: String,
    val recordType: DnsRecordType,
    val responseCode: DnsResponseCode,
    val records: List<DnsRecordValue> = emptyList(),
    val authoritative: Boolean = false,
    val authenticatedData: Boolean = false,
    val checkingDisabled: Boolean = false,
    val truncated: Boolean = false,
    val transport: String = "UDP",
    val durationMs: Long = 0,
    val detail: String = ""
) {
    val hasUsableData get() = responseCode == DnsResponseCode.NOERROR && records.isNotEmpty()
}

data class AuthorityDiscoveryResult(
    val zone: String,
    val parentNs: Set<String>,
    val childNs: Set<String>,
    val serverAddresses: Map<String, Set<String>>,
    val evidence: List<DnsEvidence>
)

data class ReachabilityResult(val address: String, val tcp53: Boolean, val ping: Boolean, val detail: String)
data class DnsDiagnosticResult(
    val target: String,
    val code: DiagnosisCode,
    val summary: String,
    val evidence: List<DnsEvidence>,
    val reachability: List<ReachabilityResult> = emptyList(),
    val durationMs: Long
) { val status get() = if (code == DiagnosisCode.NORMAL) "success" else "failed" }

sealed interface NormalizedDnsTarget {
    data class Host(val ascii: String) : NormalizedDnsTarget
    data class Address(val literal: String) : NormalizedDnsTarget
    data class Invalid(val reason: String) : NormalizedDnsTarget
}

fun interface SystemDnsResolver { suspend fun resolve(host: String): Result<Set<String>> }
interface DnsQueryClient {
    suspend fun query(name: String, type: DnsRecordType, server: String, checkingDisabled: Boolean, timeoutMs: Int, recursionDesired: Boolean = true): DnsEvidence
}
interface AuthorityDiscovery { suspend fun discover(host: String, timeoutMs: Int): Result<AuthorityDiscoveryResult> }
interface ReachabilityProber { suspend fun probe(address: String, timeoutMs: Int): ReachabilityResult }
fun interface DnsDiagnosticRunner { suspend fun diagnose(rawTarget: String, options: DnsOptions): DnsDiagnosticResult }
```

Implement `DnsTargetNormalizer.normalize` with `URI` host extraction for HTTP(S), `IDN.toASCII(..., IDN.USE_STD3_ASCII_RULES)`, literal-address detection via bracket removal plus `InetAddress`, DNS label length checks, and rejection of whitespace or illegal labels. Do not perform network lookups while recognizing literals.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsTargetNormalizerTest`

Expected: all three tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/core/dns app/src/test/java/com/example/core/dns/DnsTargetNormalizerTest.kt
git commit -m "feat: define DNS diagnostic contracts"
```

### Task 2: Parse Backward-Compatible DNS Task Options

**Files:**

- Create: `app/src/main/java/com/example/core/DiagnosticCommandParser.kt`
- Create: `app/src/test/java/com/example/core/DiagnosticCommandParserTest.kt`
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`

- [ ] **Step 1: Write failing parser tests**

```kotlin
package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticCommandParserTest {
    @Test fun `old DNS task receives defaults`() {
        val command = DiagnosticCommandParser.parse("""{"timeout_ms":5000,"tasks":[{"type":"dns","targets":["example.com"]}]}""")
        val task = command.tasks.single()
        assertEquals("1.1.1.1", task.dnsOptions!!.resolver)
        assertEquals(30_000, task.dnsOptions.diagnosticTimeoutMs)
        assertEquals(5_000, task.dnsOptions.operationTimeoutMs)
    }

    @Test fun `overrides remain scoped to their DNS task`() {
        val command = DiagnosticCommandParser.parse("""{"timeout_ms":5000,"tasks":[{"type":"dns","targets":["a.example"],"resolver":"9.9.9.9","fallback_resolver":"149.112.112.112","diagnostic_timeout_ms":45000},{"type":"ping","targets":["1.1.1.1"]}]}""")
        assertEquals("9.9.9.9", command.tasks[0].dnsOptions!!.resolver)
        assertEquals(null, command.tasks[1].dnsOptions)
    }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.example.core.DiagnosticCommandParserTest`

Expected: compilation fails because `DiagnosticCommandParser` is absent.

- [ ] **Step 3: Implement parser and task models**

Create `ParsedDiagnosticCommand(reportUrl, timeoutMs, tasks)` and `ParsedTask(type, target, dnsOptions)` data classes. `parse` must cap each source task at 50 nonblank targets, lowercase the type, create `DnsOptions` only for `dns`, and validate resolver strings as literal addresses before constructing tasks. Invalid resolver configuration must throw `IllegalArgumentException` with the field name.

Replace the local `TaskItem` construction in `MainViewModel.startDiagnosis` with the parser result, but keep execution behavior unchanged until Task 6.

- [ ] **Step 4: Run and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.example.core.DiagnosticCommandParserTest`

Expected: both parser tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/core/DiagnosticCommandParser.kt app/src/main/java/com/example/ui/MainViewModel.kt app/src/test/java/com/example/core/DiagnosticCommandParserTest.kt
git commit -m "feat: parse DNS diagnostic options"
```

### Task 3: Implement DNS Wire Queries

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/core/dns/DnsJavaWireClient.kt`
- Create: `app/src/test/java/com/example/core/dns/DnsJavaWireClientTest.kt`

- [ ] **Step 1: Add dnsjava to the version catalog and app**

Add `dnsjava = "3.6.3"`, add `dnsjava = { group = "dnsjava", name = "dnsjava", version.ref = "dnsjava" }`, and add `implementation(libs.dnsjava)`.

- [ ] **Step 2: Write failing wire-client tests using a loopback UDP/TCP fixture**

The test fixture must bind an ephemeral loopback port, decode the dnsjava request, and return a matching response ID. Cover a normal A response, preservation of the request `CD` flag, and a UDP response with `TC` followed by a full TCP response.

```kotlin
@Test fun `retries truncated UDP response over TCP`() = runTest {
    FakeDnsServer(truncateUdp = true, answer = "203.0.113.7").use { server ->
        val result = DnsJavaWireClient(server.port).query("example.com", DnsRecordType.A, "127.0.0.1", false, 2_000)
        assertEquals(DnsResponseCode.NOERROR, result.responseCode)
        assertEquals("TCP", result.transport)
        assertEquals(setOf("203.0.113.7"), result.records.map { it.value }.toSet())
    }
}
```

- [ ] **Step 3: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsJavaWireClientTest`

Expected: compilation fails because `DnsJavaWireClient` is absent.

- [ ] **Step 4: Implement the wire client**

Use dnsjava `Message.newQuery(Record.newRecord(Name.fromString("$name."), typeCode, DClass.IN))`, set `Flags.CD` when requested, and configure `SimpleResolver(server)` with the injected port, timeout, TCP disabled initially, and recursion desired only for public-recursive calls. Authority discovery passes `recursionDesired = false`. If the UDP result has `TC`, repeat with TCP. Map header RCODE, AA/AD/CD/TC flags and A, AAAA, CNAME, NS, SOA, DS, DNSKEY, and RRSIG records into `DnsEvidence`. Convert timeout, malformed packet, and I/O exceptions into evidence rather than throwing.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsJavaWireClientTest`

Expected: A, CD, and UDP-to-TCP tests pass.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/core/dns/DnsModels.kt app/src/main/java/com/example/core/dns/DnsJavaWireClient.kt app/src/test/java/com/example/core/dns/DnsJavaWireClientTest.kt
git commit -m "feat: add DNS wire query client"
```

### Task 4: Discover Delegated Authority Iteratively

**Files:**

- Create: `app/src/main/java/com/example/core/dns/DnsAuthorityDiscovery.kt`
- Create: `app/src/test/java/com/example/core/dns/DnsAuthorityDiscoveryTest.kt`

- [ ] **Step 1: Write failing referral tests**

Use a scripted fake `DnsQueryClient` keyed by `(name, type, server)`. Test root referral to `.com`, `.com` referral to `example.com`, out-of-bailiwick resolution for `alice.ns.cloudflare.com`, child-apex NS lookup, normalized parent/child sets, visited-server loop rejection, and depth 32 rejection.

```kotlin
@Test fun `discovers all parent and child nameservers without system DNS`() = runTest {
    val result = DnsAuthorityDiscovery(scriptedClient).discover("www.example.com", 5_000).getOrThrow()
    assertEquals("example.com", result.zone)
    assertEquals(setOf("alice.ns.cloudflare.com", "bob.ns.cloudflare.com"), result.parentNs)
    assertEquals(result.parentNs, result.childNs)
    assertEquals(setOf("173.245.58.1", "2400:cb00:2049:1::1"), result.serverAddresses["alice.ns.cloudflare.com"])
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsAuthorityDiscoveryTest`

Expected: compilation fails because the discovery class is absent.

- [ ] **Step 3: Implement iterative discovery**

Embed the published root-server IPv4 and IPv6 hints as immutable literals. Query with `RD=false`, follow NS referrals, use in-bailiwick A/AAAA glue, and iteratively resolve missing out-of-bailiwick NS addresses from the roots. Retain the last referral owner as the closest zone cut, then query that apex directly for the child NS set. Query every discovered NS address with at most eight concurrent requests. Track visited `(name,type,server)` keys and reject referral depth above 32. Return all evidence even when discovery fails.

- [ ] **Step 4: Run and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsAuthorityDiscoveryTest`

Expected: referral, glue, out-of-bailiwick, loop, and depth tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/core/dns/DnsAuthorityDiscovery.kt app/src/test/java/com/example/core/dns/DnsAuthorityDiscoveryTest.kt
git commit -m "feat: discover authoritative DNS servers"
```

### Task 5: Implement the Diagnostic Decision Tree

**Files:**

- Create: `app/src/main/java/com/example/core/dns/DnsDiagnosticEngine.kt`
- Create: `app/src/test/java/com/example/core/dns/DnsDiagnosticEngineTest.kt`

- [ ] **Step 1: Write one failing test for each terminal diagnosis**

Build fakes for `SystemDnsResolver`, `DnsQueryClient`, `AuthorityDiscovery`, `ReachabilityProber`, and a monotonic clock. Provide tests named:

```text
system answer in recursive and authority evidence is NORMAL
CDN rotation union prevents a false anomaly
sufficient conflicting evidence is SUSPECTED_DNS_ANOMALY
local failure and public success is LOCAL_RECURSOR_FAILURE
SERVFAIL plus CD data plus DNSSEC evidence is DNSSEC_BROKEN
unsigned CD success is not DNSSEC_BROKEN
authoritative NXDOMAIN is DOMAIN_NOT_FOUND
different parent and child NS sets is REGISTRAR_NS_MISMATCH
matching delegation after recursive failure is LOCAL_RECURSOR_FAILURE
all unreachable authority addresses is AUTHORITY_UNREACHABLE
partially reachable authority is AUTHORITY_SERVICE_FAILURE
deadline preserves evidence and returns INCOMPLETE
```

The DNSSEC guard must assert that CD data without DS, DNSKEY, RRSIG, or AD/bogus-chain evidence falls through to authority diagnosis.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsDiagnosticEngineTest`

Expected: compilation fails because `DnsDiagnosticEngine` is absent.

- [ ] **Step 3: Implement minimal branch logic**

Make `DnsDiagnosticEngine` implement `DnsDiagnosticRunner`. Implement `suspend fun diagnose(rawTarget: String, options: DnsOptions): DnsDiagnosticResult` with `withTimeoutOrNull(options.clampedDiagnosticTimeoutMs)`. Query A and AAAA for both public endpoints; when local resolution succeeds, discover authority, query every authority address twice with at most eight concurrent calls, and compare the union. Require one successful recursive response and one successful authoritative response before returning `SUSPECTED_DNS_ANOMALY`. On local failure, apply the normal-query, `SERVFAIL`/CD/DNSSEC, direct-authority, delegation, and reachability branches in the specification. Preserve evidence in an accumulator outside the timeout block and format summaries from a fixed map keyed by `DiagnosisCode`.

- [ ] **Step 4: Run and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests com.example.core.dns.DnsDiagnosticEngineTest`

Expected: all terminal branch tests pass.

- [ ] **Step 5: Refactor repeated A/AAAA and bounded-concurrency code**

Extract private `queryAddressRecords`, `queryAuthorities`, `hasDnssecEvidence`, and `normalizedNsSet` functions. Rerun the focused test after refactoring.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/core/dns/DnsDiagnosticEngine.kt app/src/test/java/com/example/core/dns/DnsDiagnosticEngineTest.kt
git commit -m "feat: classify advanced DNS failures"
```

### Task 6: Connect Real Probes, NetworkEngine, and Reports

**Files:**

- Create: `app/src/main/java/com/example/core/dns/DnsReachabilityProber.kt`
- Modify: `app/src/main/java/com/example/core/NetworkEngine.kt`
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Modify: `app/src/test/java/com/example/ExampleRobolectricTest.kt`

- [ ] **Step 1: Write failing integration assertions**

Add a test seam `NetworkEngine.dnsEngineFactory` with internal visibility and restore it after each test. In Robolectric, provide a fake engine result and assert the generated result object contains:

```json
{"task":"dns|example.com","status":"failed","diagnosis_code":"DNSSEC_BROKEN","diagnosis_summary":"DNSSEC configuration is broken","duration_ms":123}
```

Also add a JVM test that passes `127.0.0.1` to the real prober with a loopback TCP server and asserts `tcp53` reflects the connection result without shell parsing.

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.example.ExampleRobolectricTest`

Expected: structured DNS fields are absent.

- [ ] **Step 3: Implement reachability and engine wiring**

`DnsReachabilityProber` uses `Socket.connect(InetSocketAddress(literal, 53), timeout)` and `ProcessBuilder("ping", "-c", "1", "-W", seconds, literal)`; it accepts only previously validated literal addresses and always destroys the process. Add nullable `diagnosisCode` and `diagnosisSummary` fields to `TaskExecutionResult` after `durationMs` with defaults of `null` so other callers remain source-compatible.

Build the real engine from `InetAddress.getAllByName`, `DnsJavaWireClient`, `DnsAuthorityDiscovery`, and `DnsReachabilityProber`. Replace the old body of `executeDns` with the engine call and evidence log formatter. The log formatter emits stage, server, transport, RCODE, flags, records, delegation differences, reachability, conclusion, and elapsed time.

Update `MainViewModel` to pass each parsed task's `DnsOptions` and serialize `duration_ms`, `diagnosis_code`, and `diagnosis_summary` only when non-null.

- [ ] **Step 4: Run focused and full Android tests**

Run: `./gradlew testDebugUnitTest --tests com.example.ExampleRobolectricTest`

Expected: report serialization tests pass.

Run: `./gradlew test`

Expected: all Android JVM, Robolectric, and screenshot tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/core app/src/main/java/com/example/ui/MainViewModel.kt app/src/test/java/com/example/ExampleRobolectricTest.kt
git commit -m "feat: integrate advanced DNS diagnostics"
```

### Task 7: Update FastAPI Command and Report Contracts

**Files:**

- Create: `backend/requirements-dev.txt`
- Create: `backend/tests/test_dns_contract.py`
- Modify: `backend/models.py`
- Modify: `backend/api/admin.py`
- Modify: `backend/api/report.py`

- [ ] **Step 1: Add backend test dependencies**

```text
-r requirements.txt
pytest>=8.0.0
httpx>=0.27.0
```

- [ ] **Step 2: Write failing FastAPI tests**

Use `TestClient` as a context manager, set `backend.database.DB_PATH` to a temporary file before entering it, and test:

```python
def test_generate_dns_defaults(client):
    response = client.post("/api/admin/generate", json={"targets": ["example.com"], "dns_enabled": True, "ping_enabled": False, "http_enabled": False, "tcp_enabled": False})
    task = response.json()["command_json"]["tasks"][0]
    assert task == {"type": "dns", "targets": ["example.com"]}

def test_generate_dns_overrides(client):
    response = client.post("/api/admin/generate", json={"targets": ["example.com"], "dns_enabled": True, "ping_enabled": False, "http_enabled": False, "tcp_enabled": False, "resolver": "9.9.9.9", "fallback_resolver": "149.112.112.112", "diagnostic_timeout_ms": 45000})
    dns_task = next(task for task in response.json()["command_json"]["tasks"] if task["type"] == "dns")
    assert dns_task["resolver"] == "9.9.9.9"

def test_report_round_trips_structured_dns_fields(client):
    payload = {"tracking_id": "ABC234", "device_info": {}, "network_env": {}, "results": [{"task": "dns|example.com", "status": "failed", "raw_log": "evidence", "diagnosis_code": "DNSSEC_BROKEN", "diagnosis_summary": "broken", "duration_ms": 123}]}
    assert client.post("/api/reports", json=payload).status_code == 200
    task = client.get("/api/reports/ABC234").json()["task_results"][0]
    assert task["diagnosis_code"] == "DNSSEC_BROKEN"
    assert task["duration_ms"] == 123
```

Add cases for invalid resolver names, timeout bounds, more than 50 targets, legacy three-field reports, and preservation of unknown extra task fields.

- [ ] **Step 3: Run and verify RED**

Run: `python -m pytest backend/tests/test_dns_contract.py -q`

Expected: override and validation tests fail because the fields are absent.

- [ ] **Step 4: Implement Pydantic and route changes**

Add `TaskResultSchema` with required `task`, `status`, and `raw_log`, optional DNS fields, and `ConfigDict(extra="allow")`. Change `ReportPayloadSchema.results` to `list[TaskResultSchema]` and dump each model with `exclude_none=True` before JSON storage. Add optional `resolver`, `fallback_resolver`, and `diagnostic_timeout_ms` fields to `GenerateCommandRequest`; validate IP literals with `ipaddress.ip_address`, timeout ranges with `Field(ge=..., le=...)`, and target count with `Field(max_length=50)`. In `generate_command`, attach non-default DNS options only to the DNS task so legacy default JSON remains unchanged.

- [ ] **Step 5: Run and verify GREEN**

Run: `python -m pytest backend/tests/test_dns_contract.py -q`

Expected: all FastAPI contract tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/requirements-dev.txt backend/tests/test_dns_contract.py backend/models.py backend/api/admin.py backend/api/report.py
git commit -m "feat: extend backend DNS contracts"
```

### Task 8: Keep the Node Development Backend Compatible

**Files:**

- Create: `backend/node_contract.js`
- Create: `backend/tests/node_contract.test.js`
- Modify: `server.js`
- Modify: `package.json`

- [ ] **Step 1: Write failing Node contract tests**

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');
const { buildTasks, normalizeTaskResults } = require('../node_contract');

test('adds DNS overrides only to DNS tasks', () => {
  const tasks = buildTasks({ targets: ['example.com'], dns_enabled: true, ping_enabled: true, resolver: '9.9.9.9', fallback_resolver: '149.112.112.112', diagnostic_timeout_ms: 45000 });
  assert.equal(tasks.find(t => t.type === 'dns').resolver, '9.9.9.9');
  assert.equal('resolver' in tasks.find(t => t.type === 'ping'), false);
});

test('preserves structured and future report fields', () => {
  const results = normalizeTaskResults([{ task: 'dns|example.com', status: 'failed', raw_log: 'x', diagnosis_code: 'DNSSEC_BROKEN', future_field: 7 }]);
  assert.equal(results[0].future_field, 7);
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test backend/tests/node_contract.test.js`

Expected: module-not-found failure for `node_contract.js`.

- [ ] **Step 3: Implement and use the shared Node contract**

Export `validateIpLiteral`, `validateTimeouts`, `buildTasks`, and `normalizeTaskResults`. Use `node:net.isIP`, enforce the same timeout and 50-target limits, omit default DNS options, and preserve report result objects. Replace duplicate task creation and report normalization in `server.js` with these functions. Return HTTP 400 with a specific `detail` for invalid settings.

- [ ] **Step 4: Add and run the Node test script**

Set `"test": "node --test backend/tests/*.test.js"` in `package.json`.

Run: `npm test`

Expected: Node contract tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/node_contract.js backend/tests/node_contract.test.js server.js package.json
git commit -m "feat: align Node DNS command contracts"
```

### Task 9: Add Dashboard Controls and Safe Diagnosis Rendering

**Files:**

- Create: `backend/templates/dashboard-utils.js`
- Create: `backend/tests/dashboard-utils.test.js`
- Modify: `backend/templates/index.html`
- Modify: `backend/main.py`
- Modify: `server.js`

- [ ] **Step 1: Write failing browser utility tests**

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');
const { escapeHtml, diagnosisMeta } = require('../templates/dashboard-utils');

test('escapes report-controlled HTML', () => {
  assert.equal(escapeHtml('<img src=x onerror=alert(1)>'), '&lt;img src=x onerror=alert(1)&gt;');
});

test('maps known code and safely falls back for unknown code', () => {
  assert.equal(diagnosisMeta('DNSSEC_BROKEN').label, 'DNSSEC 配置断裂');
  assert.equal(diagnosisMeta('FUTURE_CODE').label, 'FUTURE_CODE');
});
```

- [ ] **Step 2: Run and verify RED**

Run: `node --test backend/tests/dashboard-utils.test.js`

Expected: module-not-found failure for `dashboard-utils.js`.

- [ ] **Step 3: Implement utility and serve it from both backends**

Create a UMD-style module that assigns `{ escapeHtml, diagnosisMeta }` to `window.NetCheckDashboard` in browsers and `module.exports` in Node. Map all nine diagnosis codes to Chinese labels and emerald/amber/rose/slate badge classes. Serve the file as `/dashboard-utils.js` from FastAPI with `FileResponse` and from `server.js` with JavaScript content type. Load it before the dashboard inline script.

- [ ] **Step 4: Add advanced DNS form controls and payload fields**

Add a collapsed `<details>` block containing `optDnsResolver`, `optDnsFallbackResolver`, and `optDnsDiagnosticTimeout`, defaulting to `1.1.1.1`, `1.0.0.1`, and `30000`. Include the three values in `handleGenerate` only when DNS is enabled; send default values too, while backend omission rules keep generated legacy JSON concise.

- [ ] **Step 5: Render structured diagnosis safely**

Before interpolation, escape tracking ID, device/network values, task, summary, raw log, and created time with `escapeHtml`. For tasks with `diagnosis_code`, render a badge from `diagnosisMeta`, escaped summary, and escaped `duration_ms`; unknown codes use their escaped literal code. Keep old tasks unchanged except for escaping.

- [ ] **Step 6: Run dashboard and all Node tests**

Run: `npm test`

Expected: contract, diagnosis mapping, fallback, and escaping tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/templates/dashboard-utils.js backend/tests/dashboard-utils.test.js backend/templates/index.html backend/main.py server.js
git commit -m "feat: display DNS diagnoses in dashboard"
```

### Task 10: Final Verification and Documentation

**Files:**

- Create: `docs/dns-diagnostics.md`

- [ ] **Step 1: Document the command and result fields**

Document default and overridden DNS task JSON, all diagnosis codes, CDN-safe interpretation, total timeout behavior, and the distinction between Cloudflare recursive `1.1.1.1` and dynamically discovered authoritative NS addresses. Leave the user's existing untracked `docs/app_guide.md` untouched.

- [ ] **Step 2: Run all deterministic test suites**

Run: `./gradlew test`

Expected: all Android JVM, Robolectric, and screenshot tests pass.

Run: `python -m pytest backend/tests -q`

Expected: all FastAPI tests pass.

Run: `npm test`

Expected: all Node and dashboard tests pass.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Review the final diff and ensure no generated artifacts are tracked**

Run: `git status --short && git diff --check && git diff --stat`

Expected: only intended source, tests, dependency metadata, and documentation changes; no APK, database, environment, or credential files.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/dns-diagnostics.md
git commit -m "docs: explain advanced DNS diagnostics"
```
