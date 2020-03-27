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

# Set the path to your local SDK installation, or use the ANDROID_HOME environment variable.
android_sdk_repository(
    name = "androidsdk",
    api_level = 29,
    # path = "/path/to/sdk",
    build_tools_version = "29.0.3"
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Load external Maven Rules
RULES_JVM_EXTERNAL_TAG = "3.0"
RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

# TODO: Stop importing gmaven_rules once Android Test no longer depends on the target.
http_archive(
    name = "gmaven_rules",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")
maven_install(
    artifacts = [
        "androidx.test:core:1.1.0",
        "androidx.test.espresso:espresso-core:3.1.1",
        "androidx.test.espresso:espresso-intents:3.1.1",
        "androidx.test.ext:junit:1.1.0",
        "androidx.test:monitor:1.1.1",
        "androidx.test:rules:1.1.1",
        "androidx.test:runner:1.1.1",
        "com.android.support:appcompat-v7:28.0.0",
        "com.android.support:cursoradapter:28.0.0",
        "com.android.support:design:28.0.0",
        "com.android.support:loader:28.0.0",
        "com.android.support:multidex:1.0.3",
        "com.android.support:support-annotations:28.0.0",
        "com.android.support:support-compat:28.0.0",
        "com.android.support:support-fragment:28.0.0",
        "com.android.support:support-v4:28.0.0",
        "com.android.support:viewpager:28.0.0",
        "com.google.android.gms:play-services-base:16.1.0",
        "com.google.android.gms:play-services-vision:17.0.2",
        "com.google.android.gms:play-services-vision-common:17.0.2",
        "com.google.guava:guava:27.1-android",
        "com.google.truth:truth:0.43",
        "com.squareup.dagger:dagger:1.2.5",
        "com.squareup.dagger:dagger-compiler:1.2.5",
        "javax.inject:javax.inject:1",
        "junit:junit:4.12",
        "org.hamcrest:java-hamcrest:2.0.0.0",
        "org.mockito:mockito-android:2.25.1",
        "org.mockito:mockito-core:2.25.1",
    ],
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2/",
        "http://central.maven.org/maven2/",
    ],
    fetch_sources = True,
)

# Load Android Test Support Repository
ANDROID_TEST_SUPPORT_TAG = "androidx-test-1.1.0"
ANDROID_TEST_SUPPORT_SHA = "0842d5204e2cc12505eabd03e5dcdd76661a81a2ef9f131ea5f381aee76b379c"

http_archive(
    name = "android_test_support",
    strip_prefix = "android-test-%s" % ANDROID_TEST_SUPPORT_TAG,
    url = "https://github.com/android/android-test/archive/androidx-test-1.1.0.tar.gz",
    sha256 = ANDROID_TEST_SUPPORT_SHA,
)

load("@android_test_support//:repo.bzl", "android_test_repositories")
android_test_repositories()
