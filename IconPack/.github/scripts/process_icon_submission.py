#!/usr/bin/env python3

import argparse
import json
import re
import secrets
import shutil
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ElementTree
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse


METADATA_START = "<!-- ICON_METADATA_START -->"
METADATA_END = "<!-- ICON_METADATA_END -->"
REQUIRED_FIELDS = ("name", "author", "file")
ALLOWED_FIELDS = frozenset((*REQUIRED_FIELDS, "link"))
SVG_NAMESPACE = "http://www.w3.org/2000/svg"
MAX_SVG_BYTES = 700_000
SVG_DOCTYPE_PATTERN = re.compile(
    rb"<!DOCTYPE\s+svg(?:\s+(?:PUBLIC|SYSTEM)\s+"
    rb"(?:\"[^\"]*\"|'[^']*')"
    rb"(?:\s+(?:\"[^\"]*\"|'[^']*'))?)?\s*>",
    re.IGNORECASE,
)
MAX_FILENAME_COMPONENT_LENGTH = 48


class SubmissionError(Exception):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        choices=("pull-request", "rebuild", "update"),
        required=True,
    )
    parser.add_argument("--event-path", type=Path)
    parser.add_argument("--base-sha")
    parser.add_argument("--random-digits", type=int, choices=(6,), default=6)
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


def read_pr_body(event_path: Path) -> str:
    try:
        event = json.loads(event_path.read_text(encoding="utf-8"))
        body = event["pull_request"]["body"]
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as error:
        raise SubmissionError(
            f"Cannot read the pull request body: {error}"
        ) from error
    if not isinstance(body, str):
        raise SubmissionError("The pull request body is empty.")
    return body


def read_pr_author_url(event_path: Path) -> str:
    try:
        event = json.loads(event_path.read_text(encoding="utf-8"))
        login = event["pull_request"]["user"]["login"]
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as error:
        raise SubmissionError(
            f"Cannot read the pull request author: {error}"
        ) from error
    if not isinstance(login, str) or not re.fullmatch(
        r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})",
        login,
    ):
        raise SubmissionError("The pull request author login is invalid.")
    return f"https://github.com/{login}"


def parse_form(body: str) -> list[dict[str, str]]:
    start = body.find(METADATA_START)
    end = body.find(METADATA_END)
    markers_missing = start < 0 and end < 0
    if not markers_missing and (start < 0 or end < 0 or end <= start):
        raise SubmissionError(
            "The icon metadata markers are missing or out of order."
        )

    if markers_missing:
        json_content = body.strip()
    else:
        section = body[start + len(METADATA_START) : end].strip()
        fenced = re.fullmatch(r"```json\s*(.*?)\s*```", section, re.DOTALL)
        if fenced is None:
            raise SubmissionError(
                "Icon metadata must remain inside the template's JSON block."
            )
        json_content = fenced.group(1)

    try:
        form = json.loads(json_content)
    except json.JSONDecodeError as error:
        raise SubmissionError(
            f"Icon metadata is not valid JSON: {error}"
        ) from error
    if not isinstance(form, dict) or set(form) != {"icons"}:
        raise SubmissionError('Icon metadata must contain only an "icons" array.')
    raw_icons = form["icons"]
    if not isinstance(raw_icons, list) or not raw_icons:
        raise SubmissionError(
            'The "icons" array must contain at least one icon.'
        )

    icons: list[dict[str, str]] = []
    seen_files: set[str] = set()
    for index, raw_icon in enumerate(raw_icons, start=1):
        if not isinstance(raw_icon, dict):
            raise SubmissionError(f"Icon #{index} must be a JSON object.")
        unknown_fields = set(raw_icon) - ALLOWED_FIELDS
        if unknown_fields:
            fields = ", ".join(sorted(unknown_fields))
            raise SubmissionError(
                f"Icon #{index} contains unsupported fields: {fields}."
            )

        icon: dict[str, str] = {}
        for field in REQUIRED_FIELDS:
            value = raw_icon.get(field)
            if not isinstance(value, str) or not value.strip():
                raise SubmissionError(
                    f'Icon #{index} requires a non-empty "{field}".'
                )
            icon[field] = value.strip()
        if len(icon["name"]) > 120 or len(icon["author"]) > 120:
            raise SubmissionError(
                f"Icon #{index} name and author must not exceed 120 characters."
            )

        link = raw_icon.get("link", "")
        if not isinstance(link, str):
            raise SubmissionError(
                f'Icon #{index} field "link" must be a string.'
            )
        icon["link"] = link.strip()
        if icon["link"]:
            parsed_link = urlparse(icon["link"])
            if parsed_link.scheme not in {"http", "https"} or not parsed_link.netloc:
                raise SubmissionError(
                    f'Icon #{index} field "link" must be an HTTP(S) URL or empty.'
                )

        file_path = PurePosixPath(icon["file"])
        if (
            file_path.is_absolute()
            or len(file_path.parts) != 2
            or file_path.parts[0] != "submissions"
            or file_path.suffix.lower() != ".svg"
            or file_path.name in {".svg", ".."}
        ):
            raise SubmissionError(
                f'Icon #{index} file must be "submissions/<filename>.svg".'
            )
        normalized_file = file_path.as_posix()
        if normalized_file in seen_files:
            raise SubmissionError(
                f'Duplicate file in metadata: "{normalized_file}".'
            )
        seen_files.add(normalized_file)
        icon["file"] = normalized_file
        icons.append(icon)

    return icons


