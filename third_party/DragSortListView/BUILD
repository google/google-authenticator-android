package(
    default_visibility = [
        "//java/com/google/android/apps/authenticator:__subpackages__",
        "//javatests/com/google/android/apps/authenticator:__subpackages__",
    ],
)

licenses(["notice"])  # Apache 2.0

exports_files(["LICENSE"])

android_library(
    name = "DragSortListView",
    srcs = glob(["src/**/*.java"]),
    custom_package = "com.mobeta.android.dslv",
    manifest = "AndroidManifest.xml",
    resource_files = [":resource_files"],
    deps = [
        "@maven//:com_android_support_cursoradapter",
        "@maven//:com_android_support_support_v4",
    ],
)

filegroup(
    name = "resource_files",
    srcs = glob(["res/**"]),
)
