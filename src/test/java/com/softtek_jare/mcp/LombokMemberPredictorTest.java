/*
 * MIT License
 *
 * Copyright (c) 2026 Alejandro Ferreira
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.softtek_jare.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import spoon.Launcher;
import spoon.reflect.declaration.CtType;

/**
 * The {@code LombokMemberPredictorTest} class.
 *
 * @author Alejandro Ferreira
 */
class LombokMemberPredictorTest {

/**
 * Loads a Spoon model from a test resource and returns the specified type.
 */
    private CtType<?> loadType(String resourcePath, String fqn) {
        Path project = Paths.get(resourcePath).toAbsolutePath();
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(project.resolve("src/main/java").toString());
        launcher.buildModel();
        return launcher.getFactory().Class().get(fqn);
    }

/**
 * Verifies that getters, setters, equals, hashCode, toString, and builder are predicted for a @Data class.
 */
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

/**
 * Verifies that no Lombok members are predicted for a plain class without Lombok annotations.
 */
    @Test
    void doesNotPredictForPlainClass() {
        CtType<?> type = loadType("src/test/resources/no-lombok-sample", "com.example.Plain");
        assertFalse(LombokMemberPredictor.hasLombokAnnotations(type));
        assertTrue(LombokMemberPredictor.predict(type).isEmpty(),
                "Plain class should have no predicted Lombok members");
    }
}