def changed_paths(base_sha: str) -> list[tuple[str, str]]:
    output = run(
        "git",
        "diff",
        "--name-status",
        "--no-renames",
        f"{base_sha}...HEAD",
    )
    changes: list[tuple[str, str]] = []
    for line in output.splitlines():
        if line:
            status, path = line.split("\t", maxsplit=1)
            changes.append((status, path))
    return changes


def validate_pr_changes(
    changes: list[tuple[str, str]],
    icons: list[dict[str, str]],
) -> bool:
    submitted_files = {icon["file"] for icon in icons}
    actual_submissions = {
        path for _, path in changes if path.startswith("submissions/")
    }

    if not actual_submissions:
        generated_change = any(
            path == "metadata.json" or path.startswith("svg/")
            for _, path in changes
        )
        if generated_change:
            print("No unprocessed SVG submissions remain; nothing to do.")
            return False
        raise SubmissionError("No SVG files were added under submissions/.")

    invalid_changes = [
        f"{status} {path}"
        for status, path in changes
        if status != "A" or path not in submitted_files
    ]
    if invalid_changes:
        formatted = "\n  ".join(invalid_changes)
        raise SubmissionError(
            "An icon submission PR may initially add only the SVG files listed "
            f"in its metadata:\n  {formatted}"
        )
    if actual_submissions != submitted_files:
        missing = submitted_files - actual_submissions
        unlisted = actual_submissions - submitted_files
        details = []
        if missing:
            details.append(f"missing: {', '.join(sorted(missing))}")
        if unlisted:
            details.append(f"not listed: {', '.join(sorted(unlisted))}")
        raise SubmissionError("SVG file mismatch (" + "; ".join(details) + ").")
    return True


