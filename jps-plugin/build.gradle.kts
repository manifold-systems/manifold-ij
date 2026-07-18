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

// Java 8 is required for JPS, otherwise an IJ project using a Java 8 compiler
// will fail to load JPS classes compiled a newer bytecode version
tasks.withType<JavaCompile>().configureEach {
  options.release.set(8)
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
    intellijIdea(getIjVersion()){useInstaller = false}
    bundledPlugin("com.intellij.java")
  }

  val manifoldVersion : String by project

  implementation("systems.manifold:manifold:$manifoldVersion")
  implementation("systems.manifold:manifold-util:$manifoldVersion")
  implementation("systems.manifold:manifold-ext:$manifoldVersion")

  testImplementation("junit:junit:4.13.2")

  add("manifoldAll", "systems.manifold:manifold-all:$manifoldVersion")
}