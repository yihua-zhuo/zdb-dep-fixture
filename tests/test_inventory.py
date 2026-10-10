from inventory.importer import parse_stock_file
from inventory.labels import render_label


def test_import_parses_items():
    assert parse_stock_file(b"items:\n  - {sku: A1, qty: 3}\n") == [{"sku": "A1", "qty": 3}]


def test_label_renders():
    assert render_label("{{ item.sku }}:{{ item.qty }}", {"sku": "A1", "qty": 3}) == "A1:3"
