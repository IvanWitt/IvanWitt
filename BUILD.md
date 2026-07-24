# IvanWitt - Build & Development Setup

## Prerequisites

Before building IvanWitt, ensure you have:

### Required Software
- **Android Studio** (latest stable) or command-line tools
- **Android SDK** (version 36+)
- **Android NDK** (version 23.1+)
- **Java Development Kit (JDK)** 17+
- **Gradle** 8.0+ (comes with Android Studio)

### System Requirements
- **Disk Space:** 20 GB+ for full SDK + build artifacts
- **RAM:** 8 GB minimum (16 GB recommended)
- **OS:** Windows, macOS, or Linux

## Setup

### 1. Install Android Studio
1. Download from https://developer.android.com/studio
2. Follow installation wizard
3. Complete initial setup

### 2. Install SDK & NDK
```bash
# Via Android Studio:
# Open Android Studio → SDK Manager → SDK Platforms
# Select API 36 and install

# Or via command line:
sdkmanager "platforms;android-36"
sdkmanager "ndk;23.1.7779620"
```

### 3. Clone Repository
```bash
git clone https://github.com/IvanWitt/IvanWitt.git
cd IvanWitt
```

### 4. Initialize Submodules
```bash
git submodule update --init --recursive
```

## Building

### Build Debug APK
```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/IvanWitt_*.apk`

### Build Release APK
Requires signing configuration. First time:

```bash
./gradlew assembleRelease
```

On first run, you'll be prompted to create a signing key.

### Build All Variants
```bash
./gradlew build
```

### Build Specific Architecture
```bash
# ARM64 only
./gradlew clean assembleDebug -x "assembleDebug.*arm.*v7a"

# x86_64 only
./gradlew clean assembleDebug -x "assembleDebug.*arm*"
```

## Development Workflow

### Open in Android Studio
1. **File** → **Open**
2. Select IvanWitt directory
3. Wait for Gradle sync
4. Project will be ready

### Run on Device/Emulator
```bash
# Debug APK
./gradlew installDebug

# Run
adb shell am start -n com.ivanwitt.app/.MainActivity

# Or in Android Studio:
# Click "Run" button (green triangle)
```

### Debug Logs
```bash
# View real-time logs
adb logcat com.ivanwitt.app

# Save to file
adb logcat com.ivanwitt.app > logfile.txt
```

### Connect Device
1. Enable **Developer Mode**: Tap build number 7 times
2. Enable **USB Debugging**
3. Connect via USB
4. Select "Allow" on device
5. Run: `adb devices`

## Signing Configuration

### Create Release Key
```bash
keytool -genkey -v -keystore ivanwitt.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias IvanWittKey
```

### Configure gradle.properties
```gradle
RELEASE_STORE_FILE=ivanwitt.keystore
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=IvanWittKey
RELEASE_KEY_PASSWORD=your_password
```

### Build Signed Release
```bash
./gradlew assembleRelease
```

## Code Style

### Format Code
```bash
# Android Studio built-in formatter
Code → Reformat Code
```

### Lint Checks
```bash
./gradlew lint
```

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests (on device)
```bash
./gradlew connectedAndroidTest
```

### Code Coverage
```bash
./gradlew jacocoTestReport
```

## Troubleshooting

### Gradle Sync Failed
```bash
./gradlew clean
./gradlew sync
```

### Build Cache Issues
```bash
./gradlew clean build
# Or delete .gradle folder
```

### JDK Version Error
```bash
export JAVA_HOME=/path/to/jdk17
./gradlew --version  # Verify
```

### NDK Not Found
```bash
sdkmanager "ndk;23.1.7779620"
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/23.1.7779620
```

### Out of Memory
```bash
# Increase heap size
export GRADLE_OPTS="-Xmx4g"
./gradlew assembleDebug
```

## IDE Setup

### Android Studio Plugins
Recommended:
- Kotlin
- Gradle (built-in)
- Android Studio (built-in)

Install via **Settings** → **Plugins**

### Code Templates
Can be configured in **Settings** → **File and Code Templates**

## CI/CD

GitHub Actions workflow available in `.github/workflows/`

Check workflow status: https://github.com/IvanWitt/IvanWitt/actions

---

**Having issues? Check [FAQ.md](FAQ.md) or open an issue**
