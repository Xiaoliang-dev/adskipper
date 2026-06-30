# Gradle Wrapper

The `gradle-wrapper.jar` file needs to be downloaded. Run the following command after cloning:

```bash
gradle wrapper --gradle-version 8.7
```

Or let Android Studio / Gradle handle it automatically on first build.

The wrapper will be automatically downloaded when you run:
```bash
./gradlew assembleDebug
```