# DoH Report File Upload Design

## Goal

Add a FastAPI endpoint that accepts DoH report files, enriches them with
request-address metadata, and stores them under `/data/doh_report`. Docker
deployments must bind that container directory to the host directory at the
same path so uploaded files survive container replacement and are directly
available on the host.

## API Contract

Expose `POST /api/files/upload` in a dedicated files router. The request uses
`multipart/form-data` with one required field named `file`.

Only files whose final extension is `.json`, `.txt`, or `.log` are accepted.
Extension comparison is case-insensitive, while the extension from the
uploaded filename is retained in the stored filename. A file may contain at
most 20 MiB. An empty file is valid.

Successful requests return JSON containing:

- `status`: `success`
- `filename`: the generated stored filename
- `size`: the number of bytes written
- `source_ip`: the validated source IP used in the filename
- `uploaded_at`: the UTC upload time

Unsupported extensions return HTTP 400. Files larger than 20 MiB return HTTP
413. A missing multipart file remains FastAPI's standard HTTP 422 validation
response. Invalid JSON or a JSON top-level value other than an object returns
HTTP 400. Storage failures return HTTP 500 without exposing internal paths or
exception details.

## Request Metadata Enrichment

Write these four PHP-style server variables into every stored report:

| Stored field | Request source |
| --- | --- |
| `HTTP_CLIENT_IP` | Complete `Client-IP` request-header value |
| `HTTP_TRUE_CLIENT_IP` | Complete `True-Client-IP` request-header value |
| `HTTP_X_FORWARDED_FOR` | Complete `X-Forwarded-For` request-header value |
| `REMOTE_ADDR` | TCP peer address from `request.client.host` |

Use JSON `null` for a missing request header or unavailable TCP peer. These
values record what the server received; they do not need to contain valid IP
addresses. This is separate from the validated source IP used in the stored
filename. In particular, `REMOTE_ADDR` always records the TCP peer and never a
forwarding header.

For a `.json` upload, require UTF-8 content that parses to a JSON object. Add
the four fields at the object's top level, overwriting client-supplied values
with the server-observed values. Serialize the enriched object as UTF-8 JSON.
Reject malformed JSON and non-object top-level values without leaving a file.

For `.txt` and `.log`, prepend four lines in the field order shown above. Each
line has the form `<field>=<JSON literal>`, so strings are quoted and escaped
and absent values appear as `null`. Add one blank line after the metadata and
then copy the original uploaded bytes unchanged. For example:

```text
HTTP_CLIENT_IP=null
HTTP_TRUE_CLIENT_IP="203.0.113.10"
HTTP_X_FORWARDED_FOR="175.29.122.236"
REMOTE_ADDR="10.11.0.23"

<original content starts here>
```

The 20 MiB limit applies to the original uploaded content. The small
server-generated metadata prefix or JSON fields do not count against it.

## Source IP and Filename

Resolve the source IP in this order:

1. The first comma-separated address in `X-Forwarded-For`.
2. `X-Real-IP`.
3. The request's TCP client address.

Each proxy-header candidate must parse as a valid IPv4 or IPv6 address. An
invalid candidate is skipped and resolution continues to the next source. If
none of the candidates are valid, return HTTP 400.

The stored filename has the form:

```text
<safe-source-ip>_<UTC timestamp including microseconds><original extension>
```

Use a compact UTC timestamp with microseconds, such as
`20260922T081530123456Z`. Preserve IPv4 dots. Replace IPv6 colons with hyphens,
which prevents path ambiguity while keeping the address recognizable. Ignore
the rest of the client-provided filename so it cannot introduce path traversal
or unsafe path characters.

Proxy headers are trusted by explicit product requirement. Deployments must
ensure that only a trusted reverse proxy can reach the backend or that the
proxy overwrites incoming forwarding headers.

## Storage Flow

Put the route in `backend/api/files.py` and register its router in
`backend/main.py`. Read the target directory from `DOH_REPORT_DIR`, defaulting
to `/data/doh_report`, so tests and non-Docker development can use an isolated
directory.

Create the target directory when processing an upload. Stream the upload in
bounded chunks into a temporary file in that directory while counting bytes.
If the count exceeds 20 MiB, close and remove the temporary file, then return
HTTP 413. After the complete upload succeeds, publish it with an atomic hard
link from the temporary file to the final path and then remove the temporary
name. This link operation must not replace an existing path. The microsecond
timestamp makes collisions unlikely; if a generated name already exists,
regenerate the timestamp rather than overwrite an existing report.

Always close the uploaded file and remove any partial temporary file after an
error. Log server-side storage exceptions without returning their details to
the client.

## Dependencies and Docker Deployment

Add `python-multipart` to `backend/requirements.txt` for FastAPI multipart
parsing.

Keep the existing named volume mounted at `/data` for SQLite, and add the more
specific bind mount below to the backend service:

```yaml
- /data/doh_report:/data/doh_report
```

The nested bind mount makes the host's `/data/doh_report` directory visible at
the required container path while leaving `/data/diagnostic.db` on the named
volume. The deployment guide must instruct operators to create the host
directory and grant write access to container UID `10001` before starting the
service.

## Tests and Validation

FastAPI tests use a temporary `DOH_REPORT_DIR` and cover:

- successful JSON upload with all four server-observed metadata fields;
- server metadata overriding spoofed fields already present in JSON;
- text and log uploads retaining their original bytes after the metadata
  prefix;
- absent request headers being stored as `null`;
- invalid, non-UTF-8, or non-object JSON being rejected without a partial
  file;
- `X-Forwarded-For`, `X-Real-IP`, and TCP-address precedence;
- generated IPv4 and IPv6-safe filenames with the original extension;
- rejection of unsupported extensions;
- rejection of an upload larger than 20 MiB without a partial file;
- traversal-like original filenames being ignored;
- storage errors returning a sanitized HTTP 500 response.

Validation also runs the complete backend test suite and checks the rendered
Compose configuration to confirm the host bind mount.

## Out of Scope

This change does not add authentication, file listing, download, deletion,
archive extraction, content inspection, malware scanning, or dashboard UI.
