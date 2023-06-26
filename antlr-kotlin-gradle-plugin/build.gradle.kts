plugins {
    `java-library`
    `maven-publish`
    `kotlin-dsl`
}

dependencies {
    implementation("org.antlr:antlr4:${Versions.antlr}")
    implementation(project(":antlr-kotlin-target"))
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }
}
