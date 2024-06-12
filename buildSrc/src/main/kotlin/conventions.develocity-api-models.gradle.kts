@file:Suppress("UnstableApiUsage")

plugins {
    id("java")
    id("org.openapi.generator")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

val develocityApiExtension = extensions.create<DevelocityApiExtension>("develocityApi")

repositories {
    exclusiveContent {
        forRepository {
            ivy {
                name = "Develocity API Specification"
                url = uri("https://docs.gradle.com/enterprise/api-manual/ref")
                patternLayout { artifact("/develocity-[revision]-api.yaml") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("com.gradle", "develocity-api-specification") }
    }
}

val develocityApiSpecification: DependencyScopeConfiguration = configurations.dependencyScope("develocityApiSpecification") {
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, "develocity-api-specification"))
}.get()

val resolvableDevelocityApiSpecification = configurations.resolvable("resolvableDevelocityApiSpecification") {
    extendsFrom(develocityApiSpecification)
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, "develocity-api-specification"))
}

dependencies {
    develocityApiSpecification(develocityApiExtension.version.map { "com.gradle:develocity-api-specification:$it" })
    implementation(libs.findLibrary("jackson-annotations").get())
    implementation(libs.findLibrary("jackson-databind").get())
    implementation(libs.findLibrary("jakarta-annotation-api").get())
}

openApiGenerate {
    generatorName = "java"
    inputSpec = resolvableDevelocityApiSpecification.map { it.singleFile.absolutePath }
    outputDir = layout.buildDirectory.dir("generated/openapi").map { it.asFile.absolutePath }
    modelPackage = provider { "$group.api" }
    apiPackage = provider { "$group.unused.api" }
    invokerPackage = provider { "$group.unused.invoker" }
    cleanupOutput = true
    openapiNormalizer = mapOf("REF_AS_PARENT_IN_ALLOF" to "true")
    // see https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/java.md for a description of each configuration option
    configOptions = mapOf(
        "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)",
        "containerDefaultToNull" to "true",
        "disallowAdditionalPropertiesIfNotPresent" to "false",
        "hideGenerationTimestamp" to "true",
        "library" to "native",
        "openApiNullable" to "false",
        "useBeanValidation" to "false",
        "useJakartaEe" to "true",
    )
}

val generateDevelocityApiModels by tasks.registering(Sync::class) {
    from(tasks.openApiGenerate) {
        includeEmptyDirs = false
        include("src/main/java/com/gradle/develocity/api/*")
        eachFile {
            path = path.removePrefix("src/main/java")
        }
    }
    into(layout.buildDirectory.dir(name))
}

sourceSets {
    main {
        java {
            srcDir(generateDevelocityApiModels)
        }
    }
}

abstract class DevelocityApiExtension {
    abstract val version: Property<String>
}
