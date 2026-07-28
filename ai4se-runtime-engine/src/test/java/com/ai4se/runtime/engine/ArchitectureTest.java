package com.ai4se.runtime.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.ai4se.runtime.kernel.artifact.Artifact;
import com.ai4se.runtime.kernel.context.ExecutionContext;
import com.ai4se.runtime.kernel.task.Task;
import com.ai4se.runtime.kernel.trace.TraceRoot;
import com.ai4se.runtime.kernel.trace.TraceSpan;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture guardrails — dependency direction + domain mutation only via Lifecycle Services.
 */
@AnalyzeClasses(
        packages = "com.ai4se.runtime",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule engine_must_not_depend_on_worker_implementations =
            noClasses()
                    .that().resideInAPackage("com.ai4se.runtime.engine..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ai4se.runtime.workers..")
                    .because("Runtime must only depend on Worker SPI, not Mock/Command/Claude implementations");

    @ArchTest
    static final ArchRule kernel_must_not_depend_on_runtime_engine =
            noClasses()
                    .that().resideInAPackage("com.ai4se.runtime.kernel..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ai4se.runtime.engine..")
                    .because("Kernel must not depend on Runtime");

    @ArchTest
    static final ArchRule kernel_must_not_depend_on_worker_implementations =
            noClasses()
                    .that().resideInAPackage("com.ai4se.runtime.kernel..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ai4se.runtime.workers..")
                    .because("Kernel must not depend on Worker implementations");

    @ArchTest
    static final ArchRule worker_implementations_must_not_depend_on_runtime =
            noClasses()
                    .that().resideInAPackage("com.ai4se.runtime.workers..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ai4se.runtime.engine..")
                    .because("Worker implementations must not reverse-depend on Runtime");

    @ArchTest
    static final ArchRule worker_api_must_not_depend_on_runtime_or_kernel =
            noClasses()
                    .that().resideInAPackage("com.ai4se.runtime.worker.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ai4se.runtime.engine..",
                            "com.ai4se.runtime.kernel..",
                            "com.ai4se.runtime.workers..")
                    .because("Worker SPI stays below Runtime/Kernel/Workers");

    @ArchTest
    static final ArchRule no_cycles_in_runtime_slices =
            slices()
                    .matching("com.ai4se.runtime.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule task_domain_mutators_only_via_lifecycle_service =
            methods()
                    .that().areDeclaredIn(Task.class)
                    .and().haveNameMatching("transitionTo|bindExecutionContext|bindTraceId|markError|markCheckpoint")
                    .should().onlyBeCalled().byClassesThat()
                    .resideInAnyPackage(
                            "com.ai4se.runtime.engine.service..",
                            "com.ai4se.runtime.kernel..")
                    .because("Task mutations must go through TaskLifecycleService");

    @ArchTest
    static final ArchRule context_domain_mutators_only_via_lifecycle_service =
            methods()
                    .that().areDeclaredIn(ExecutionContext.class)
                    .and().haveNameMatching("activate|freeze|indexArtifact")
                    .should().onlyBeCalled().byClassesThat()
                    .resideInAnyPackage(
                            "com.ai4se.runtime.engine.service..",
                            "com.ai4se.runtime.kernel..")
                    .because("ExecutionContext mutations must go through ContextLifecycleService");

    @ArchTest
    static final ArchRule artifact_domain_mutators_only_via_lifecycle_service =
            methods()
                    .that().areDeclaredIn(Artifact.class)
                    .and().haveNameMatching("commit|abandon|supersede")
                    .should().onlyBeCalled().byClassesThat()
                    .resideInAnyPackage(
                            "com.ai4se.runtime.engine.service..",
                            "com.ai4se.runtime.kernel..")
                    .because("Artifact mutations must go through ArtifactLifecycleService");

    @ArchTest
    static final ArchRule trace_domain_mutators_only_via_lifecycle_service =
            methods()
                    .that().areDeclaredIn(TraceRoot.class)
                    .and().haveNameMatching("appendSpan|close")
                    .should().onlyBeCalled().byClassesThat()
                    .resideInAnyPackage(
                            "com.ai4se.runtime.engine.service..",
                            "com.ai4se.runtime.kernel..")
                    .because("TraceRoot mutations must go through TraceLifecycleService");

    @ArchTest
    static final ArchRule trace_span_end_only_via_lifecycle_service =
            methods()
                    .that().areDeclaredIn(TraceSpan.class)
                    .and().haveName("end")
                    .should().onlyBeCalled().byClassesThat()
                    .resideInAnyPackage(
                            "com.ai4se.runtime.engine.service..",
                            "com.ai4se.runtime.kernel..")
                    .because("TraceSpan.end must go through TraceLifecycleService");
}
