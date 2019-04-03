# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Tests for Authenticator Android app

load(":target_devices.bzl", "test_target_devices")

licenses(["notice"])  # Apache 2.0

# Library containing all of the code of the Play tests
android_library(
    name = "tests_lib",
    testonly = 1,
    srcs = glob(["**/*.java"]),
    custom_package = "com.google.android.apps.authenticator2.tests",
    plugins = [
        "//:dagger-graph-analysis-processor",
        "//:dagger-inject-adapter-processor",
        "//:dagger-module-adapter-processor",
    ],
    deps = [
        "//java/com/google/android/apps/authenticator:authenticator_lib",
        "//third_party/DragSortListView",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_espresso_espresso_core",
        "@maven//:androidx_test_espresso_espresso_intents",
        "@maven//:androidx_test_ext_junit",
        "@maven//:androidx_test_monitor",
        "@maven//:androidx_test_rules",
        "@maven//:androidx_test_runner",
        "@maven//:com_android_support_design",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:com_squareup_dagger_dagger",
        "@maven//:javax_inject_javax_inject",
        "@maven//:junit_junit",
        "@maven//:org_hamcrest_java_hamcrest",
        "@maven//:org_mockito_mockito_android",
        "@maven//:org_mockito_mockito_core",
    ],
)

# Instrumentation test binaries
[android_binary(
    name = "test_app-%s" % dex_mode,
    testonly = 1,
    custom_package = "com.google.android.apps.authenticator2.tests",
    instruments = "//java/com/google/android/apps/authenticator:authenticator_multidex-%s" % dex_mode,
    manifest = "AndroidManifest.xml",
    deps = [":tests_lib"],
) for dex_mode in [
    "legacy",
    "native",
]]

# Android test targets for each test device/platform.
# Each platform gets an android_test called authenticator_instrumentation_tests-<device>.
# For example, authenticator_instrumentation_tests-19 contains all tests for API 19.
#
# TODO: Break these tests up by size (e.g. instrumentation, Robolectric, unit)
# and category (e.g. enrollment, DB, etc).
[android_instrumentation_test(
    name = "authenticator_instrumentation_tests-%s" % api_level,
    size = "large",
    timeout = "eternal",
    flaky = 1,
    target_device = device_info["target"],
    test_app = ":test_app-%s" % device_info["dex_mode"],
) for api_level, device_info in test_target_devices.items()]
