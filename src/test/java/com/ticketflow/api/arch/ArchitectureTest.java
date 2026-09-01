package com.ticketflow.api.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.tidketflow.api")
class ArchitectureTest {
    @ArchTest
    static final ArchRule servicesShouldNotDependOnWebLayer = noClasses()
            .that().resideInAPackage("..event..")
            .and().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat().resideInAPackage("..web..")
            .allowEmptyShould(true);
}
