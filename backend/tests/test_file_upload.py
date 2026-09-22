import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from backend.main import app


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
