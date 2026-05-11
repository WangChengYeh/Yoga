# YogaFlow 3D
#
# Common usage:
#   make build            compile debug APK
#   make test             JVM unit tests
#   make check            test + build  (CI equivalent)
#   make install          build + install on connected device
#   make test-device      full on-device regression
#   make test-avatar      avatar position sweep + CV verification
#   make test-avatar-self demo video capture + motion analysis
#   make test-integration 10-step ADB integration suite
#   make test-all         JVM tests + full on-device regression
#   make release VERSION=1.3.0  tag + publish GitHub release
#   make help             show this list

.PHONY: all build build-release clean check \
        install kill launch logcat \
        test test-device test-all \
        test-regression test-avatar test-avatar-self test-integration \
        avatar-pos-verify avatar-pos-logcat \
        release help

# ── Tooling ───────────────────────────────────────────────────────────────────

# Honour JAVA_HOME from the environment; auto-detect via Homebrew as fallback.
ifeq ($(JAVA_HOME),)
  _BREW_JDK := $(shell brew --prefix openjdk@17 2>/dev/null)/libexec/openjdk.jdk/Contents/Home
  ifneq ($(wildcard $(_BREW_JDK)),)
    export JAVA_HOME := $(_BREW_JDK)
  endif
endif

ADB     ?= adb
PKG     := com.yogaflow
GRADLEW := ./gradlew
APK     := app/build/outputs/apk/debug/app-debug.apk
SCRIPTS := scripts

# ── Build ─────────────────────────────────────────────────────────────────────

all: check

build:
	$(GRADLEW) assembleDebug

build-release:
	$(GRADLEW) assembleRelease

clean:
	$(GRADLEW) clean

# ── JVM tests + CI gate ───────────────────────────────────────────────────────

# --rerun-tasks forces a real run even when Gradle considers everything up-to-date.
test:
	$(GRADLEW) testDebugUnitTest --rerun-tasks

# check = what CI runs: unit tests must pass and the APK must compile.
check: test build

# ── Device helpers ────────────────────────────────────────────────────────────

install: build
	$(ADB) install -r $(APK)

launch:
	$(ADB) shell am start -n $(PKG)/.MainActivity

kill:
	$(ADB) shell am force-stop $(PKG)

logcat:
	$(ADB) logcat | grep -E --line-buffered "YogaFlow|Godot|MediaPipe|FATAL|E AndroidRuntime"

# ── On-device test suites ─────────────────────────────────────────────────────

# test-regression: self-contained (script runs gradle + install internally).
test-regression:
	bash $(SCRIPTS)/test-regression.sh

# test-avatar: assumes APK already installed; installs first via make install.
test-avatar: install
	bash $(SCRIPTS)/test-avatar-movement.sh

# test-avatar-self: self-contained (script installs internally).
test-avatar-self:
	bash $(SCRIPTS)/test-avatar-self.sh

# test-integration: script's --skip-install flag; we pre-install via make install.
test-integration: install
	python3 $(SCRIPTS)/test-integration.py --skip-install

test-device: test-regression

test-all: test test-device

# ── Avatar position detection ─────────────────────────────────────────────────

# Requires: pip install opencv-python numpy

avatar-pos-verify:
	python3 $(SCRIPTS)/test-avatar-position.py --verify test-artifacts/

avatar-pos-logcat:
	python3 $(SCRIPTS)/test-avatar-position.py --logcat

# ── Release ───────────────────────────────────────────────────────────────────

# Usage: make release VERSION=1.3.0
# Bumps versionName in build.gradle.kts, tags, and publishes a GitHub release.
# GitHub Actions builds and attaches the APK automatically.
release:
ifndef VERSION
	$(error VERSION is required — usage: make release VERSION=1.3.0)
endif
	@echo "Bumping versionName to $(VERSION) in app/build.gradle.kts..."
	sed -i '' 's/versionName = "[^"]*"/versionName = "$(VERSION)"/' app/build.gradle.kts
	git add app/build.gradle.kts
	git commit -m "release: bump versionName to $(VERSION)"
	git push origin main
	@echo "Tagging v$(VERSION)..."
	git tag "v$(VERSION)"
	git push origin "v$(VERSION)"
	gh release create "v$(VERSION)" \
	  --title "v$(VERSION)" \
	  --generate-notes
	@echo ""
	@echo "Release v$(VERSION) published. GitHub Actions will attach the APK."

# ── Help ──────────────────────────────────────────────────────────────────────

help:
	@echo ""
	@echo "YogaFlow 3D — make targets"
	@echo ""
	@echo "  Build"
	@echo "    build              Compile debug APK"
	@echo "    build-release      Compile release APK"
	@echo "    clean              Remove Gradle build outputs"
	@echo "    check              Unit tests + debug build  (CI gate)"
	@echo ""
	@echo "  Device  (requires connected ADB device)"
	@echo "    install            Build + install debug APK"
	@echo "    launch             Start app on device"
	@echo "    kill               Force-stop app on device"
	@echo "    logcat             Tail filtered logcat"
	@echo ""
	@echo "  Tests"
	@echo "    test               JVM unit tests (--rerun-tasks)"
	@echo "    test-device        Full on-device regression"
	@echo "    test-avatar        Avatar position sweep + CV verification"
	@echo "    test-avatar-self   Demo video capture + motion analysis"
	@echo "    test-integration   10-step ADB integration suite"
	@echo "    test-all           JVM tests + on-device regression"
	@echo ""
	@echo "  Avatar position  (requires: pip install opencv-python numpy)"
	@echo "    avatar-pos-verify  Verify test-artifacts/ via diff + logcat"
	@echo "    avatar-pos-logcat  Show live logcat avatar position commands"
	@echo ""
	@echo "  Release"
	@echo "    release VERSION=X.Y.Z  Bump version, tag, publish GitHub release"
	@echo ""
