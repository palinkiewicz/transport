<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Transport App Icon" width="128" />

  # Transport

  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/palinkiewicz/transport">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60" />
  </a>
  <p align="center">
    <img src="https://img.shields.io/badge/Min%20SDK-29-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Min SDK" />
    <img src="https://img.shields.io/badge/Target%20SDK-36-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Target SDK" />
    <img src="https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?style=for-the-badge" alt="Language" />
    <img src="https://img.shields.io/github/repo-size/palinkiewicz/transport?style=for-the-badge&color=blue" alt="Repository Size" />
    <img src="https://img.shields.io/github/v/release/palinkiewicz/transport?style=for-the-badge&color=orange" alt="Latest Release" />
  </p>
</div>

**Transport** is a modern, privacy-respecting Free and Open Source Software (FOSS) public transport companion for Android. Built entirely from the ground up using **Jetpack Compose** and **Material 3 Expressive**, it delivers worldwide journey planning, live departures, and a real-time vehicle map — powered by the community-run [Transitous](https://transitous.org) MOTIS API, with no accounts, advertisements, or telemetry.

The application follows a disciplined layered MVVM architecture with Hilt dependency injection, kotlinx.serialization networking, and DataStore-backed persistence for robust performance and absolute reliability.

## Key Features

### 🗺️ Live Transit Map
* **MapLibre Vector Map**: A fast, Google-Maps-like vector basemap (patched OSM Liberty style served by OpenFreeMap) rendered natively via MapLibre Compose — no API keys required.
* **Real-Time Vehicles**: A two-rate pipeline fetches vehicle trip segments for the visible viewport every 30 seconds and interpolates smooth positions along their paths every second.
* **Sticky Selections**: Selected stops and vehicles survive panning and zooming — a tapped vehicle keeps its halo, route overlay, and info panel even when it leaves the fetched viewport.
* **Stop Insights**: Tapping a stop instantly overlays every route shape passing through it, with per-line filter chips.
* **Filter Menu & Search Bar**: Toggle transport categories directly on the map, and jump anywhere via the built-in geocoding search bar with the shared full-screen location picker.
* **Begin/Finish Here**: Start planning a journey straight from any point on the map — prefills flow seamlessly into the Search screen.

### 🚆 Journey Planning
* **Worldwide Routing**: Door-to-door journey planning across all Transitous-aggregated feeds, including walking legs and multi-modal transfers.
* **Advanced Search Options**: Persisted, configurable planning parameters (via DataStore) so your preferences survive across sessions.
* **Auto-Refreshing Results**: Result lists silently re-fetch every 30 seconds, keeping delays and real-time predictions current while you browse.
* **Itinerary Detail & Map View**: Leg-by-leg itinerary breakdowns with intermediate stops, plus a dedicated map rendering of the full connection geometry.
* **Location-Biased Geocoding**: Suggestions are ranked using your last-known position (plain `LocationManager` — no Play Services dependency).

### 🕒 Timetables & Live Departures
* **Stop Departures**: Live departure boards for any stop, auto-refreshing every 30 seconds with real-time data where available.
* **Trip View**: Full trip timelines showing every stop along a vehicle's run.

### ⭐ Favourites
* **Starred Places, Connections & Lines**: One-tap access to the searches you repeat every day, persisted locally with DataStore.

## Architecture & Technology Stack

The project follows a layered MVVM structure with a package-per-feature layout, keeping data access, domain models, and UI strictly separated:

* **Data Layer**: A Retrofit `MotisApi` mirroring the MOTIS v2.10.2 OpenAPI schema, decoded with **kotlinx.serialization** (lenient parsing so upstream API evolution never breaks the app). One repository per concern (geocoding, planning, timetables, map stops, live vehicles, route shapes) mapping DTOs into clean domain models and returning Kotlin `Result<T>`. Preferences (map filters, favourites, search options) are persisted as JSON blobs in **DataStore**.
* **Domain Layer**: Plain Kotlin models (`Journey`, `Departure`, `TransitLocation`, `VehicleSegment`, `RouteShape`, …) consumed directly by the UI.
* **Presentation Layer**: Built completely with **Jetpack Compose (Material 3 Expressive)** under an MVVM structure with `StateFlow`-driven state, type-safe Navigation-Compose routes, and shared ViewModel scoping across the Results/Itinerary graph.
* **Dependency Injection**: **Hilt** provides application-wide singletons (JSON, OkHttp, Retrofit, repositories) with minimal wiring boilerplate.

## Getting Started & Development

### Requirements
* **JDK 17** or newer
* **Android SDK** (Compile SDK 37, Target SDK 36, Minimum SDK 29)
* An active Android Emulator or physical device

### Building and Installation
Execute the standard Gradle tasks via the wrapper at the repository root:

```bash
# Clone the repository
git clone https://github.com/palinkiewicz/transport.git
cd transport

# Build the debug configuration APK
./gradlew assembleDebug

# Build and deploy directly onto a connected device or emulator
./gradlew installDebug
```

ABI splits are enabled — builds produce `dt-<version>-<abi>.apk` outputs (arm64-v8a, armeabi-v7a, and universal).

## Verification & Testing

Maintain code quality and stability through the following automated validation tasks:

```bash
# Run JVM unit tests
./gradlew test

# Execute Android Lint checks
./gradlew lint

# Execute instrumented UI/integration tests (requires connected device)
./gradlew connectedAndroidTest
```

> [!NOTE]
> Dependencies are centrally managed via the Gradle Version Catalog (`gradle/libs.versions.toml`). Avoid declaring hardcoded versions inline within build scripts.

## Data & Attribution

Transport is powered by [Transitous](https://transitous.org), a free, community-run transit routing service built on [MOTIS](https://github.com/motis-project/motis). Schedule and real-time data come from the publicly available feeds listed at [transitous.org/sources](https://transitous.org/sources/). Please be considerate of this shared community resource — every request the app makes carries an identifying `User-Agent`.

Map tiles are served by [OpenFreeMap](https://openfreemap.org) from [OpenStreetMap](https://www.openstreetmap.org/copyright) data.

## License

This project is licensed under the Free and Open Source Software regulations. Feel free to inspect, fork, and enhance the application following standard open-source compliance guidelines.
