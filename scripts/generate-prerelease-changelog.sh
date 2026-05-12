#!/bin/bash

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <extension-id> [search-labels]"
    echo "Example: $0 vscode-spring-boot 'label:\"for: vscode\"'"
    exit 1
fi

EXTENSION_ID="$1"
SEARCH_LABELS="$2"

REPO="spring-projects/spring-tools"
CHANGELOG_FILE="vscode-extensions/$EXTENSION_ID/CHANGELOG.md"
PACKAGE_JSON="vscode-extensions/$EXTENSION_ID/package.json"

if [ ! -f "$CHANGELOG_FILE" ]; then
    echo "Error: $CHANGELOG_FILE does not exist."
    exit 1
fi

# Derive the prefix name (e.g., vscode-spring-boot -> Spring Boot)
RAW_NAME=${EXTENSION_ID#vscode-}
PREFIX_NAME=$(echo "$RAW_NAME" | awk '{for(i=1;i<=NF;i++)sub(/./,toupper(substr($i,1,1)),$i)}1' FS="-" OFS=" ")

# 1. Determine the Last Update Date
# Find the latest git tag that starts with the extension ID
LATEST_TAG=$(git tag -l "${EXTENSION_ID}-*" --sort=-v:refname | head -n 1)

if [ -n "$LATEST_TAG" ]; then
    echo "Found latest release tag: $LATEST_TAG"
    # Get the commit date of the tag
    LAST_UPDATE_DATE=$(git log -1 --format="%aI" "$LATEST_TAG")
else
    echo "Could not find a previous release tag for $EXTENSION_ID."
    
    # Fallback 1: Find the last commit that modified the CHANGELOG_FILE with a commit message starting with "Update pre-release changelog"
    LAST_UPDATE_DATE=$(git log -1 --grep="^Update pre-release changelog" --format="%aI" -- "$CHANGELOG_FILE")

    if [ -z "$LAST_UPDATE_DATE" ]; then
        echo "Could not find a previous 'Update pre-release changelog' commit for $CHANGELOG_FILE."
        echo "Falling back to the last modification date of the file."
        LAST_UPDATE_DATE=$(git log -1 --format="%aI" -- "$CHANGELOG_FILE")
        
        if [ -z "$LAST_UPDATE_DATE" ]; then
            echo "Could not determine the last update date for $CHANGELOG_FILE"
            exit 1
        fi
    fi
fi

echo "Fetching issues closed since: $LAST_UPDATE_DATE"

# 2. Fetch Closed Issues via GitHub CLI (gh)
SEARCH_QUERY="is:closed closed:>=$LAST_UPDATE_DATE $SEARCH_LABELS"

ISSUES_JSON=$(gh issue list --repo "$REPO" --search "$SEARCH_QUERY" --json number,title,url --limit 100)

# 3. Format the Issues and exclude backports
# Using jq to parse the JSON output, filter out titles starting with [backport, and format each issue
FORMATTED_ISSUES=$(echo "$ISSUES_JSON" | jq -r '
  .[] 
  | select(.title | test("^\\s*\\[\\s*backport"; "i") | not) 
  | "* _('"$PREFIX_NAME"')_ \(.title) [#\(.number)](\(.url))"
')

if [ -z "$FORMATTED_ISSUES" ]; then
    echo "No relevant issues found since $LAST_UPDATE_DATE."
    exit 0
fi

# 4. Generate the Header
# Extract the current version from package.json
VERSION=$(jq -r '.version' "$PACKAGE_JSON")
CURRENT_DATE=$(date +"%Y-%m-%d")
HEADER="## $CURRENT_DATE ($VERSION PRE-RELEASE)"

# 5. Update the Changelog
TEMP_FILE=$(mktemp)

# Check if the exact header already exists in the file
if grep -q "^$HEADER$" "$CHANGELOG_FILE"; then
    echo "Pre-release section for $CURRENT_DATE ($VERSION PRE-RELEASE) already exists. Prepending new issues."
    
    # We need to insert the new issues right after the "#### all fixes and improvements in detail" line
    # that follows our header.
    
    # Find the line number of the header
    HEADER_LINE=$(grep -n "^$HEADER$" "$CHANGELOG_FILE" | cut -d: -f1)
    
    # Find the line number of the "#### all fixes and improvements in detail" that comes after the header
    # We look at the file starting from the header line
    DETAILS_OFFSET=$(tail -n +$HEADER_LINE "$CHANGELOG_FILE" | grep -n "^#### all fixes and improvements in detail$" | head -n 1 | cut -d: -f1)
    
    if [ -n "$DETAILS_OFFSET" ]; then
        INSERT_LINE=$((HEADER_LINE + DETAILS_OFFSET))
        
        # Copy everything up to the insert line
        head -n "$INSERT_LINE" "$CHANGELOG_FILE" > "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        # Insert the new issues
        echo "$FORMATTED_ISSUES" >> "$TEMP_FILE"
        
        # Copy the rest of the file, skipping the blank line immediately following if it exists
        # to avoid accumulating blank lines when we run this multiple times
        tail -n +$((INSERT_LINE + 1)) "$CHANGELOG_FILE" | awk 'NR==1 && /^$/ {next} {print}' >> "$TEMP_FILE"
    else
        # Fallback if "#### all fixes and improvements in detail" is missing under the header
        # Just insert right after the header
        head -n "$HEADER_LINE" "$CHANGELOG_FILE" > "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        echo "#### all fixes and improvements in detail" >> "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        echo "$FORMATTED_ISSUES" >> "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        tail -n +$((HEADER_LINE + 1)) "$CHANGELOG_FILE" >> "$TEMP_FILE"
    fi
    
    # Deduplicate issues in the section we just modified
    # We only want to deduplicate the issues list, not the whole file
    # Extract the issues part between the header and the next header
    NEXT_HEADER_OFFSET=$(tail -n +$((HEADER_LINE + 1)) "$TEMP_FILE" | grep -n "^## " | head -n 1 | cut -d: -f1)
    
    if [ -n "$NEXT_HEADER_OFFSET" ]; then
        END_LINE=$((HEADER_LINE + NEXT_HEADER_OFFSET - 1))
    else
        END_LINE=$(wc -l < "$TEMP_FILE")
    fi
    
    # Create a temporary file for the deduplicated content
    DEDUP_TEMP=$(mktemp)
    
    # 1. Copy everything before the issues section
    head -n "$INSERT_LINE" "$TEMP_FILE" > "$DEDUP_TEMP"
    echo "" >> "$DEDUP_TEMP"
    
    # 2. Extract, deduplicate, and append the issues section
    # Use awk to keep only the first occurrence of each line
    sed -n "$((INSERT_LINE + 1)),${END_LINE}p" "$TEMP_FILE" | awk '!seen[$0]++' | grep -v "^$" >> "$DEDUP_TEMP"
    echo "" >> "$DEDUP_TEMP"
    
    # 3. Copy everything after the issues section
    if [ -n "$NEXT_HEADER_OFFSET" ]; then
        tail -n +$((END_LINE + 1)) "$TEMP_FILE" >> "$DEDUP_TEMP"
    fi
    
    mv "$DEDUP_TEMP" "$TEMP_FILE"
    
else
    echo "Creating new pre-release section for $CURRENT_DATE ($VERSION PRE-RELEASE)."
    # Prepend the new header and the formatted list of issues to CHANGELOG.md
    echo "$HEADER" > "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"
    echo "#### all fixes and improvements in detail" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"
    echo "$FORMATTED_ISSUES" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"
    cat "$CHANGELOG_FILE" >> "$TEMP_FILE"
fi

mv "$TEMP_FILE" "$CHANGELOG_FILE"

echo "Successfully updated $CHANGELOG_FILE with pre-release changelog."
