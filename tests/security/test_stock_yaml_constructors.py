"""Stock uploads must not invoke Python callables while decoding YAML."""

import warnings

from inventory.importer import parse_stock_file


def test_stock_import_does_not_execute_yaml_constructors():
    assert parse_stock_file(b"items: [{sku: A1, qty: 1}]\n") == [
        {"sku": "A1", "qty": 1}
    ]

    # On vulnerable PyYAML, tuple consumes map and invokes warnings.warn.
    # The only side effect is an in-process warning captured below.
    marker = "stock-yaml-constructor-executed"
    upload = b"""items: []
probe: !!python/object/new:tuple
  - !!python/object/new:map
    - !!python/name:warnings.warn
    - [stock-yaml-constructor-executed]
"""
    with warnings.catch_warnings(record=True) as observed:
        warnings.simplefilter("always")
        try:
            parse_stock_file(upload)
        except Exception:
            # Rejecting the document is fine, but rejection after executing
            # a constructor must still fail the security assertion.
            pass

    assert not any(marker in str(w.message) for w in observed), (
        "Stock import executed a Python callable from untrusted YAML"
    )
