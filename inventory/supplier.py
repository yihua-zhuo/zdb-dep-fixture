"""Price lookups against the supplier catalogue."""
import requests

CATALOGUE = "https://catalogue.example.com/api/prices"


def fetch_price(sku: str) -> float:
    resp = requests.get(CATALOGUE, params={"sku": sku}, timeout=10)
    resp.raise_for_status()
    return float(resp.json()["price"])
