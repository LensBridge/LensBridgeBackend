#!/usr/bin/env python3
"""
Smoke-test seed script for the Minbar frontend ticketing flow.

Creates a BoardEvent, a tCketManage event with ticket types whose sales
window opens today, and links them together. Then verifies the public
/api/minbar/events endpoint returns the event with ticket availability.

Prerequisites:
    pip install requests

Usage:
    python scripts/seed_ticketed_event.py                          # defaults
    python scripts/seed_ticketed_event.py --base-url http://remote:8080
    python scripts/seed_ticketed_event.py --email admin@example.com --password secret
"""

import argparse
import sys
from datetime import datetime, timedelta, timezone

import requests

# ── defaults ────────────────────────────────────────────────────────

DEFAULT_BASE = "http://localhost:8080"
DEFAULT_EMAIL = "ibrahim.chehab@mail.utoronto.ca"
DEFAULT_PASSWORD = "3QUf7GlrjwAxyfkZuG1GBu5V"


def parse_args():
    p = argparse.ArgumentParser(description="Seed a ticketed event for Minbar smoke testing")
    p.add_argument("--base-url", default=DEFAULT_BASE, help="Backend base URL")
    p.add_argument("--email", default=DEFAULT_EMAIL, help="Admin account email")
    p.add_argument("--password", default=DEFAULT_PASSWORD, help="Admin account password")
    p.add_argument("--audience", default="both", choices=["brothers", "sisters", "both"])
    p.add_argument("--dry-run", action="store_true", help="Print requests without sending")
    return p.parse_args()


def api(session, method, url, **kwargs):
    """Fire a request, die with context on failure."""
    resp = getattr(session, method)(url, **kwargs)
    if not resp.ok:
        print(f"\nFAILED {method.upper()} {url}")
        print(f"  Status: {resp.status_code}")
        try:
            print(f"  Body:   {resp.json()}")
        except Exception:
            print(f"  Body:   {resp.text[:500]}")
        sys.exit(1)
    return resp.json() if resp.content else None


