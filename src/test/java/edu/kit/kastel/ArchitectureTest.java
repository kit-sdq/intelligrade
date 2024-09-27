/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "edu.kit.kastel.sdq.intelligrade")
class ArchitectureTest {
    @ArchTest
    static final ArchRule noForEachInCollectionsOrStream = noClasses()
            .should()
            .callMethod(Stream.class, "forEach", Consumer.class)
            .orShould()
            .callMethod(Stream.class, "forEachOrdered", Consumer.class)
            .orShould()
            .callMethod(List.class, "forEach", Consumer.class)
            .orShould()
            .callMethod(List.class, "forEachOrdered", Consumer.class)
            .because("Lambdas should be functional. ForEach is typically used for side-effects.");
}
