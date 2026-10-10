"""Inventory service: a small HTTP API for store managers."""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer

from inventory.importer import parse_stock_file
from inventory.labels import render_label

STOCK: dict[str, int] = {}
LABEL_TEMPLATES: dict[str, str] = {"default": "{{ item.sku }} x{{ item.qty }}"}


class Handler(BaseHTTPRequestHandler):
    def _body(self) -> bytes:
        return self.rfile.read(int(self.headers.get("Content-Length", "0")))

    def _send(self, status: int, payload) -> None:
        out = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def do_POST(self):
        if self.path == "/stock/import":
            items = parse_stock_file(self._body())
            for i in items:
                STOCK[i["sku"]] = i["qty"]
            return self._send(200, {"imported": len(items)})
        if self.path.startswith("/labels/template/"):
            store = self.path.rsplit("/", 1)[1]
            LABEL_TEMPLATES[store] = self._body().decode()
            return self._send(200, {"saved": store})
        if self.path.startswith("/labels/render/"):
            store = self.path.rsplit("/", 1)[1]
            req = json.loads(self._body())
            item = {"sku": req["sku"], "qty": STOCK.get(req["sku"], 0)}
            tpl = LABEL_TEMPLATES.get(store, LABEL_TEMPLATES["default"])
            return self._send(200, {"label": render_label(tpl, item)})
        self._send(404, {"error": "not found"})


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
