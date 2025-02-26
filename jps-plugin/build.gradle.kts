plugins {
  id("org.jetbrains.intellij.platform.module")
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

//repositories {
////  if(!System.getenv("CI")) {
//  mavenLocal()
////  }
//  mavenCentral()
////  maven {
////    url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
////  }
//  intellijPlatform {
//    defaultRepositories()
//  }
////  gradlePluginPortal()
//}

fun getIjVersion() : String {
  val defaultIjVersion : String = project.property("defaultIjVersion") as String
  return System.getProperty("ijVersion") ?: defaultIjVersion
}

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(getIjVersion())
    pluginVerifier()
    bundledPlugin("com.intellij.java")
  }

  val manifoldVersion : String by project

  implementation("systems.manifold:manifold:$manifoldVersion")
  implementation("systems.manifold:manifold-util:$manifoldVersion")
  implementation("systems.manifold:manifold-ext:$manifoldVersion")

  testImplementation("junit:junit:4.13.2")

  add("manifoldAll", "systems.manifold:manifold-all:$manifoldVersion")
}

tasks.configureEach {
  when (name) {
    "buildSearchableOptions" -> enabled = false
    "runIde" -> enabled = false
  }
}