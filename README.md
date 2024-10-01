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

The tool window to the left with the pen icon is the grading window, and the tool window with the sheet
of paper icon to the bottom left is the annotations list.

## Differences to the eclipse tool

- Hovering over line markers does not show the corresponding annotation(s). This is because IntelliJ itself has many tooltips on code that would conflict with our tooltip (though there is code in the HighlighterManager for such tooltips, which is currently commented out). Instead, you can click on or hover over gutter icons to show the annotations. Selecting an annotation from the list brings you to the corresponding entry in the annotations list.
- You can right click an annotation in the annotations list to edit its custom message and custom score
- Grading Buttons show the number of corresponding annotations made
- The old Alt + Enter shortcut is now Alt + A since IntelliJ already uses the former
- All editors are read-only

## Building
- run gradle target `runIde` to get a development version of the IDE:
    - on Windows: `gradlew.bat runIde`
    - on Linux: `./gradlew runIde`

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
