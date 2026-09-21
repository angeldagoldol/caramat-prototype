#!/usr/bin/env bash
# Export the Chapter I-II paper to PDF, which is the format the laboratory examination asks for.
#
# Requires LibreOffice Writer:  sudo apt-get install libreoffice-writer
# Usage:  ./scripts/export-pdf.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCX="$ROOT/deliverables/Implementation_of_a_Priority_Queue_Max_Heap_in_an_Online_Scholarship_Application_System_Chapters_I_II.docx"

if [ ! -f "$DOCX" ]; then
  echo "Build the document first: python3 scripts/build_chapters_1_2_docx.py" >&2
  exit 1
fi

if ! command -v soffice >/dev/null 2>&1; then
  echo "LibreOffice Writer is required. Install it with: sudo apt-get install libreoffice-writer" >&2
  exit 1
fi

PROFILE="$(mktemp -d)"
trap 'rm -rf "$PROFILE"' EXIT

soffice --headless \
  -env:UserInstallation="file://$PROFILE" \
  --convert-to pdf \
  --outdir "$ROOT/deliverables" \
  "$DOCX" >/dev/null

echo "Created ${DOCX%.docx}.pdf"
