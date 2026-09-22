#!/bin/bash

# Move to the root of the git repository
cd "$(git rev-parse --show-toplevel)" || exit

# Stage everything
git add -A

# Commit using the message passed to the script
git commit -m "$1"

# Push to main
git push origin main