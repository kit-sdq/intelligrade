# IntelliGrade

IntelliGrade is an IntelliJ port of [the Eclipse Grading Tool](https://github.com/kit-sdq/programming-lecture-eclipse-artemis).
It is used to grade [Artemis](https://github.com/ls1intum/Artemis) based exercises.

## Running
```
git clone https://github.com/kit-sdq/intelligrade/
cd intelligrade
./gradlew runIde
```
for updating, execute
```
cd intelligrade
git pull
./gradlew runIde
```

## Setup

If the Welcome to IntelliJ IDEA screen pops up, simply create a new empty project and open it.
This will be the grading workspace. Set the Artemis URL at File > Settings > Tools > IntelliGrade Settings,
and press the connect button.

The tool window to the top right with the pen icon is the grading window, and the tool window with the sheet
of paper icon to the bottom left is the annotations list.

## Differences to the Eclipse tool

- Hovering over line markers does not show the corresponding annotation(s). Instead, you can click on or hover over gutter icons to show the annotations. Selecting an annotation from the list brings you to the corresponding entry in the annotations list.
- You can right click an annotation in the annotations list (or double-click the `message` column) to **edit its custom message and custom score** or **delete** the annotation.
- The old Alt + Enter shortcut for adding annotations is now **Alt + A**. You can search in the popup as usual, and hold Control to add a custom message.
- All editors are read-only.

## Building
- Make sure that you have Java >=21 and a recent version of Gradle installed.
- Run the gradle target `runIde` to get a development version of the IDE:
    - on Windows: `gradlew.bat runIde`
    - on Linux: `./gradlew runIde`
  This will download IntelliJ Community Edition (may take several minutes) and run it with Intelligrade installed.

## Features

## Contributing

### Important
Before starting, please make sure that you've understood *and internalized* Intellij's [threading model](https://plugins.jetbrains.com/docs/intellij/threading-model.html).
Whatever you do, always remember which thread you are on (EDT/pooled) and what you can do on that thread.

Also, please do not touch the [JCEF login code](src/main/java/edu/kit/kastel/sdq/intelligrade/login) unless absolutely necessary.
It contains delicate synchronization code, which is needed since most JCEF methods are asynchronous and call you back on arbitrary threads.
Also, IntelliJ's JCEF wrapper (JBCEF) is very buggy.

### UI
Most layouts use MigLayout, which is included in IJ.
Good references are [the white paper](http://www.miglayout.com/whitepaper.html) and [the cheat sheet](http://www.migcalendar.com/miglayout/mavensite/docs/cheatsheet.html).
JB publishes [UI guidelines](https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html), which are worth a read.
For icons, they have a very nice [search engine](https://intellij-icons.jetbrains.design/).
