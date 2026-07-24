#!/usr/bin/env python3

import argparse
import html
import json
import re
import subprocess
from pathlib import Path
from typing import Any
from urllib.parse import quote
from urllib.request import Request, urlopen


COMMENT_MARKER = "<!-- icon-pack-generated-previews -->"
IDENTIFIER_PATTERN = re.compile(r"\d{4}|\d{6}")


class PreviewError(Exception):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate = subparsers.add_parser("generate")
    generate.add_argument("--base-sha", required=True)
    generate.add_argument("--metadata", type=Path, required=True)
    generate.add_argument("--svg-directory", type=Path, required=True)
    generate.add_argument("--output-directory", type=Path, required=True)
    generate.add_argument("--manifest", type=Path, required=True)
    generate.add_argument("--renderer", type=Path, required=True)

    comment = subparsers.add_parser("comment")
    comment.add_argument("--manifest", type=Path, required=True)
    comment.add_argument("--repository", required=True)
    comment.add_argument("--head-repository", required=True)
    comment.add_argument("--commit-sha", required=True)
    comment.add_argument("--pull-request", type=int, required=True)
    comment.add_argument("--token", required=True)
    return parser.parse_args()


def run(*command: str) -> str:
    result = subprocess.run(
        command,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout.strip()


def read_metadata(path: Path) -> list[dict[str, Any]]:
    try:
        metadata = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PreviewError(f"Cannot read {path.as_posix()}: {error}") from error
    if not isinstance(metadata, list) or not all(
        isinstance(entry, dict) for entry in metadata
    ):
        raise PreviewError(f"{path.as_posix()} must contain an array of objects.")
    return metadata


def read_base_metadata(revision: str) -> list[dict[str, Any]]:
    try:
        metadata = json.loads(run("git", "show", f"{revision}:metadata.json"))
    except (json.JSONDecodeError, subprocess.CalledProcessError) as error:
        raise PreviewError(
            f"Cannot read metadata.json at revision {revision}."
        ) from error
    if not isinstance(metadata, list) or not all(
        isinstance(entry, dict) for entry in metadata
    ):
        raise PreviewError(
            f"metadata.json at revision {revision} must contain an array."
        )
    return metadata


def entry_identifier(entry: dict[str, Any]) -> str:
    identifier = str(entry.get("Id", ""))
    if IDENTIFIER_PATTERN.fullmatch(identifier) is None:
        raise PreviewError(f'Invalid metadata icon Id "{identifier}".')
    return identifier


def metadata_text(
    entry: dict[str, Any],
    field: str,
    identifier: str,
) -> str:
    value = entry.get(field)
    if not isinstance(value, str) or not value.strip():
        raise PreviewError(
            f'Metadata icon "{identifier}" requires a non-empty {field}.'
        )
    return value.strip()


def generate_previews(args: argparse.Namespace) -> None:
    if not args.renderer.is_file():
        raise PreviewError(f"Renderer not found at {args.renderer}.")
    metadata = read_metadata(args.metadata)
    base_ids = {
        entry_identifier(entry)
        for entry in read_base_metadata(args.base_sha)
    }
    generated_entries = [
        entry
        for entry in metadata
        if entry_identifier(entry) not in base_ids
    ]
    args.output_directory.mkdir(parents=True, exist_ok=True)
    manifest: list[dict[str, str]] = []

    for entry in generated_entries:
        identifier = entry_identifier(entry)
        name = metadata_text(entry, "Name", identifier)
        source_filename = metadata_text(entry, "Source", identifier)
        if (
            Path(source_filename).name != source_filename
            or Path(source_filename).suffix.lower() != ".svg"
        ):
            raise PreviewError(
                f'Metadata icon "{identifier}" has an invalid SVG Source.'
            )
        source_path = args.svg_directory / source_filename
        if not source_path.is_file() or source_path.is_symlink():
            raise PreviewError(
                f"SVG source not found at {source_path.as_posix()}."
            )
        preview_path = args.output_directory / f"{identifier}.png"
        subprocess.run(
            [
                str(args.renderer),
                "--width",
                "256",
                "--height",
                "256",
                "--keep-aspect-ratio",
                "--output",
                str(preview_path),
                str(source_path),
            ],
            check=True,
        )
        manifest.append(
            {
                "id": identifier,
                "name": name,
                "preview": preview_path.as_posix(),
            }
        )

    args.manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Generated {len(manifest)} icon preview(s).")


def github_request(
    method: str,
    url: str,
    token: str,
    payload: dict[str, Any] | None = None,
) -> Any:
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    request = Request(
        url,
        data=data,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "User-Agent": "ArchiveTune-IconPack",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            response_body = response.read()
    except OSError as error:
        raise PreviewError(f"GitHub API request failed: {error}") from error
    if not response_body:
        return None
    return json.loads(response_body)


def markdown_text(value: str) -> str:
    return html.escape(
        value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ")
    )


def build_comment(
    manifest: list[dict[str, str]],
    head_repository: str,
    commit_sha: str,
) -> str:
    rows = [
        COMMENT_MARKER,
        "## Generated icon previews",
        "",
        "| Icon | Preview |",
        "|:--|:--:|",
    ]
    for item in manifest:
        name = markdown_text(item["name"])
        preview_path = quote(item["preview"], safe="/")
        image_url = (
            "https://raw.githubusercontent.com/"
            f"{head_repository}/{commit_sha}/{preview_path}"
        )
        rows.append(
            f'| {name} | <img src="{image_url}" alt="{name}" '
            'width="150" height="150"> |'
        )
    return "\n".join(rows)


def post_comment(args: argparse.Namespace) -> None:
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    if not isinstance(manifest, list):
        raise PreviewError("Preview manifest must contain an array.")
    if not manifest:
        print("No generated icon previews to comment.")
        return
    for item in manifest:
        if (
            not isinstance(item, dict)
            or not all(
                isinstance(item.get(field), str)
                for field in ("id", "name", "preview")
            )
        ):
            raise PreviewError("Preview manifest contains an invalid entry.")
    if re.fullmatch(r"[0-9a-fA-F]{40}", args.commit_sha) is None:
        raise PreviewError("Generated commit SHA is invalid.")

    body = build_comment(manifest, args.head_repository, args.commit_sha)
    comments_url = (
        f"https://api.github.com/repos/{args.repository}/issues/"
        f"{args.pull_request}/comments"
    )
    authenticated_user = github_request(
        "GET",
        "https://api.github.com/user",
        args.token,
    )
    authenticated_login = (
        authenticated_user.get("login")
        if isinstance(authenticated_user, dict)
        else None
    )
    if not isinstance(authenticated_login, str) or not authenticated_login:
        raise PreviewError("Cannot determine the authenticated GitHub user.")

    existing_comment_id = None
    page = 1
    while True:
        comments = github_request(
            "GET",
            f"{comments_url}?per_page=100&page={page}",
            args.token,
        )
        if not isinstance(comments, list):
            raise PreviewError("GitHub returned an invalid comment response.")
        for comment in comments:
            comment_user = comment.get("user")
            comment_login = (
                comment_user.get("login")
                if isinstance(comment_user, dict)
                else None
            )
            if (
                comment_login == authenticated_login
                and COMMENT_MARKER in str(comment.get("body", ""))
            ):
                existing_comment_id = comment.get("id")
                break
        if existing_comment_id is not None or len(comments) < 100:
            break
        page += 1

    if existing_comment_id is None:
        github_request("POST", comments_url, args.token, {"body": body})
        print("Created generated icon preview comment.")
    else:
        github_request(
            "PATCH",
            "https://api.github.com/repos/"
            f"{args.repository}/issues/comments/{existing_comment_id}",
            args.token,
            {"body": body},
        )
        print("Updated generated icon preview comment.")


def main() -> int:
    try:
        args = parse_args()
        if args.command == "generate":
            generate_previews(args)
        else:
            post_comment(args)
    except (
        PreviewError,
        OSError,
        subprocess.CalledProcessError,
        json.JSONDecodeError,
    ) as error:
        print(f"::error::{error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
