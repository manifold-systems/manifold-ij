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

plugins {
  id("org.jetbrains.intellij.platform")
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
  if (System.getenv("CI")?.equals("true", ignoreCase = true) != true) {
    mavenLocal()
  }
  mavenCentral()
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
  }
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
  implementation("systems.manifold:manifold-params-rt:$manifoldVersion")
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
    version = getIjVersion()

    ideaVersion {
      // Get build numbers from https://www.jetbrains.com/idea/download/other.html
      sinceBuild = "251"   //2025.1
      untilBuild = "251.*" //2025.1.*
    }
  }
}

tasks.named<Jar>("jar") {
  manifest {
    attributes["Contains-Sources"] = "darkj"
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