plugins {
    id("dev.prism")
}

group = "com.leclowndu93150"
version = "1.1.0"

// The net.fabricmc types under src/main/java exist only so javac can resolve the Fabric
// supertypes of the Voxy classes Foxy compiles against. Shipping them put a real
// net.fabricmc.loader.api.FabricLoader in the game, which every multiloader mod that probes for
// that class read as "we are on Fabric" before failing on a stub Foxy does not implement.
subprojects {
    tasks.withType<Jar>().configureEach {
        exclude("net/fabricmc/**")
    }
}

prism {
    metadata {
        modId = "foxy"
        name = "Foxy"
        description = "Loads the Voxy LoD rendering mod on NeoForge by bridging its Fabric entrypoints and APIs."
        license = "MIT"
        author("leclowndu93150")
    }

    modrinthMaven()
    maven("NeoForged", "https://maven.neoforged.net/releases")

    version("26.1.2") {
        neoforge {
            loaderVersion = "26.1.2.48-beta"
            loaderVersionRange = "[4,)"

            dependencies {
                compileOnly("maven.modrinth:sodium:mc26.1.2-0.8.12-neoforge")
                runtimeOnly("maven.modrinth:sodium:mc26.1.2-0.8.12-neoforge")
                compileOnly("maven.modrinth:voxy:0.2.16-beta")
                implementation("maven.modrinth:voxy:0.2.16-beta")
                compileOnly("cpw.mods:modlauncher:11.0.5")
                compileOnly("cpw.mods:securejarhandler:3.0.8")
                compileOnly("net.neoforged.fancymodloader:loader:11.0.13")

                compileOnly("maven.modrinth:chunky:hEXc6nbN")
            }
        }
    }

    version("26.2") {
        neoforge {
            loaderVersion = "26.2.0.28-beta"
            loaderVersionRange = "[4,)"

            dependencies {
                compileOnly("maven.modrinth:sodium:mc26.2-0.9.1-neoforge")
                runtimeOnly("maven.modrinth:sodium:mc26.2-0.9.1-neoforge")
                compileOnly("maven.modrinth:voxy:0.2.18-beta")
                implementation("maven.modrinth:voxy:0.2.18-beta")
                compileOnly("cpw.mods:modlauncher:11.0.5")
                compileOnly("cpw.mods:securejarhandler:3.0.8")
                compileOnly("net.neoforged.fancymodloader:loader:11.0.13")

                compileOnly("maven.modrinth:chunky:hEXc6nbN")
            }
        }
    }

}
