#!/usr/bin/env python3
"""Announce a merged Gieligotchi pull request in Discord."""

from __future__ import annotations

import json
import os
import re
import urllib.request
from pathlib import Path


def extract_summary(body: str, fallback: str) -> str:
	"""Return the PR Summary section without including validation/checklist text."""
	body = (body or "").replace("\r\n", "\n").strip()
	match = re.search(
		r"(?ims)^#{1,6}\s+summary\s*$\n(.*?)(?=^#{1,6}\s+\S|\Z)",
		body,
	)
	if match:
		body = match.group(1).strip()
	if not body:
		body = fallback.strip()
	body = re.sub(r"<!--.*?-->", "", body, flags=re.S).strip()
	return body if len(body) <= 3500 else body[:3499].rstrip() + "…"


def main() -> None:
	event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
	pr = event["pull_request"]
	role_id = os.environ.get("DISCORD_UPDATE_ROLE_ID", "").strip()
	webhook_url = os.environ.get("DISCORD_UPDATES_WEBHOOK", "").strip()
	if not role_id:
		raise RuntimeError("DISCORD_UPDATE_ROLE_ID is not configured")
	if not webhook_url:
		raise RuntimeError("DISCORD_UPDATES_WEBHOOK is not configured")

	summary = extract_summary(pr.get("body") or "", pr["title"])
	payload = {
		"content": f"<@&{role_id}>",
		"username": "Gieligotchi Bot",
		"allowed_mentions": {"parse": [], "roles": [role_id]},
		"embeds": [
			{
				"title": f"Update merged: {pr['title']}",
				"url": pr["html_url"],
				"description": summary,
				"color": 0x77A95B,
				"footer": {"text": f"Pull request #{pr['number']}"},
				"timestamp": pr.get("merged_at") or pr.get("updated_at"),
			}
		],
	}
	request = urllib.request.Request(
		webhook_url + ("&" if "?" in webhook_url else "?") + "wait=true",
		data=json.dumps(payload).encode("utf-8"),
		headers={
			"Content-Type": "application/json",
			"User-Agent": "gieligotchi-merged-pr-announcer",
		},
		method="POST",
	)
	with urllib.request.urlopen(request, timeout=30):
		pass
	print(f"Announced merged pull request #{pr['number']}.")


if __name__ == "__main__":
	main()
