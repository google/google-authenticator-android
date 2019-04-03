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

# Android devices to run tests against. This is a dictionary mapping
# a user-friendly name to a map containing information about the corresponding
# BUILD target. The user-friendly name is used used as a suffix for test
# targets.
#
# The following keys in the map are supported (others are silently ignored):
# * target -- Blaze target to use in android_test's target_devices.
# * dex_mode -- Either "legacy" (< API 21) or "native" (>= API 21)

_tested_api_levels = [
    "19",
    "21",
    "22",
    "23",
]

test_target_devices = {
    api_level: {
        "target": "@android_test_support//tools/android/emulated_devices/generic_phone:android_%s_x86_qemu2" % api_level,
        "dex_mode": "legacy" if int(api_level) < 21 else "native",
    } for api_level in _tested_api_levels
}
