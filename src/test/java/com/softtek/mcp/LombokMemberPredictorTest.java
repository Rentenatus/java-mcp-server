package com.softtek.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import spoon.Launcher;
import spoon.reflect.declaration.CtType;

class LombokMemberPredictorTest {

    private CtType<?> loadType(String resourcePath, String fqn) {
        Path project = Paths.get(resourcePath).toAbsolutePath();
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(project.resolve("src/main/java").toString());
        launcher.buildModel();
        return launcher.getFactory().Class().get(fqn);
    }

    @Test
    void predictsGettersSettersEqualsHashCodeAndToStringForDataClass() {
        CtType<?> type = loadType("src/test/resources/lombok-sample", "com.example.User");
        assertTrue(LombokMemberPredictor.hasLombokAnnotations(type),
                "User should be detected as Lombok-annotated");

        List<LombokMemberPredictor.PredictedMember> predicted = LombokMemberPredictor.predict(type);
        List<String> sigs = predicted.stream().map(LombokMemberPredictor.PredictedMember::signature).toList();

        assertTrue(sigs.stream().anyMatch(s -> s.endsWith("getName()")), "Expected getName(), got: " + sigs);
        assertTrue(sigs.stream().anyMatch(s -> s.endsWith("getAge()")), "Expected getAge()");
        assertTrue(sigs.stream().anyMatch(s -> s.endsWith("getEmail()")), "Expected getEmail()");
        assertTrue(sigs.stream().anyMatch(s -> s.contains("setName(")), "Expected setName(...)");
        assertTrue(sigs.stream().anyMatch(s -> s.contains("setAge(")), "Expected setAge(...)");
        assertTrue(sigs.contains("boolean equals(Object obj)"));
        assertTrue(sigs.contains("int hashCode()"));
        assertTrue(sigs.contains("String toString()"));
        assertTrue(sigs.stream().anyMatch(s -> s.contains("Builder builder()")),
                "Expected builder() method");
    }

    @Test
    void doesNotPredictForPlainClass() {
        CtType<?> type = loadType("src/test/resources/no-lombok-sample", "com.example.Plain");
        assertFalse(LombokMemberPredictor.hasLombokAnnotations(type));
        assertTrue(LombokMemberPredictor.predict(type).isEmpty(),
                "Plain class should have no predicted Lombok members");
    }
}
