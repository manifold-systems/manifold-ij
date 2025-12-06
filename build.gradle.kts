/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

import java.util.concurrent.TimeUnit
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.InstrumentedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
  id("org.jetbrains.intellij.platform")
  id("org.jetbrains.kotlin.jvm") version "2.2.0"
  id("java")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

configurations {
  create("manifoldAll")
  create("manifoldEp")
}

repositories {
//  if (System.getenv("CI")?.equals("true", ignoreCase = true) != true) {
    mavenLocal()
//  }
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
  gradlePluginPortal()
}

subprojects {
  afterEvaluate {
    if (plugins.hasPlugin("java-library")) {
      tasks.named<Jar>("jar") {
        // make the manifold-jps-plugin.jar file go in this plugin's dir
        destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
      }
    }
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(getIjVersion(), useInstaller = false)
    pluginModule(implementation(project(":jps-plugin")))
    bundledPlugin("com.intellij.java")
    bundledPlugin("com.intellij.modules.json")

    testFramework(TestFrameworkType.Platform)
  }

  val manifoldVersion : String by project
  implementation("systems.manifold:manifold:$manifoldVersion")
  implementation("systems.manifold:manifold-util:$manifoldVersion")
  implementation("systems.manifold:manifold-darkj:$manifoldVersion")
  implementation("systems.manifold:manifold-image:$manifoldVersion")
  implementation("systems.manifold:manifold-ext:$manifoldVersion")
  implementation("systems.manifold:manifold-props:$manifoldVersion")
  implementation("systems.manifold:manifold-params:$manifoldVersion")
  implementation("systems.manifold:manifold-delegation:$manifoldVersion")
  implementation("systems.manifold:manifold-strings:$manifoldVersion")
  implementation("systems.manifold:manifold-exceptions:$manifoldVersion")
  implementation("systems.manifold:manifold-preprocessor:$manifoldVersion")
  implementation("systems.manifold:manifold-xml-rt:$manifoldVersion")

  testImplementation("junit:junit:4.13.2")

  add("manifoldAll", "systems.manifold:manifold-all:$manifoldVersion")
  add("manifoldEp", "systems.manifold:manifold-ext-producer-sample:$manifoldVersion")

  // this is for manifold-sql where use of hikari brings in slf4j
  // if we don"t add the nop here, hikari"s exception logging looks like unhandled exceptions that IJ reports as manifold errors
  implementation("org.slf4j:slf4j-api:2.0.3")
}

configurations.all {
  resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS )//always check for new SNAPSHOTs
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  when (name) {
    "compileJava" -> options.compilerArgs.addAll(listOf("-proc:none", "-Xplugin:Manifold no-bootstrap"))
    "compileTestJava" -> options.compilerArgs.addAll(listOf("-proc:none", "-Xplugin:Manifold"))
  }
}

fun getIjVersion() : String {
  val defaultIjVersion : String = project.property("defaultIjVersion") as String
  return System.getProperty("ijVersion") ?: defaultIjVersion
}

intellijPlatform {
  buildSearchableOptions = false
  pluginConfiguration {
    name = project.name
    description = "IntelliJ IDEA plugin for the Manifold project"
    version = project.property("version") as String

    ideaVersion {
      // Get build numbers from https://www.jetbrains.com/idea/download/other.html
      sinceBuild = "252"   //2025.2
      untilBuild = "252.*" //2025.2.*
    }
  }
}

tasks.register("deleteFrontloadClasses") {
  doLast {
    val kotlinDir = layout.buildDirectory.dir("classes/kotlin/main/com/intellij").get().asFile

    val kotlinFiles = fileTree(kotlinDir) {
      include("**/*.class")
    }

    println("Deleting Kotlin classes: ${kotlinFiles.files}")

    delete(kotlinFiles)
  }
}
// Make the build task depend on it
tasks.named("build") {
  finalizedBy("deleteFrontloadClasses")
}

// keep the frontloaded classes out of sight
tasks.named("instrumentCode") {
  doLast {
    val kotlinDir = layout.buildDirectory.dir("classes/kotlin/main/com/intellij").get().asFile

    val kotlinFiles = fileTree(kotlinDir) {
      include("**/*.class")
    }

    println("Deleting Kotlin classes: ${kotlinFiles.files}")

    delete(kotlinFiles)
  }
}

tasks.named<PrepareSandboxTask>("prepareSandbox") {
  from(rootProject.layout.buildDirectory.file("libs/manifold-jps-plugin.jar")) {
    into("manifold-ij/lib")
  }
  doLast {
    val kotlinDir = layout.buildDirectory.dir("classes/kotlin/main/com/intellij").get().asFile

    val kotlinFiles = fileTree(kotlinDir) {
      include("**/*.class")
    }

    println("Deleting Kotlin classes: ${kotlinFiles.files}")

    delete(kotlinFiles)
  }
}

tasks.named<Jar>("jar") {
  exclude("com/intellij/**")

  manifest {
    attributes["Contains-Sources"] = "darkj"
  }
}

tasks.named<InstrumentedJarTask>("instrumentedJar") {
  doLast {
    val jarFile = archiveFile.get().asFile
    println("Purging unwanted classes from: ${jarFile.absolutePath}")

    val tempJar = File(jarFile.parent, jarFile.name + ".tmp")

    ant.withGroovyBuilder {
      "zip"("destfile" to tempJar) {
        "zipfileset"("src" to jarFile) {
          // Adjust this pattern as needed
          "exclude"("name" to "com/intellij/**")
        }
      }
    }

    if (jarFile.delete()) {
      tempJar.renameTo(jarFile)
      println("Updated $jarFile with excluded classes.")
    } else {
      println("Failed to delete original jar!")
    }
  }
}

tasks.test {
  //scanForTestClasses = false
  include("**/*Test.class")

  // Uncomment below to show stdout/stderr in console
  // testLogging.showStandardStreams = true
  testLogging {
    events = mutableSetOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
  }

  // Set system properties in the test JVM containing the path to manifold-all and manifold-ep
  systemProperties = mapOf(
    "path.to.manifold.all" to configurations.getByName("manifoldAll").singleFile.absolutePath,
    "path.to.manifold.ep" to configurations.getByName("manifoldEp").files.first()
  )
}

tasks.runIde {
    minHeapSize = "1g"
    maxHeapSize = "4g"
// uncomment to override the ide that is run
//  ideDir = new File("C:\\Program Files\\JetBrains\\IntelliJ IDEA 2024.1.4")
}