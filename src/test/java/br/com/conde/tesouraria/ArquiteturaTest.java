package br.com.conde.tesouraria;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras de arquitetura: o domínio não conhece a web nem os controllers falam com repositórios. */
@AnalyzeClasses(packages = "br.com.conde.tesouraria")
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioNaoDependeDaWeb = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..web..", "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule dominioNaoDependeDeApplication = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule controllersNaoUsamRepositorios = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
}
