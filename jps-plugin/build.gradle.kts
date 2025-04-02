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

plugins {
  id("org.jetbrains.intellij.platform.module")
  id("java")
}

tasks.named<Jar>("jar") {
  archiveFileName.set("manifold-jps-plugin.jar")
}

configurations {
  create("manifoldAll")
}

java {
// Java 8 is required for JPS, otherwise an IJ project use a Java 8 compiler
// will fail to load Java 11 compiled JPS classes
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

fun getIjVersion() : String {
  val defaultIjVersion : String = project.property("defaultIjVersion") as String
  return System.getProperty("ijVersion") ?: defaultIjVersion
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(getIjVersion(), useInstaller = false)
    bundledPlugin("com.intellij.java")
  }

  val manifoldVersion : String by project

  implementation("systems.manifold:manifold:$manifoldVersion")
  implementation("systems.manifold:manifold-util:$manifoldVersion")
  implementation("systems.manifold:manifold-ext:$manifoldVersion")

  testImplementation("junit:junit:4.13.2")

  add("manifoldAll", "systems.manifold:manifold-all:$manifoldVersion")
}