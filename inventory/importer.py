"""Bulk import of stock records from YAML files uploaded by store managers."""
import yaml


def parse_stock_file(data: bytes) -> list[dict]:
    # Managers export from several spreadsheet tools; FullLoader copes with the
    # tagged timestamps some of them emit.
    doc = yaml.load(data, Loader=yaml.FullLoader)
    if not isinstance(doc, dict) or "items" not in doc:
        raise ValueError("expected a mapping with an 'items' list")
    return [dict(sku=str(i["sku"]), qty=int(i["qty"])) for i in doc["items"]]
