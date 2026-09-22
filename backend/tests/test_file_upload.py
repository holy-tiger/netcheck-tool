import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import HTTPException
from fastapi.testclient import TestClient
from starlette.requests import Request

from backend.api.files import resolve_source_ip
from backend.main import app


def make_request(headers=(), client=("203.0.113.8", 43123)):
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/api/files/upload",
        "headers": [
            (key.lower().encode(), value.encode())
            for key, value in headers
        ],
        "client": client,
        "server": ("testserver", 80),
        "scheme": "http",
        "query_string": b"",
    }
    return Request(scope)


class SourceIpTest(unittest.TestCase):
    def test_invalid_forwarded_for_falls_back_to_real_ip(self):
        request = make_request(
            (
                ("X-Forwarded-For", "invalid"),
                ("X-Real-IP", "192.0.2.9"),
            )
        )

        self.assertEqual("192.0.2.9", resolve_source_ip(request))

    def test_missing_proxy_headers_fall_back_to_tcp_peer(self):
        self.assertEqual("203.0.113.8", resolve_source_ip(make_request()))

    def test_no_valid_address_is_rejected(self):
        request = make_request(
            (("X-Real-IP", "invalid"),),
            client=("also-invalid", 1),
        )

        with self.assertRaises(HTTPException) as context:
            resolve_source_ip(request)

        self.assertEqual(400, context.exception.status_code)


class FileUploadApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.env_patch = patch.dict(
            os.environ,
            {"DOH_REPORT_DIR": self.temp_dir.name},
        )
        self.env_patch.start()
        self.addCleanup(self.env_patch.stop)
        self.client = TestClient(app, raise_server_exceptions=False)
        self.addCleanup(self.client.close)

    def test_upload_saves_content_under_generated_name(self):
        response = self.client.post(
            "/api/files/upload",
            files={"file": ("report.JSON", b'{"ok":true}', "application/json")},
            headers={
                "X-Forwarded-For": "198.51.100.24, 10.0.0.4",
                "X-Real-IP": "192.0.2.99",
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("success", body["status"])
        self.assertEqual("198.51.100.24", body["source_ip"])
        self.assertEqual(11, body["size"])
        self.assertRegex(
            body["filename"],
            r"^198\.51\.100\.24_\d{8}T\d{12}Z\.JSON$",
        )
        self.assertRegex(body["uploaded_at"], r"^\d{4}-\d{2}-\d{2}T")
        self.assertEqual(
            b'{"ok":true}',
            Path(self.temp_dir.name, body["filename"]).read_bytes(),
        )

    def test_empty_supported_file_is_accepted(self):
        response = self.client.post(
            "/api/files/upload",
            files={"file": ("empty.txt", b"", "text/plain")},
            headers={"X-Forwarded-For": "198.51.100.25"},
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual(0, response.json()["size"])

    def test_missing_file_is_rejected_by_request_validation(self):
        response = self.client.post(
            "/api/files/upload",
            headers={"X-Forwarded-For": "198.51.100.25"},
        )

        self.assertEqual(422, response.status_code)

    def test_ipv6_and_original_path_are_sanitized(self):
        response = self.client.post(
            "/api/files/upload",
            files={
                "file": (
                    "../../private/report.log",
                    b"dns output",
                    "text/plain",
                )
            },
            headers={"X-Forwarded-For": "2001:db8::7"},
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertRegex(
            body["filename"],
            r"^2001-db8--7_\d{8}T\d{12}Z\.log$",
        )
        self.assertNotIn("private", body["filename"])
