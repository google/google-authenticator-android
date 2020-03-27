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

licenses(["notice"])  # Apache 2.0

filegroup(
    name = "res",
    srcs = glob(
        ["res/**"],
        exclude_directories = 1,
    ) + [
        "//third_party/DragSortListView:resource_files",
    ],
)

# Library containing all of the code of the app
android_library(
    name = "authenticator_lib",
    srcs = glob(["**/*.java"]),
    custom_package = "com.google.android.apps.authenticator2",
    manifest = "AndroidManifest.xml",
    plugins = [
        "//:dagger-graph-analysis-processor",
        "//:dagger-inject-adapter-processor",
        "//:dagger-module-adapter-processor",
    ],
    resource_files = [
        ":res",
    ],
    visibility = [
        "//javatests/com/google/android/apps/authenticator:__subpackages__",
    ],
    deps = [
        "//third_party/DragSortListView",
        "@maven//:com_android_support_appcompat_v7",
        "@maven//:com_android_support_design",
        "@maven//:com_android_support_loader",
        "@maven//:com_android_support_multidex",
        "@maven//:com_android_support_support_annotations",
        "@maven//:com_android_support_support_compat",
        "@maven//:com_android_support_support_fragment",
        "@maven//:com_android_support_support_v4",
        "@maven//:com_android_support_viewpager",
        "@maven//:com_google_android_gms_play_services_base",
        "@maven//:com_google_android_gms_play_services_vision",
        "@maven//:com_google_android_gms_play_services_vision_common",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:com_squareup_dagger_dagger",
        "@maven//:javax_inject_javax_inject",
    ],
)

# Production APK
android_binary(
    name = "authenticator",
    custom_package = "com.google.android.apps.authenticator2",
    manifest = "AndroidManifest.xml",
    proguard_specs = [
        "proguard-rules.pro",
    ],
    resource_files = [
        ":res",
    ],
    deps = [
        ":authenticator_lib",
    ],
)

# Multidex APK. This is not optimized by proguard and is for development/testing use only.
[android_binary(
    name = "authenticator_multidex-%s" % dex_mode,
    testonly = 1,
    custom_package = "com.google.android.apps.authenticator2",
    dex_shards = 25,
    manifest = "AndroidManifest.xml",
    multidex = "%s" % dex_mode,
    resource_files = [
        ":res",
    ],
    visibility = [
        "//javatests/com/google/android/apps/authenticator:__subpackages__",
    ],
    deps = [
        ":authenticator_lib",
    ],
) for dex_mode in [
    "legacy",
    "native",
]]