def validate_svg(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        raise SubmissionError(f"{path.as_posix()} must be a regular file.")
    size = path.stat().st_size
    if size == 0 or size > MAX_SVG_BYTES:
        raise SubmissionError(
            f"{path.as_posix()} must be non-empty and no larger than "
            f"{MAX_SVG_BYTES // 1_000} KB."
        )
    svg_bytes = path.read_bytes()
    if svg_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SubmissionError(
            f"{path.as_posix()} contains PNG data. Export and submit an actual "
            "SVG XML file instead of renaming a PNG file."
        )
    upper_svg_bytes = svg_bytes.upper()
    if b"<!ENTITY" in upper_svg_bytes:
        raise SubmissionError(f"{path.as_posix()} must not declare an entity.")
    sanitized_svg_bytes, doctype_count = SVG_DOCTYPE_PATTERN.subn(
        b"",
        svg_bytes,
        count=1,
    )
    if b"<!DOCTYPE" in sanitized_svg_bytes.upper():
        raise SubmissionError(
            f"{path.as_posix()} contains an unsupported DTD declaration."
        )
    try:
        root = ElementTree.fromstring(sanitized_svg_bytes)
    except ElementTree.ParseError as error:
        raise SubmissionError(
            f"{path.as_posix()} is not valid XML: {error}"
        ) from error
    if root.tag not in {f"{{{SVG_NAMESPACE}}}svg", "svg"}:
        raise SubmissionError(
            f"{path.as_posix()} does not have an SVG root element."
        )
    for element in root.iter():
        local_tag = element.tag.rsplit("}", maxsplit=1)[-1].lower()
        if local_tag in {"script", "foreignobject"}:
            raise SubmissionError(
                f"{path.as_posix()} contains unsupported active SVG content."
            )
        for attribute, value in element.attrib.items():
            if (
                attribute.rsplit("}", maxsplit=1)[-1].lower() == "href"
                and value
                and not value.startswith("#")
            ):
                raise SubmissionError(
                    f"{path.as_posix()} contains an external SVG reference."
                )
    if doctype_count:
        path.write_bytes(sanitized_svg_bytes)


def read_metadata(path: Path) -> list[dict[str, Any]]:
    try:
        metadata = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SubmissionError(f"Cannot read metadata.json: {error}") from error
    if not isinstance(metadata, list) or not all(
        isinstance(entry, dict) for entry in metadata
    ):
        raise SubmissionError("metadata.json must be an array of objects.")
    return metadata


def read_metadata_at_revision(revision: str) -> list[dict[str, Any]]:
    try:
        metadata = json.loads(run("git", "show", f"{revision}:metadata.json"))
    except (json.JSONDecodeError, subprocess.CalledProcessError) as error:
        raise SubmissionError(
            f"Cannot read metadata.json at revision {revision}: {error}"
        ) from error
    if not isinstance(metadata, list) or not all(
        isinstance(entry, dict) for entry in metadata
    ):
        raise SubmissionError(
            f"metadata.json at revision {revision} must be an array of objects."
        )
    return metadata


def workflow_message(value: str) -> str:
    return (
        value.replace("%", "%25")
        .replace("\r", "%0D")
        .replace("\n", "%0A")
    )


def warn_duplicate_authors(
    icons: list[dict[str, str]],
    metadata: list[dict[str, Any]],
) -> None:
    existing_authors = {
        entry["Author"]
        for entry in metadata
        if isinstance(entry.get("Author"), str)
    }
    duplicate_authors = sorted(
        {
            icon["author"]
            for icon in icons
            if icon["author"] in existing_authors
        }
    )
    for author in duplicate_authors:
        message = workflow_message(
            f'Author "{author}" already exists in metadata.json. '
            "Confirm that this submission uses the intended author name."
        )
        print(f"::warning title=Duplicate author name::{message}")


def used_ids(metadata: list[dict[str, Any]], random_digits: int) -> set[str]:
    identifier_pattern = re.compile(rf"\d{{{random_digits}}}")
    filename_pattern = re.compile(rf"_(\d{{{random_digits}}})(?:\.|$)")
    identifiers = {
        str(entry["Id"])
        for entry in metadata
        if isinstance(entry.get("Id"), (str, int))
        and identifier_pattern.fullmatch(str(entry["Id"]))
    }
    svg_directory = Path("svg")
    for path in svg_directory.iterdir() if svg_directory.is_dir() else ():
        match = filename_pattern.search(path.name)
        if match:
            identifiers.add(match.group(1))
    return identifiers


def next_id(existing: set[str], random_digits: int) -> str:
    lower_bound = 10 ** (random_digits - 1)
    available_identifiers = 9 * lower_bound
    if len(existing) >= available_identifiers:
        raise SubmissionError(
            f"All {random_digits}-digit icon IDs are already in use."
        )
    while True:
        identifier = str(
            secrets.randbelow(available_identifiers) + lower_bound
        )
        if identifier not in existing:
            existing.add(identifier)
            return identifier


def svg_filename_component(value: str, fallback: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_value = normalized.encode("ascii", "ignore").decode("ascii")
    component = re.sub(
        r"[^a-z0-9]+",
        "_",
        ascii_value.lower(),
    ).strip("_")
    return component[:MAX_FILENAME_COMPONENT_LENGTH].rstrip("_") or fallback


def svg_asset_stem(name: str, author: str, identifier: str) -> str:
    icon_name = svg_filename_component(name, "icon")
    author_name = svg_filename_component(author, "author")
    return f"{icon_name}_{author_name}_{identifier}"


def metadata_by_id(
    metadata: list[dict[str, Any]],
    source: str,
) -> dict[str, dict[str, Any]]:
    entries_by_id: dict[str, dict[str, Any]] = {}
    for index, entry in enumerate(metadata, start=1):
        identifier = entry.get("Id")
        if not isinstance(identifier, (str, int)):
            raise SubmissionError(f"{source} entry #{index} requires an Id.")
        normalized_identifier = str(identifier)
        if re.fullmatch(r"\d{4}|\d{6}", normalized_identifier) is None:
            raise SubmissionError(
                f'{source} entry #{index} has invalid Id '
                f'"{normalized_identifier}".'
            )
        if normalized_identifier in entries_by_id:
            raise SubmissionError(
                f'{source} contains duplicate Id "{normalized_identifier}".'
            )
        entries_by_id[normalized_identifier] = entry
    return entries_by_id


def metadata_text(
    entry: dict[str, Any],
    field: str,
    identifier: str,
) -> str:
    value = entry.get(field)
    if not isinstance(value, str) or not value.strip():
        raise SubmissionError(
            f'Metadata entry with Id "{identifier}" requires a {field}.'
        )
    return value.strip()


def source_filename(entry: dict[str, Any], identifier: str) -> str:
    value = entry.get("Source")
    if (
        not isinstance(value, str)
        or not value
        or Path(value).name != value
        or Path(value).suffix.lower() != ".svg"
    ):
        raise SubmissionError(
            f'Metadata entry with Id "{identifier}" requires a valid SVG Source.'
        )
    return value


def current_svg_path(entry: dict[str, Any], identifier: str) -> Path:
    declared_path = Path("svg") / source_filename(entry, identifier)
    if declared_path.is_file() and not declared_path.is_symlink():
        return declared_path
    svg_directory = Path("svg")
    candidates = [
        path
        for path in svg_directory.iterdir() if svg_directory.is_dir()
        if path.is_file()
        and not path.is_symlink()
        and path.suffix.lower() == ".svg"
        and path.stem.endswith(f"_{identifier}")
    ]
    if not candidates:
        raise SubmissionError(
            f'Cannot find current SVG asset for Id "{identifier}".'
        )
    if len(candidates) > 1:
        filenames = ", ".join(sorted(path.name for path in candidates))
        raise SubmissionError(
            f'Multiple SVG assets match Id "{identifier}": {filenames}.'
        )
    return candidates[0]


def synchronize_metadata_assets(
    metadata: list[dict[str, Any]],
    base_metadata: list[dict[str, Any]],
) -> int:
    entries_by_id = metadata_by_id(metadata, "metadata.json")
    base_entries_by_id = metadata_by_id(
        base_metadata,
        "base metadata.json",
    )
    synchronized_count = 0

    for identifier, entry in entries_by_id.items():
        entry.pop("Filename", None)
        if identifier not in base_entries_by_id:
            continue
        name = metadata_text(entry, "Name", identifier)
        author = metadata_text(entry, "Author", identifier)
        current_path = current_svg_path(entry, identifier)
        expected_filename = f"{svg_asset_stem(name, author, identifier)}.svg"
        expected_path = Path("svg") / expected_filename
        if expected_path != current_path:
            if expected_path.exists():
                raise SubmissionError(
                    f"Cannot rename {current_path.as_posix()} because "
                    f"{expected_path.as_posix()} already exists."
                )
            current_path.rename(expected_path)
            synchronized_count += 1
        if entry.get("Source") != expected_filename:
            entry["Source"] = expected_filename
            synchronized_count += 1
        entry["Id"] = identifier
        entry["Name"] = name
        entry["Author"] = author

    return synchronized_count


def process_pull_request(args: argparse.Namespace) -> None:
    if args.event_path is None or not args.base_sha:
        raise SubmissionError(
            "Pull-request mode requires --event-path and --base-sha."
        )
    changes = changed_paths(args.base_sha)
    if not any(path.startswith("submissions/") for _, path in changes):
        if not any(path == "metadata.json" for _, path in changes):
            print("No unprocessed SVG submissions remain; nothing to do.")
            return
        metadata_path = Path("metadata.json")
        metadata = read_metadata(metadata_path)
        synchronized_count = synchronize_metadata_assets(
            metadata,
            read_metadata_at_revision(args.base_sha),
        )
        metadata_path.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Synchronized {synchronized_count} SVG asset change(s).")
        return

    icons = parse_form(read_pr_body(args.event_path))
    if not validate_pr_changes(changes, icons):
        return
    github_author_url = read_pr_author_url(args.event_path)
    metadata_path = Path("metadata.json")
    metadata = read_metadata(metadata_path)
    base_metadata = read_metadata_at_revision(args.base_sha)
    warn_duplicate_authors(icons, base_metadata)
    identifiers = used_ids(metadata, args.random_digits)
    Path("svg").mkdir(exist_ok=True)

    pending_assets: list[tuple[Path, Path, dict[str, Any]]] = []
    for icon in icons:
        submission = Path(icon["file"])
        validate_svg(submission)
        identifier = next_id(identifiers, args.random_digits)
        destination_filename = (
            f"{svg_asset_stem(icon['name'], icon['author'], identifier)}.svg"
        )
        destination = Path("svg") / destination_filename
        if destination.exists():
            raise SubmissionError(
                f"{destination.as_posix()} already exists."
            )
        pending_assets.append(
            (
                submission,
                destination,
                {
                    "Id": identifier,
                    "Name": icon["name"],
                    "Author": icon["author"],
                    "Source": destination_filename,
                    "Submission": icon["file"],
                    "Link": icon["link"],
                    "GitHubAuthorUrl": github_author_url,
                },
            )
        )

    for submission, destination, entry in pending_assets:
        shutil.move(submission, destination)
        metadata.append(entry)
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Stored {len(pending_assets)} submitted SVG icon(s).")


def metadata_matches_submission(
    entry: dict[str, Any],
    submission: Path,
) -> bool:
    identifier = str(entry.get("Id", ""))
    source = entry.get("Source")
    original_submission = entry.get("Submission")
    return (
        original_submission == submission.as_posix()
        or source == submission.name
        or (
            re.fullmatch(r"\d{4}|\d{6}", identifier) is not None
            and submission.name == f"{identifier}.svg"
        )
    )


def remove_unreferenced_svg_files(metadata: list[dict[str, Any]]) -> int:
    referenced_files = {
        source_filename(entry, str(entry["Id"]))
        for entry in metadata
    }
    removed_count = 0
    svg_directory = Path("svg")
    if svg_directory.is_dir():
        for path in svg_directory.iterdir():
            if path.name == ".gitkeep":
                continue
            if path.is_dir() and not path.is_symlink():
                continue
            if path.name not in referenced_files:
                path.unlink()
                removed_count += 1
    return removed_count


def rebuild_existing_icons(args: argparse.Namespace) -> None:
    metadata_path = Path("metadata.json")
    metadata = read_metadata(metadata_path)
    metadata_by_id(metadata, "metadata.json")
    submissions = sorted(Path("submissions").glob("*.svg"))
    rebuilt_count = 0

    for submission in submissions:
        matches = [
            entry
            for entry in metadata
            if metadata_matches_submission(entry, submission)
        ]
        if not matches:
            raise SubmissionError(
                f"{submission.as_posix()} has no matching metadata entry."
            )
        if len(matches) > 1:
            raise SubmissionError(
                f"{submission.as_posix()} matches multiple metadata entries."
            )
        entry = matches[0]
        identifier = str(entry["Id"])
        name = metadata_text(entry, "Name", identifier)
        author = metadata_text(entry, "Author", identifier)
        validate_svg(submission)
        expected_filename = f"{svg_asset_stem(name, author, identifier)}.svg"
        expected_path = Path("svg") / expected_filename
        previous_path = current_svg_path(entry, identifier)
        if expected_path != previous_path and expected_path.exists():
            raise SubmissionError(
                f"{expected_path.as_posix()} already exists."
            )
        expected_path.parent.mkdir(exist_ok=True)
        shutil.move(submission, expected_path)
        if previous_path != expected_path:
            previous_path.unlink(missing_ok=True)
        entry.pop("Filename", None)
        entry["Id"] = identifier
        entry["Name"] = name
        entry["Author"] = author
        entry["Source"] = expected_filename
        rebuilt_count += 1

    update_count, removed_count = reconcile_existing_icons(metadata)
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Rebuilt {rebuilt_count} SVG icon(s), synchronized "
        f"{update_count} metadata asset(s), and removed "
        f"{removed_count} unreferenced SVG file(s)."
    )


def reconcile_existing_icons(
    metadata: list[dict[str, Any]],
) -> tuple[int, int]:
    entries_by_id = metadata_by_id(metadata, "metadata.json")
    expected_filenames: set[str] = set()
    synchronized_count = 0

    for identifier, entry in entries_by_id.items():
        name = metadata_text(entry, "Name", identifier)
        author = metadata_text(entry, "Author", identifier)
        current_path = current_svg_path(entry, identifier)
        expected_filename = f"{svg_asset_stem(name, author, identifier)}.svg"
        if expected_filename in expected_filenames:
            raise SubmissionError(
                f'Duplicate generated SVG filename "{expected_filename}".'
            )
        expected_filenames.add(expected_filename)
        expected_path = Path("svg") / expected_filename
        if expected_path != current_path:
            if expected_path.exists():
                raise SubmissionError(
                    f"Cannot rename {current_path.as_posix()} because "
                    f"{expected_path.as_posix()} already exists."
                )
            current_path.rename(expected_path)
            synchronized_count += 1
        if entry.get("Source") != expected_filename:
            entry["Source"] = expected_filename
            synchronized_count += 1
        if "Filename" in entry:
            entry.pop("Filename")
            synchronized_count += 1
        entry["Id"] = identifier
        entry["Name"] = name
        entry["Author"] = author

    removed_count = remove_unreferenced_svg_files(metadata)
    return synchronized_count, removed_count


def update_existing_icons() -> None:
    metadata_path = Path("metadata.json")
    metadata = read_metadata(metadata_path)
    synchronized_count, removed_count = reconcile_existing_icons(metadata)
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Updated {len(metadata)} SVG icon(s), synchronized "
        f"{synchronized_count} metadata asset(s), and removed "
        f"{removed_count} unreferenced SVG file(s)."
    )


def process(args: argparse.Namespace) -> None:
    if args.mode == "pull-request":
        process_pull_request(args)
    elif args.mode == "rebuild":
        rebuild_existing_icons(args)
    else:
        update_existing_icons()


def main() -> int:
    try:
        process(parse_args())
    except (SubmissionError, subprocess.CalledProcessError, OSError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
