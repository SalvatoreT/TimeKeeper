#!/bin/bash
set -e # exit on error

# Get the new version and version code from the environment variables
new_version=$NEW_VERSION
new_version_code=$NEW_VERSION_CODE

# Check if the module.yaml file exists
if [ ! -f "module.yaml" ]; then
  echo "module.yaml file not found!"
  exit 1
fi

# Overwrite the versionName and versionCode in module.yaml
# Note: We use a different sed delimiter here to avoid issues with slashes in paths
sed -i.bak "s/versionCode: .*/versionCode: $new_version_code # Overwritten by CI/" module.yaml
sed -i.bak "s/versionName: .*/versionName: $new_version # Overwritten by CI/" module.yaml

echo "module.yaml updated successfully with new version: $new_version and version code: $new_version_code"
