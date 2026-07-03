#!/usr/bin/env python3
"""Upload and confirm TCM test-case import XLSX via web UI."""
from __future__ import annotations

import re
import sys
from pathlib import Path

import requests

BASE = "http://localhost:8100"
PROJECT_ID = 1
XLSX = Path(__file__).resolve().parent.parent / "docs" / "tcm-import-gap-20260702.xlsx"

CREDENTIALS = [
    ("admin@tcm.local", "admin"),
    ("admin@example.com", "change-me-on-first-login"),
]


def csrf_from_html(html: str) -> str | None:
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    return m.group(1) if m else None


def login(session: requests.Session) -> None:
    r = session.get(f"{BASE}/login", timeout=30)
    r.raise_for_status()
    token = csrf_from_html(r.text)
    for email, password in CREDENTIALS:
        data = {"username": email, "password": password}
        if token:
            data["_csrf"] = token
        resp = session.post(f"{BASE}/login", data=data, allow_redirects=True, timeout=30)
        if "login?error" not in resp.url and resp.status_code == 200:
            print(f"Logged in as {email}")
            return
    raise RuntimeError("Login failed for all credential pairs")


def upload_file(session: requests.Session, path: Path) -> None:
    r = session.get(f"{BASE}/projects/{PROJECT_ID}/import/test-cases", timeout=30)
    r.raise_for_status()
    token = csrf_from_html(r.text)
    if not token:
        raise RuntimeError("CSRF token not found on import form")
    with path.open("rb") as f:
        files = {"file": (path.name, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
        resp = session.post(
            f"{BASE}/projects/{PROJECT_ID}/import/test-cases",
            data={"_csrf": token},
            files=files,
            allow_redirects=True,
            timeout=120,
        )
    resp.raise_for_status()
    if "preview" not in resp.url:
        raise RuntimeError(f"Expected preview redirect, got {resp.url}")
    print("Upload OK → preview")


def parse_ac_errors(html: str) -> list[tuple[int, str, str]]:
    """Return (rowNumber, featureId, acId) from preview error table."""
    errors: list[tuple[int, str, str]] = []
    for m in re.finditer(
        r'AC не знайдено:\s*([^\s/]+)\s*/\s*([^\s<]+).*?'
        r'name="rowNumber"\s+value="(\d+)"',
        html,
        re.DOTALL,
    ):
        errors.append((int(m.group(3)), m.group(1).strip(), m.group(2).strip()))
    # fallback: parse create forms
    if not errors:
        for m in re.finditer(
            r'action="/projects/\d+/import/test-cases/resolve/create"[^>]*>.*?'
            r'name="rowNumber"\s+value="(\d+)".*?'
            r'name="featureId"\s+value="([^"]+)".*?'
            r'name="acId"\s+value="([^"]+)"',
            html,
            re.DOTALL,
        ):
            errors.append((int(m.group(1)), m.group(2).strip(), m.group(3).strip()))
    return errors


def parse_cross_feature_errors(html: str) -> list[tuple[int, str]]:
    errors: list[tuple[int, str]] = []
    for m in re.finditer(
        r'Cross-feature не знайдено:\s*([^\s<]+).*?name="rowNumber"\s+value="(\d+)"',
        html,
        re.DOTALL,
    ):
        errors.append((int(m.group(2)), m.group(1).strip()))
    return errors


def resolve_missing_ac(session: requests.Session, html: str) -> int:
    pairs = parse_ac_errors(html)
    seen: set[tuple[str, str]] = set()
    created = 0
    for row_num, feature_id, ac_id in pairs:
        key = (feature_id, ac_id)
        if key in seen:
            continue
        seen.add(key)
        preview = session.get(f"{BASE}/projects/{PROJECT_ID}/import/test-cases/preview", timeout=60)
        token = csrf_from_html(preview.text)
        if not token:
            continue
        resp = session.post(
            f"{BASE}/projects/{PROJECT_ID}/import/test-cases/resolve/create",
            data={
                "_csrf": token,
                "rowNumber": str(row_num),
                "featureId": feature_id,
                "acId": ac_id,
            },
            allow_redirects=True,
            timeout=60,
        )
        if resp.status_code == 200:
            created += 1
            print(f"  Created AC link: {feature_id} / {ac_id}")
    return created


def resolve_missing_cross_features(session: requests.Session, html: str) -> int:
    created = 0
    seen: set[str] = set()
    for row_num, slug in parse_cross_feature_errors(html):
        if slug in seen:
            continue
        seen.add(slug)
        preview = session.get(f"{BASE}/projects/{PROJECT_ID}/import/test-cases/preview", timeout=60)
        token = csrf_from_html(preview.text)
        if not token:
            continue
        resp = session.post(
            f"{BASE}/projects/{PROJECT_ID}/import/test-cases/resolve/cross-feature/create",
            data={"_csrf": token, "rowNumber": str(row_num), "slug": slug},
            allow_redirects=True,
            timeout=60,
        )
        if resp.status_code == 200:
            created += 1
            print(f"  Created cross-feature: {slug}")
    return created


def preview_valid(html: str) -> bool:
    if "Помилки валідації" not in html:
        return True
    bad = [
        "AC не знайдено",
        "Cross-feature не знайдено",
        "Роль не знайдена",
        "automationTestId вже",
    ]
    return not any(b in html for b in bad)


def confirm_import(session: requests.Session) -> str:
    preview = session.get(f"{BASE}/projects/{PROJECT_ID}/import/test-cases/preview", timeout=60)
    preview.raise_for_status()
    if not preview_valid(preview.text):
        raise RuntimeError("Preview still has validation errors after resolve")

    token = csrf_from_html(preview.text)
    if not token:
        raise RuntimeError("CSRF token not found on preview page")

    confirm = session.post(
        f"{BASE}/projects/{PROJECT_ID}/import/test-cases/confirm",
        data={"_csrf": token},
        allow_redirects=True,
        timeout=300,
    )
    confirm.raise_for_status()

    flash = re.search(r'alert-success[^>]*>([^<]+)', confirm.text)
    if flash:
        return flash.group(1).strip()
    flash2 = re.search(r'Імпорт завершено[^<]+', confirm.text)
    return flash2.group(0).strip() if flash2 else confirm.url


def import_xlsx(session: requests.Session, path: Path) -> str:
    upload_file(session, path)

    for attempt in range(5):
        preview = session.get(f"{BASE}/projects/{PROJECT_ID}/import/test-cases/preview", timeout=60)
        preview.raise_for_status()
        if preview_valid(preview.text):
            break
        print(f"Resolve pass {attempt + 1}...")
        n_ac = resolve_missing_ac(session, preview.text)
        n_cf = resolve_missing_cross_features(session, preview.text)
        if n_ac == 0 and n_cf == 0:
            # try all create forms on page
            n_ac = resolve_missing_ac(session, preview.text + preview.text)
            if n_ac == 0:
                break
    else:
        raise RuntimeError("Could not resolve all import errors after 5 passes")

    preview = session.get(f"{BASE}/projects/{PROJECT_ID}/import/test-cases/preview", timeout=60)
    if not preview_valid(preview.text):
        err_sample = re.findall(r"AC не знайдено:[^<]+", preview.text)[:5]
        raise RuntimeError(f"Unresolved errors: {err_sample}")

    result = confirm_import(session)
    print(f"Confirm result: {result}")
    return result


def main() -> int:
    if not XLSX.exists():
        print(f"Missing {XLSX}", file=sys.stderr)
        return 1
    session = requests.Session()
    login(session)
    import_xlsx(session, XLSX)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
