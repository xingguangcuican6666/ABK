#!/bin/bash
find ~/storage/downloads/ABK_repo/app/src/main/java -name "*.kt" | while read f; do
    result=$(grep -n '"\${\"' "$f")
    if [ -n "$result" ]; then
        echo "=== $f ==="
        echo "$result"
    fi
done
