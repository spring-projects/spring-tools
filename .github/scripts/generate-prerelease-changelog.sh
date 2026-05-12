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

# Function to determine the date of the previous change we should consider
get_last_update_date() {
    local date_changelog=""
    local date_tag=""
    local final_date=""
    
    # 1. Find the last commit that modified the CHANGELOG_FILE with a commit message starting with "Update pre-release changelog"
    date_changelog=$(git log -1 --grep="^Update pre-release changelog" --format="%aI" -- "$CHANGELOG_FILE")
    
    # 2. Find the latest git tag that starts with the extension ID (skipping non-zero patch versions)
    local latest_tag=$(git tag -l "${EXTENSION_ID}-*" --sort=-v:refname | grep -E "^${EXTENSION_ID}-[0-9]+\.[0-9]+\.0(-|$)" | head -n 1)
    if [ -n "$latest_tag" ]; then
        date_tag=$(git log -1 --format="%aI" "$latest_tag")
    fi
    
    # 3. If both are present, use the later of the dates
    if [ -n "$date_changelog" ] && [ -n "$date_tag" ]; then
        # Use node to compare ISO 8601 dates reliably across platforms
        local later_date=$(node -e "console.log(new Date('$date_changelog') > new Date('$date_tag') ? '$date_changelog' : '$date_tag')")
        final_date="$later_date"
    elif [ -n "$date_changelog" ]; then
        final_date="$date_changelog"
    elif [ -n "$date_tag" ]; then
        final_date="$date_tag"
    fi
    
    if [ -n "$final_date" ]; then
        echo "$final_date"
        return
    fi
    
    # 4. If there are no dates, use VSCode Marketplace
    local publisher=$(jq -r '.publisher' "$PACKAGE_JSON")
    if [ -n "$publisher" ] && [ "$publisher" != "null" ]; then
        echo "Querying VSCode Marketplace for the last non-prerelease version of ${publisher}.${EXTENSION_ID}..." >&2
        local date_marketplace=$(npx --yes @vscode/vsce show "${publisher}.${EXTENSION_ID}" --json 2>/dev/null | jq -r '[.versions[] | select((.properties == null or all(.properties[]; .key != "Microsoft.VisualStudio.Code.PreRelease" or .value != "true")) and (.version | test("^[0-9]+\\.[0-9]+\\.0$")))] | .[0] | .lastUpdated')
        
        if [ -n "$date_marketplace" ] && [ "$date_marketplace" != "null" ]; then
            echo "$date_marketplace"
            return
        fi
    fi
    
    # 5. Still no dates? Keep date blank
    echo ""
}

# Function to fetch and format issues from GitHub
fetch_and_format_issues() {
    local since_date="$1"
    local query="is:closed $SEARCH_LABELS"
    
    if [ -n "$since_date" ]; then
        query="is:closed closed:>=$since_date $SEARCH_LABELS"
        echo "Fetching issues closed since: $since_date" >&2
    else
        echo "No previous update found. Fetching all relevant closed issues." >&2
    fi
    
    local issues_json=$(gh issue list --repo "$REPO" --search "$query" --json number,title,url --limit 100)
    
    # Using jq to parse the JSON output, filter out titles starting with [backport, and format each issue
    echo "$issues_json" | jq -r '
      .[] 
      | select(.title | test("^\\s*\\[\\s*backport"; "i") | not) 
      | "* _('"$PREFIX_NAME"')_ \(.title) [#\(.number)](\(.url))"
    '
}

# Function to update the changelog file
update_changelog() {
    local formatted_issues="$1"
    local version=$(jq -r '.version' "$PACKAGE_JSON")
    local current_date=$(date +"%Y-%m-%d")
    local header="## $current_date ($version PRE-RELEASE)"
    
    local temp_file=$(mktemp)
    
    # Check if the exact header already exists in the file
    if grep -q "^$header$" "$CHANGELOG_FILE"; then
        echo "Pre-release section for $current_date ($version PRE-RELEASE) already exists. Prepending new issues." >&2
        
        # Find the line number of the header
        local header_line=$(grep -n "^$header$" "$CHANGELOG_FILE" | cut -d: -f1)
        
        # Find the line number of the "#### all fixes and improvements in detail" that comes after the header
        local details_offset=$(tail -n +$header_line "$CHANGELOG_FILE" | grep -n "^#### all fixes and improvements in detail$" | head -n 1 | cut -d: -f1)
        
        if [ -n "$details_offset" ]; then
            local insert_line=$((header_line + details_offset))
            
            # Copy everything up to the insert line
            head -n "$insert_line" "$CHANGELOG_FILE" > "$temp_file"
            echo "" >> "$temp_file"
            # Insert the new issues
            echo "$formatted_issues" >> "$temp_file"
            
            # Copy the rest of the file, skipping the blank line immediately following if it exists
            tail -n +$((insert_line + 1)) "$CHANGELOG_FILE" | awk 'NR==1 && /^$/ {next} {print}' >> "$temp_file"
        else
            # Fallback if "#### all fixes and improvements in detail" is missing under the header
            head -n "$header_line" "$CHANGELOG_FILE" > "$temp_file"
            echo "" >> "$temp_file"
            echo "#### all fixes and improvements in detail" >> "$temp_file"
            echo "" >> "$temp_file"
            echo "$formatted_issues" >> "$temp_file"
            echo "" >> "$temp_file"
            tail -n +$((header_line + 1)) "$CHANGELOG_FILE" >> "$temp_file"
        fi
        
        # Deduplicate issues in the section we just modified
        local next_header_offset=$(tail -n +$((header_line + 1)) "$temp_file" | grep -n "^## " | head -n 1 | cut -d: -f1)
        local end_line
        
        if [ -n "$next_header_offset" ]; then
            end_line=$((header_line + next_header_offset - 1))
        else
            end_line=$(wc -l < "$temp_file")
        fi
        
        local dedup_temp=$(mktemp)
        
        # 1. Copy everything before the issues section
        head -n "$insert_line" "$temp_file" > "$dedup_temp"
        echo "" >> "$dedup_temp"
        
        # 2. Extract, deduplicate, and append the issues section
        sed -n "$((insert_line + 1)),${end_line}p" "$temp_file" | awk '!seen[$0]++' | grep -v "^$" >> "$dedup_temp"
        echo "" >> "$dedup_temp"
        
        # 3. Copy everything after the issues section
        if [ -n "$next_header_offset" ]; then
            tail -n +$((end_line + 1)) "$temp_file" >> "$dedup_temp"
        fi
        
        mv "$dedup_temp" "$temp_file"
        
    else
        echo "Creating new pre-release section for $current_date ($version PRE-RELEASE)." >&2
        # Prepend the new header and the formatted list of issues to CHANGELOG.md
        echo "$header" > "$temp_file"
        echo "" >> "$temp_file"
        echo "#### all fixes and improvements in detail" >> "$temp_file"
        echo "" >> "$temp_file"
        echo "$formatted_issues" >> "$temp_file"
        echo "" >> "$temp_file"
        cat "$CHANGELOG_FILE" >> "$temp_file"
    fi
    
    mv "$temp_file" "$CHANGELOG_FILE"
    echo "Successfully updated $CHANGELOG_FILE with pre-release changelog." >&2
}

# --- Main Execution ---

LAST_UPDATE_DATE=$(get_last_update_date)
FORMATTED_ISSUES=$(fetch_and_format_issues "$LAST_UPDATE_DATE")

if [ -z "$FORMATTED_ISSUES" ]; then
    echo "No relevant issues found."
    exit 0
fi

update_changelog "$FORMATTED_ISSUES"
