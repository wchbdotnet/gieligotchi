#!/usr/bin/env python3
"""Post meaningful RuneLite Plugin Hub PR changes to Discord."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import urllib.request


REPOSITORY = "runelite/plugin-hub"
PR_NUMBER = 16119
PR_URL = f"https://github.com/{REPOSITORY}/pull/{PR_NUMBER}"
API = "https://api.github.com"
STATE_FILE = Path(".plugin-hub-monitor/state.json")


def github(path: str):
    request = urllib.request.Request(
        API + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "User-Agent": "gieligotchi-plugin-hub-monitor",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def discord(payload):
    url = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not url:
        raise RuntimeError("PLUGIN_HUB_DISCORD_WEBHOOK is not configured")
    request = urllib.request.Request(
        url + ("&" if "?" in url else "?") + "wait=true",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": "gieligotchi-plugin-hub-monitor"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30):
        pass


def clean(text: str, limit: int = 500):
    text = " ".join((text or "").split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def set_changed(value: bool):
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"changed={'true' if value else 'false'}\n")


def main():
    pr = github(f"/repos/{REPOSITORY}/pulls/{PR_NUMBER}")
    comments = github(f"/repos/{REPOSITORY}/issues/{PR_NUMBER}/comments?per_page=100")
    reviews = github(f"/repos/{REPOSITORY}/pulls/{PR_NUMBER}/reviews?per_page=100")
    checks = github(f"/repos/{REPOSITORY}/commits/{pr['head']['sha']}/check-runs?per_page=100")["check_runs"]

    snapshot = {
        "state": pr["state"],
        "draft": pr["draft"],
        "merged": pr["merged"],
        "updated_at": pr["updated_at"],
        "head": pr["head"]["sha"],
        "requested_reviewers": sorted(user["login"] for user in pr["requested_reviewers"]),
        "comments": [(item["id"], item["updated_at"]) for item in comments],
        "reviews": [(item["id"], item["state"], item["submitted_at"]) for item in reviews],
        "checks": sorted((item["name"], item["status"], item["conclusion"], item["completed_at"]) for item in checks),
    }
    fingerprint = hashlib.sha256(json.dumps(snapshot, sort_keys=True).encode()).hexdigest()
    previous = {}
    if STATE_FILE.is_file():
        previous = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    if previous.get("fingerprint") == fingerprint:
        set_changed(False)
        print("No meaningful Plugin Hub PR change.")
        return

    latest_comment = max(comments, key=lambda item: item["updated_at"], default=None)
    latest_review = max(reviews, key=lambda item: item["submitted_at"] or "", default=None)
    review_states = {item["state"] for item in reviews}
    if pr["merged"]:
        status, colour = "Merged", 0x57F287
    elif pr["state"] == "closed":
        status, colour = "Closed", 0xED4245
    elif "CHANGES_REQUESTED" in review_states:
        status, colour = "Changes requested", 0xED4245
    elif "APPROVED" in review_states:
        status, colour = "Approved — awaiting merge", 0x57F287
    elif pr["draft"]:
        status, colour = "Draft", 0x99AAB5
    else:
        status, colour = "Open — awaiting maintainer review", 0xF1C40F

    passed = sum(item["conclusion"] == "success" for item in checks)
    failed = sum(item["conclusion"] in {"failure", "timed_out", "cancelled"} for item in checks)
    pending = sum(item["status"] != "completed" for item in checks)
    fields = [
        {"name": "Status", "value": status, "inline": False},
        {"name": "Checks", "value": f"{passed} passing · {failed} failing · {pending} pending", "inline": False},
    ]
    activity = []
    if latest_review:
        activity.append(f"Review by **{latest_review['user']['login']}**: {latest_review['state'].lower().replace('_', ' ')}")
    if latest_comment:
        activity.append(f"**{latest_comment['user']['login']}**: {clean(latest_comment['body'])}")
    if activity:
        fields.append({"name": "Latest activity", "value": "\n".join(activity)[-1000:], "inline": False})

    discord({
        "username": "Gieligotchi GitHub",
        "allowed_mentions": {"parse": []},
        "embeds": [{
            "title": f"RuneLite Plugin Hub PR #{PR_NUMBER} updated",
            "url": PR_URL,
            "description": clean(pr["title"]),
            "color": colour,
            "fields": fields,
            "footer": {"text": "Gieligotchi Plugin Hub review watcher"},
            "timestamp": pr["updated_at"],
        }],
    })

    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps({"fingerprint": fingerprint}, indent=2) + "\n", encoding="utf-8")
    set_changed(True)
    print("Posted Plugin Hub PR update.")


if __name__ == "__main__":
    main()
