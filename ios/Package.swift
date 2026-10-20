// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TiviLiteIOS",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "TiviLiteCore",
            targets: ["TiviLiteCore"]
        ),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "TiviLiteCore",
            path: "TiviLite"
        ),
    ]
)
