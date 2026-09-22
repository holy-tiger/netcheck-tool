# Upload Filenames and Report Geolocation Design

## Goal

Preserve a safe form of the client-provided filename for uploaded DoH report
files, adding a random numeric suffix only when the name already exists. Also
capture the source IP of structured diagnostic reports, resolve its country
through a best-effort online service, persist both values, and display them on
the report dashboard.

## Safe Uploaded Filenames

The `POST /api/files/upload` endpoint continues to accept only `.json`, `.txt`,
and `.log` files up to 20 MiB and continues to enrich their contents with
request metadata.

Derive the stored name from `UploadFile.filename` as follows:

1. Treat both `/` and `\` as path separators and retain only the final segment.
2. Remove Unicode control characters and trim leading and trailing whitespace.
3. Reject an empty name, `.` or `..` with HTTP 400.
4. Validate the final extension case-insensitively against the existing allow
   list and retain the client's extension spelling.
5. Limit the safe name to at most 200 UTF-8 bytes, truncating the stem without
   splitting a multibyte character and always retaining the extension.

If the safe name does not exist, publish the upload under that exact name. If
it exists, insert an underscore and a cryptographically secure six-digit
number before the extension, for example `report_483921.json`. Retry with a new
number if the candidate also exists. Atomic hard-link publication continues to
prevent overwriting during concurrent uploads.

The successful response's `filename` field contains the actual stored name.
Source-IP resolution and the response's `source_ip` field remain unchanged,
even though the source IP is no longer part of the filename.

## Diagnostic Report Source IP

The structured report endpoints at `POST /api/reports` and
`POST /api/reports/` receive the HTTP request in addition to the existing JSON
payload. Resolve the source address using the established order:

1. the first valid address in `X-Forwarded-For`;
2. a valid `X-Real-IP` address;
3. a valid TCP peer address.

Normalize the selected IPv4 or IPv6 address before persistence. Invalid proxy
header values are skipped. If no source contains a valid address, reject the
submission with HTTP 400.

Move shared FastAPI address parsing into a focused backend module so file
uploads and structured reports use identical behavior. The Node development
server implements the same resolution rules with Node's standard IP parser.

## Online Country Resolution

After resolving a public source IP, query an online geolocation API before
writing the report. The default URL template is:

```text
https://ipwho.is/{ip}
```

Allow deployments to override it with `IP_GEOLOCATION_URL_TEMPLATE`; the
template must contain `{ip}`. Use a two-second timeout. Accept a response only
when it is valid JSON, does not explicitly report failure, and contains a
non-empty string `country`. Otherwise return the display value `未知` from the
lookup component.

Private, loopback, link-local, multicast, reserved, and unspecified addresses
must not be sent to the external service and resolve directly to `未知`.
Timeouts, DNS failures, connection errors, non-success HTTP responses, malformed
JSON, and unexpected response shapes must never reject the diagnostic report;
they resolve to `未知` and are logged without exposing details to the client.

FastAPI uses an asynchronous HTTP client with redirects disabled. The Node
development server uses its built-in `fetch` with an abort timeout. Both send
only the source IP required by the chosen online provider.

## Persistence and Migration

Add nullable `source_ip` and `country` columns to `diagnostic_reports`. New
database creation includes both columns. Startup migration inspects the table
schema and runs the required `ALTER TABLE` statements only for missing
columns, allowing existing SQLite volumes to upgrade without data loss.

New and updated reports persist the resolved IP and country. The existing
Tracking ID upsert also updates both columns. List and single-report queries
return:

```json
{
  "source_ip": "175.29.122.236",
  "country": "China"
}
```

For rows created before migration, API serialization maps a null or empty
country to `未知` and a null or empty source IP to `未知`.

The Node development server performs an equivalent startup migration and
returns the same fields so its dashboard behavior stays compatible with the
FastAPI deployment.

## Dashboard

Add an `IP / 国家` column between `网络环境` and `任务状态` in the latest-report
table. The first line shows `source_ip`; the second shows `country`. Update
loading, empty, and failure rows from five to six columns.

Add the corresponding translated header key for Chinese, English, and Arabic.
Dynamic IP and country values are rendered as text-safe escaped content. The
detail lookup continues to receive both fields from the single-report API;
this increment changes only the requested report-list table presentation.

Update the deployment guide with the online API behavior, configuration
variable, two-second degradation policy, and privacy warning that public
source IPs are sent to the configured provider.

## Error Handling

- Unsafe or missing upload filenames return HTTP 400 and leave no file.
- Filename collision retry never overwrites an existing report.
- Missing `{ip}` in a configured URL template disables lookup and yields
  `未知` rather than rejecting reports.
- Country lookup failures yield `未知`; only invalid source-IP resolution can
  reject a structured report.
- Database migration or write failures remain server errors and are logged.

## Tests and Validation

Backend tests cover:

- exact preservation of safe ASCII and Unicode filenames;
- removal of client path components and control characters;
- rejection of empty and reserved filenames;
- UTF-8 byte-length truncation with extension retention;
- six-digit collision suffixes, repeated collisions, and no overwrite;
- report source-IP precedence and normalization;
- successful country response parsing;
- private IP short-circuit without an HTTP request;
- timeout, HTTP error, malformed response, and bad-template degradation;
- migration of an existing SQLite schema;
- insert and upsert persistence of `source_ip` and `country`;
- list and detail response compatibility for old and new rows;
- Node route and migration parity;
- dashboard column, translated headers, escaping, and six-column empty states.

Final validation runs all Python and Node tests, renders the Compose
configuration, builds the production image, and verifies the upload and report
routes in the image's OpenAPI document.

## Out of Scope

This increment does not provide precise city coordinates, ISP or ASN data,
historical backfilling for old reports, a bundled offline geolocation database,
or authentication. It does not make country lookup mandatory for accepting a
report.
