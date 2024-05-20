# manifold-ij
[![](https://img.shields.io/jetbrains/plugin/d/10057.svg)][jb-url]
[![](https://img.shields.io/jetbrains/plugin/v/10057.svg)][jb-url]

An IntelliJ IDEA plugin for Manifold.

[jb-url]: https://plugins.jetbrains.com/plugin/10057-manifold

# Description
Use the [Manifold IntelliJ IDEA plugin][jb-url] to experience Manifold to its fullest.

The plugin provides comprehensive support for Manifold along with high-level IntelliJ features including:
* Feature highlighting
* Error reporting
* Code completion
* Navigation
* Usage searching
* Refactoring
* Incremental compilation
* Debugging

Install the plugin directly from IntelliJ via:

<kbd>Settings</kbd> ➜ <kbd>Plugins</kbd> ➜ <kbd>Marketplace</kbd> ➜ search: `Manifold`

Visit the [Manifold website](http://manifold.systems/) for more information.

# Development

The plugin can be compiled and tested against different API versions. The default value lives in the `defaultIjVersion` property in the root `gradle.properties` file.

Either override this value in `gradle.properties` locally, or from the command line: `gradlew check -DijVersion=15.0.6`