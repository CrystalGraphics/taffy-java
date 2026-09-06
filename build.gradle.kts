// Taffy — the layout engine, VENDORED.
//
// A fork of the published sources of `dev.vfyjxf:taffy:1.1.4` (MIT, see LICENSE), carrying our own
// fixes to its measure path. `MODIFICATIONS.md` is the diff-against-upstream statement MIT requires
// and the reason each change exists; `plan/engine-rewrite.md` D3 is the decision to own this at all.
//
// The package stays `dev.vfyjxf.taffy` on purpose — mc1710 already relocates it under
// `com.crystalgui.shadow.` when shipping, so this fork cannot lose a classloader race to a stock
// copy, and 165 call sites in core/ and the harness need no edit. See MODIFICATIONS.md.
//
// Java 21 to match :core, downgraded to Java 8 bytecode by mc1710's `downgradeJar` exactly as core's
// classes are: `Layout` is a record and `TrackSizingFunction` a sealed hierarchy, so this cannot be
// compiled at 8 directly.

plugins {
    `java-library`
}

// Coordinates, so a consumer's dependencySubstitution can name this module.
group = "com.crystalgui"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // `api`, not `implementation`: upstream's public surface leaks fastutil (GridComputer's track
    // lists, TaffyTree's node map), and consumers resolved it transitively through the artifact's POM
    // before this was a project. `implementation` would quietly drop it from mc1710's shadow
    // configuration -- the "a module bundled as CLASS FILES does not bring its dependencies" trap that
    // cost this repo every `.java` script once already.
    api("it.unimi.dsi:fastutil:${rootProject.properties["fastutil_version"]}")

    testImplementation("junit:junit:${rootProject.properties["dep.junit"]}")
}

tasks.withType<JavaCompile>().configureEach {
    // Upstream's code is not warning-clean and never was; we are not going to make it so, because
    // every cosmetic edit here is a line of noise in the next upstream diff. Only OUR changes get
    // held to this repo's standards, and they are annotated `// CrystalGUI:` so they can be found.
    options.compilerArgs.add("-Xlint:none")
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnit()
    testLogging { showStandardStreams = true }
}
