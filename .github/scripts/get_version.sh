#!/usr/bin/env bash
set -e

git fetch --tags
# Get all version numbers from tags
TAG_VERSIONS=$(git tag -l 'v*' | sed 's/^v//')

MAX_VERSION_CODE=0
for VERSION in $TAG_VERSIONS; do
  IFS='.' read -r TAG_MAJOR TAG_MINOR TAG_PATCH <<< "$VERSION"
  TAG_MAJOR=${TAG_MAJOR:-0}
  TAG_MINOR=${TAG_MINOR:-0}
  TAG_PATCH=${TAG_PATCH:-0}
  TAG_VERSION_CODE=$((TAG_MAJOR * 10000 + TAG_MINOR * 100 + TAG_PATCH))
  if [ "$TAG_VERSION_CODE" -gt "$MAX_VERSION_CODE" ]; then
    MAX_VERSION_CODE=$TAG_VERSION_CODE
  fi
done

echo "Highest existing version code: $MAX_VERSION_CODE"

# Get the latest tag
LAST_TAG=$(git tag --list --sort=-v:refname 'v*' | head -n1)
if [ -z "$LAST_TAG" ]; then
  LAST_TAG="v0.0.0"
fi
echo "Last tag: $LAST_TAG"

# Remove the 'v' prefix if present
VERSION=${LAST_TAG#v}

IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
# Use BUMP_TYPE from environment
MAJOR=${MAJOR:-0}
MINOR=${MINOR:-0}
PATCH=${PATCH:-0}

if [ "$BUMP_TYPE" = "major" ]; then
  MAJOR=$((MAJOR + 1))
  MINOR=0
  PATCH=0
elif [ "$BUMP_TYPE" = "minor" ]; then
  MINOR=$((MINOR + 1))
  PATCH=0
else
  PATCH=$((PATCH + 1))
fi

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
NEW_TAG="v$NEW_VERSION"

# Calculate new version code
NEW_VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
# Ensure it's greater than existing
if [ "$NEW_VERSION_CODE" -le "$MAX_VERSION_CODE" ]; then
  NEW_VERSION_CODE=$((MAX_VERSION_CODE + 1))
fi

echo "NEW_VERSION=$NEW_VERSION" >> $GITHUB_ENV
echo "NEW_TAG=$NEW_TAG" >> $GITHUB_ENV
echo "NEW_VERSION_CODE=$NEW_VERSION_CODE" >> $GITHUB_ENV

echo "New version: $NEW_VERSION"
echo "New tag: $NEW_TAG"
echo "New version code: $NEW_VERSION_CODE"