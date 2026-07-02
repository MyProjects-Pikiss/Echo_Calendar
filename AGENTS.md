# Agent Instructions

For Android SDK or Gradle work in WSL/Codex, do not use the default Windows SDK path from `app/local.properties`.

```bash
cd app
ANDROID_HOME=$HOME/Android/Sdk GRADLE_USER_HOME=$PWD/.gradle-home ./gradlew <task>
```
