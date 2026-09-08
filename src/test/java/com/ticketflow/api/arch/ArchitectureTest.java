package com.ticketflow.api.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * [ARCHUNIT] Testes de ARQUITETURA. Rodam como teste JUnit comum, mas em vez
 * de verificar comportamento, verificam ESTRUTURA e DEPENDÊNCIAS.
 *
 * O valor real: uma regra de design deixa de ser documentação (que ninguém lê)
 * ou code review (que depende de alguém lembrar) e passa a ser garantida pelo
 * BUILD. Quem violar não consegue mergear.
 *
 * ⚠️ 'packages' precisa estar EXATO. Um typo faz o ArchUnit importar zero
 * classes e TODOS os testes passarem vazios. Foi o que aconteceu com
 * "com.tidketflow.api". Nunca use allowEmptyShould(true) para silenciar isso.
 *
 * DoNotIncludeTests: analisa apenas o código de produção. Sem isso, as
 * próprias classes de teste violariam as regras.
 */
@AnalyzeClasses(
        packages = "com.ticketflow.api",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * 🛡️ REGRA DE SANIDADE — sempre inclua esta primeiro.
     * Se o pacote estiver escrito errado, ESTE teste falha e te avisa,
     * em vez de todos os outros passarem vazios e te enganarem.
     */
    @ArchTest
    static void classesShouldHaveBeenImported(JavaClasses classes){
        if(classes.size() < 10){
            throw new AssertionError(
                    "ArchUnit imported only " + classes.size() + " classes. " +
                    "The 'packages' attribute is probably misspelled.");
        }
    }

    /**
     * [SRP / CAMADAS] Service não conhece HTTP.
     * Ajustada para a estrutura REAL do projeto: nossos controllers estão em
     * ..event.., então a regra correta proíbe os TIPOS de Spring Web, não um
     * pacote nosso. Assim ela vale de verdade.
     */
    @ArchTest
    static final ArchRule servicesShouldNotDependOnWebLayer = noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
            .because("services must be reusable from Kafka consumers, " +
                    "batch jobs and schedulers - not only from HTTP");

    /**
     * [CAMADAS] Controller nunca fala direto com o repositório.
     * Sem isso, alguém "resolve rápido" um bug pulando o service, e a regra
     * de negócio vaza para a camada web.
     */
    @ArchTest
    static final ArchRule controllersShouldNotAccessRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("controllers must go through the service layer");

    /**
     * [ENCAPSULAMENTO] A entidade Event não pode escapar pela API.
     * Garante o que discutimos em 3.2: nada de vazar entidade no JSON.
     */
    @ArchTest
    static final ArchRule controllersShouldNotExposeEntities = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .because("controllers must expose DTOs, never JPA entities");

    /**
     * [CLEAN CODE] Nada de System.out em código de produção.
     * Regra pequena, mas resolve um problema real: print esquecido em produção
     * não vai para o agregador de logs, não tem nível, não tem correlação.
     */
    @ArchTest
    static final ArchRule noSystemOut = noClasses()
            .should().callMethod(System.class, "currentTimeMillis")
            .orShould().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("use SLF4J for logging and the injected Clock for time");

    /**
     * [CONVENÇÃO] Toda classe anotada com @Service deve terminar em "Service".
     * Convenção de nome garantida pelo build — é o que mantém um projeto
     * navegável depois de 2 anos e 5 desenvolvedores.
     */
    @ArchTest
    static final ArchRule serviceNamingConvention = classes()
            .that().areAnnotatedWith("org.springframework.stereotype.Service")
            .should().haveSimpleNameEndingWith("Service")
            .orShould().haveSimpleNameEndingWith("Policy")
            .orShould().haveSimpleNameEndingWith("Calculator");

    /**
     * [DEPENDÊNCIAS CÍCLICAS] O ArchUnit detecta ciclos entre pacotes.
     * Ciclo entre pacotes é um dos indicadores mais fortes de má modularização:
     * impede extrair um módulo, dificulta raciocinar sobre o código e costuma
     * evoluir para dependência circular de beans.
     */
    @ArchTest
    static final ArchRule noCyclesBetweenPackages =
            com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                    .matching("com.ticketflow.api.(*)..")
                    .should().beFreeOfCycles();
}
