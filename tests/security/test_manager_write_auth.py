"""Anonymous callers must not change manager-controlled inventory or labels."""

from http.client import HTTPResponse
from io import BytesIO
import json
from types import SimpleNamespace

import pytest

from app import Handler


class MemoryConnection:
    """Socket interface used by the standard-library HTTP handler; no network."""

    def __init__(self, incoming):
        self.incoming = BytesIO(incoming)
        self.outgoing = BytesIO()

    def makefile(self, mode, *args, **kwargs):
        return self.incoming

    def sendall(self, data):
        self.outgoing.write(data)


def post(path, body, content_type):
    request = (
        "POST {} HTTP/1.0\r\n"
        "Host: localhost\r\n"
        "Content-Type: {}\r\n"
        "Content-Length: {}\r\n\r\n"
    ).format(path, content_type, len(body)).encode("ascii") + body
    connection = MemoryConnection(request)
    server = SimpleNamespace(server_name="localhost", server_port=80)
    Handler(connection, ("127.0.0.1", 12345), server)
    response = HTTPResponse(MemoryConnection(connection.outgoing.getvalue()))
    response.begin()
    return response.status, response.read()


@pytest.mark.parametrize(
    "path,body,content_type",
    [
        (
            "/stock/import",
            b"items:\n  - {sku: auth-regression-item, qty: 7}\n",
            "application/yaml",
        ),
        (
            "/labels/template/auth-regression-store",
            b"anonymous-template-marker",
            "text/plain",
        ),
    ],
    ids=["stock-import", "store-template"],
)
def test_anonymous_manager_write_is_refused(path, body, content_type):
    render_path = "/labels/render/auth-regression-store"
    render_body = json.dumps({"sku": "auth-regression-item"}).encode()
    before_status, before_body = post(render_path, render_body, "application/json")

    status, _ = post(path, body, content_type)

    after_status, after_body = post(render_path, render_body, "application/json")
    # Permit explicit denial, concealed resources, or an authentication redirect.
    assert status in (401, 403, 404, 302, 303, 307, 308), (
        "Anonymous manager write was not refused: {} returned {}".format(path, status)
    )
    # Some fixes also protect reads. If rendering remains public, verify that
    # denial was effective rather than merely an error response after a write.
    if before_status == 200 and after_status == 200:
        assert json.loads(after_body) == json.loads(before_body), (
            "Anonymous manager write changed publicly observable state"
        )
