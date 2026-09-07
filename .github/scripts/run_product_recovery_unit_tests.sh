#!/usr/bin/env bash
set -euo pipefail

# Product Recovery hard gate. Feature presentation tests are deliberately absent:
# they return only after their replacement user journeys pass human acceptance.
./gradlew --no-daemon --stacktrace \
  :core:model:testDebugUnitTest \
  :core:design:testDebugUnitTest \
  :core:map-domain:test \
  :core:navigation-domain:test \
  :core:marine-data:test \
  :core:shell-contract:test \
  :core:shell-engine:test \
  :adapter:chart-google:testDebugUnitTest \
  :adapter:chart-library-android:testDebugUnitTest \
  :adapter:marine-data-android:testDebugUnitTest \
  :adapter:map-offline:testDebugUnitTest \
  :adapter:map-storage:testDebugUnitTest \
  :adapter:shell-android:testDebugUnitTest \
  :adapter:shell-storage:testDebugUnitTest

# Cross-App source truth is a data-safety contract, not a presentation contract.
./gradlew --no-daemon --stacktrace \
  :app-shell:testStandaloneDebugUnitTest \
  --tests com.yokuli.marine.shell.MarineDataCrossAppStoryTest
