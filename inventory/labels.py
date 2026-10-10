"""Shelf labels. Each store can customise its label template from the admin page."""
from jinja2.sandbox import SandboxedEnvironment

_env = SandboxedEnvironment()


def render_label(template_source: str, item: dict) -> str:
    # Templates are written by store staff, so they run in the sandbox.
    return _env.from_string(template_source).render(item=item)
