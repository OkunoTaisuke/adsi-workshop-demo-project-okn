package com.example.attendance.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
    packages = "com.example.attendance",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class LayerDependencyTest {

    @ArchTest
    static final ArchRule layer_dependencies_are_respected = layeredArchitecture()
        .consideringAllDependencies()
        .optionalLayer("Controller").definedBy("..controller..")
        .optionalLayer("Service").definedBy("..service..")
        .optionalLayer("Repository").definedBy("..repository..")
        .optionalLayer("Entity").definedBy("..entity..")
        .optionalLayer("DTO").definedBy("..dto..")
        .optionalLayer("Domain").definedBy("..domain..")
        .optionalLayer("Config").definedBy("..config..")
        .optionalLayer("Exception").definedBy("..exception..")

        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
        .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Config")
        .whereLayer("Entity").mayOnlyBeAccessedByLayers(
            "Controller", "Service", "Repository", "Config", "DTO", "Domain")
        .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Service", "Repository", "DTO", "Entity");
}
