#!/bin/bash

# Auto-detect Java 17 to avoid compatibility issues with newer Java versions
JAVA17_HOME=""

# Try to find Java 17 in common locations
if [ -d "/opt/homebrew/Cellar/openjdk@17" ]; then
    # Find the latest version of openjdk@17
    JAVA17_HOME=$(find /opt/homebrew/Cellar/openjdk@17 -type d -name "Home" 2>/dev/null | head -1)
elif [ -d "/usr/local/Cellar/openjdk@17" ]; then
    JAVA17_HOME=$(find /usr/local/Cellar/openjdk@17 -type d -name "Home" 2>/dev/null | head -1)
fi

if [ -n "$JAVA17_HOME" ]; then
    export JAVA_HOME="$JAVA17_HOME"
    echo "✓ Using Java 17: $JAVA_HOME"
else
    echo "⚠ Warning: Java 17 not found. Install with: brew install openjdk@17"
    exit 1
fi

gradle wrapper --gradle-version 8.5
./gradlew build
./gradlew deploy