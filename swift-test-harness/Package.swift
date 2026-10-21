// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftTestHarness",
    platforms: [
        .macOS("15.0"),
    ],
    dependencies: [
        .package(name: "ProcMacro2", path: "../build/SPMPackage/macosArm64/Debug")
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "ProcMacro2Library", package: "ProcMacro2")
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-L", "../build/swift-test",
                    "-lProcMacro2",
                ]),
            ]
        ),
    ],
    swiftLanguageModes: [.v6]
)
