tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}