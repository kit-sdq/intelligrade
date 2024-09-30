# IntelliGrade

IntelliGrade is an IntelliJ port of [This](https://github.com/kit-sdq/programming-lecture-eclipse-artemis) plugIn. 
It is used to grade [Artemis](https://github.com/ls1intum/Artemis) based exercises.

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