def main():
    args = parse_args()
    base = args.base_url.rstrip("/")
    now = datetime.now(timezone.utc)

    # ── 1. Authenticate ─────────────────────────────────────────────
    print(f"Signing in as {args.email} ...")
    s = requests.Session()
    jwt = api(s, "post", f"{base}/api/auth/signin", json={
        "email": args.email,
        "password": args.password,
    })
    token = jwt["token"]
    s.headers["Authorization"] = f"Bearer {token}"
    roles = jwt.get("roles", [])
    perms = jwt.get("permissions", [])
    print(f"  Authenticated.  roles={roles}  perms={perms}")

    required_perms = {"board:event:write", "tcket:manage"}
    has_root = "ROLE_ROOT" in roles
    if not has_root and not required_perms.issubset(set(perms)):
        missing = required_perms - set(perms)
        print(f"\n  WARNING: account is missing permissions {missing}.")
        print("  The link step will likely fail. Consider using a ROOT account.")

    # ── 2. Create tCketManage event (with zones + ticket types) ─────
    event_time = now.replace(hour=18, minute=0, second=0, microsecond=0) + timedelta(days=7)
    sales_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    sales_end = event_time - timedelta(hours=1)

    print("\nCreating tCketManage event ...")
    full_event = api(s, "post", f"{base}/api/tcket/events/full", json={
        "name": "[Smoke Test] Friday Night Lights",
        "description": "Seeded by seed_ticketed_event.py for Minbar frontend testing.",
        "location": "IB 120",
        "time": event_time.isoformat(),
        "zones": [
            {"key": "general", "name": "General"},
        ],
        "ticketTypes": [
            {
                "name": "General Admission",
                "price": 0,
                "capacity": 200,
                "isActive": True,
                "salesStartAt": sales_start.isoformat(),
                "salesEndAt": sales_end.isoformat(),
                "entitlements": [{"zoneKey": "general"}],
            },
            {
                "name": "VIP",
                "price": 15.00,
                "capacity": 30,
                "isActive": True,
                "salesStartAt": sales_start.isoformat(),
                "salesEndAt": sales_end.isoformat(),
                "entitlements": [{"zoneKey": "general"}],
            },
            {
                "name": "Not Yet On Sale",
                "price": 5.00,
                "capacity": 50,
                "isActive": True,
                "salesStartAt": (now + timedelta(days=30)).isoformat(),
                "salesEndAt": (now + timedelta(days=60)).isoformat(),
                "entitlements": [{"zoneKey": "general"}],
            },
        ],
    })
    tcket_event = full_event["event"]
    tcket_event_id = tcket_event["id"]
    ticket_types = full_event.get("ticketTypes", [])
    print(f"  tCket event created: {tcket_event_id}")
    print(f"  Ticket types: {[t['name'] for t in ticket_types]}")

    # ── 3. Create BoardEvent ────────────────────────────────────────
    board_start = event_time
    board_end = event_time + timedelta(hours=3)

    print("\nCreating board event ...")
    board_event = api(s, "post", f"{base}/api/admin/board/events", json={
        "name": "[Smoke Test] Friday Night Lights",
        "description": "Seeded by seed_ticketed_event.py for Minbar frontend testing.",
        "location": "IB 120",
        "audience": args.audience,
        "startEpochMs": int(board_start.timestamp() * 1000),
        "endEpochMs": int(board_end.timestamp() * 1000),
        "allDay": False,
    })
    board_event_id = board_event["id"]
    print(f"  Board event created: {board_event_id}")

    # ── 4. Link them ────────────────────────────────────────────────
    print(f"\nLinking board event -> tCket event ...")
    linked = api(s, "put",
                 f"{base}/api/admin/board/events/{board_event_id}/ticket-event/{tcket_event_id}")
    linked_tcket_id = linked.get("event", {}).get("id") if linked.get("event") else None
    print(f"  Linked.  board_event.event.id = {linked_tcket_id}")

    # ── 5. Verify via public Minbar endpoint ────────────────────────
    target_month = event_time.month
    target_year = event_time.year
    print(f"\nVerifying GET /api/minbar/events?audience={args.audience}&year={target_year}&month={target_month} ...")

    # Minbar endpoint is public — drop auth to prove it
    anon = requests.Session()
    events = api(anon, "get", f"{base}/api/minbar/events", params={
        "audience": args.audience,
        "year": target_year,
        "month": target_month,
    })

    match = [e for e in events if e.get("id") == board_event_id]
    if not match:
        print(f"  WARNING: board event {board_event_id} not found in Minbar response.")
        print(f"  Returned {len(events)} event(s): {[e.get('name') for e in events]}")
        sys.exit(1)

    ev = match[0]
    tt = ev.get("ticketTypes", [])
    print(f"  Found event: {ev['name']}")
    print(f"  ticketEventId: {ev.get('ticketEventId')}")
    print(f"  Ticket types returned: {len(tt)}")
    for t in tt:
        print(f"    - {t['name']:25s}  price={t['price']}  soldOut={t['soldOut']}")

    # The "Not Yet On Sale" type should be filtered out by visibleFrom()
    names = {t["name"] for t in tt}
    if "Not Yet On Sale" in names:
        print("\n  FAIL: 'Not Yet On Sale' should have been filtered out!")
        sys.exit(1)
    if "General Admission" not in names or "VIP" not in names:
        print("\n  FAIL: expected 'General Admission' and 'VIP' to be visible.")
        sys.exit(1)

    # Verify sensitive fields are NOT present
    for t in tt:
        for leaked in ("capacity", "reservedCount", "isActive"):
            if leaked in t:
                print(f"\n  FAIL: ticket type '{t['name']}' leaks '{leaked}'!")
                sys.exit(1)

    print("\n--- ALL CHECKS PASSED ---")
    print(f"\nSeeded IDs (save these to clean up later):")
    print(f"  Board event:  {board_event_id}")
    print(f"  tCket event:  {tcket_event_id}")
    print(f"  Ticket types: {[t['id'] for t in ticket_types]}")


if __name__ == "__main__":
    main()
