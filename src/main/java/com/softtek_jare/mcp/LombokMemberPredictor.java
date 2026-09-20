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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.FieldFactory;
import spoon.reflect.factory.TypeFactory;

/**
 * Heuristic predictor of Lombok-generated members, used as a safety net when
 * a project is loaded with delombok disabled (or delombok failed). It inspects
 * Lombok annotations on a type and reports which members Lombok would have
 * generated, even though they are NOT present in the AST.
 *
 * @author Alejandro Ferreira
 */
public final class LombokMemberPredictor {

    private static final Set<String> FIELD_LEVEL_GETTER = Set.of(
            "Getter", "Data", "Value");
    private static final Set<String> FIELD_LEVEL_SETTER = Set.of(
            "Setter", "Data");
    private static final Set<String> TYPE_LEVEL_GETTER = Set.of(
            "Getter", "Data", "Value");
    private static final Set<String> TYPE_LEVEL_SETTER = Set.of(
            "Setter", "Data");

    public record PredictedMember(String kind, String signature) {}

/**
 * Private constructor to prevent instantiation.
 */
    private LombokMemberPredictor() {}

/**
 * Predicts which members Lombok would generate for the given type based on its annotations.
 */
    public static List<PredictedMember> predict(CtType<?> type) {
        if (type == null) return List.of();
        List<PredictedMember> out = new ArrayList<>();
        TypeFactory tf = type.getFactory().Type();
        FieldFactory ff = type.getFactory().Field();

        Set<String> typeAnnots = collectAnnotationNames(type);

        boolean addToString = typeAnnots.contains("ToString") || typeAnnots.contains("Data") || typeAnnots.contains("Value");
        boolean addEqualsHashCode = typeAnnots.contains("EqualsAndHashCode") || typeAnnots.contains("Data") || typeAnnots.contains("Value");
        boolean generateNoArgs = typeAnnots.contains("NoArgsConstructor")
                || typeAnnots.contains("Data") || typeAnnots.contains("RequiredArgsConstructor") || typeAnnots.contains("AllArgsConstructor")
                || typeAnnots.contains("Builder") || typeAnnots.contains("Value");
        boolean generateRequiredArgs = typeAnnots.contains("RequiredArgsConstructor")
                || typeAnnots.contains("Data") || typeAnnots.contains("Value");
        boolean generateAllArgs = typeAnnots.contains("AllArgsConstructor")
                || typeAnnots.contains("Data") || typeAnnots.contains("Builder");
        boolean generateBuilder = typeAnnots.contains("Builder") || typeAnnots.contains("Data") || typeAnnots.contains("Value");
        boolean generateSingular = typeAnnots.contains("Builder") || typeAnnots.contains("Data");

        if (typeAnnots.contains("ToString") || typeAnnots.contains("Data") || typeAnnots.contains("Value")) {
            out.add(new PredictedMember("method", "String toString()"));
        }
        if (typeAnnots.contains("EqualsAndHashCode") || typeAnnots.contains("Data") || typeAnnots.contains("Value")) {
            out.add(new PredictedMember("method", "boolean equals(Object obj)"));
            out.add(new PredictedMember("method", "int hashCode()"));
        }
        if (typeAnnots.contains("NoArgsConstructor") || typeAnnots.contains("Data")) {
            String name = type.getSimpleName();
            out.add(new PredictedMember("constructor", name + "()"));
        }
        if (typeAnnots.contains("RequiredArgsConstructor") || typeAnnots.contains("Data") || typeAnnots.contains("Value")) {
            String name = type.getSimpleName();
            String params = finalNonNullFields(type).stream()
                    .map(f -> f.getType().getQualifiedName() + " " + f.getSimpleName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            out.add(new PredictedMember("constructor", name + "(" + params + ")"));
        }
        if (typeAnnots.contains("AllArgsConstructor") || typeAnnots.contains("Data") || typeAnnots.contains("Builder")) {
            String name = type.getSimpleName();
            String params = allInstanceFields(type).stream()
                    .map(f -> f.getType().getQualifiedName() + " " + f.getSimpleName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            out.add(new PredictedMember("constructor", name + "(" + params + ")"));
        }
        if (typeAnnots.contains("Builder") || typeAnnots.contains("Data") || typeAnnots.contains("Value")) {
            String name = type.getSimpleName();
            out.add(new PredictedMember("method", "static " + name + ".Builder builder()"));
            out.add(new PredictedMember("method", name + " build()  (on inner Builder)"));
        }

        boolean hasTypeGetter = typeAnnots.stream().anyMatch(TYPE_LEVEL_GETTER::contains);
        boolean hasTypeSetter = typeAnnots.stream().anyMatch(TYPE_LEVEL_SETTER::contains);

        for (CtField<?> field : allInstanceFields(type)) {
            Set<String> fieldAnnots = collectAnnotationNames(field);
            String fieldName = field.getSimpleName();
            String capitalized = capitalize(fieldName);
            String typeName = field.getType().getQualifiedName();

            boolean isFinal = field.hasModifier(ModifierKind.FINAL);
            boolean isStatic = field.hasModifier(ModifierKind.STATIC);
            if (isStatic) continue;

            boolean genGetter = fieldAnnots.contains("Getter")
                    || (!fieldAnnots.contains("Setter") && hasTypeGetter);
            boolean genSetter = !isFinal && (fieldAnnots.contains("Setter")
                    || (!fieldAnnots.contains("Getter") && hasTypeSetter));

            if (genGetter) {
                String prefix = field.getType().getSimpleName().equals("boolean") && !fieldName.startsWith("is")
                        ? "is" : "get";
                out.add(new PredictedMember("method", typeName + " " + prefix + capitalized + "()"));
            }
            if (genSetter) {
                String prefix = field.getType().getSimpleName().equals("boolean") && fieldName.startsWith("is") && fieldName.length() > 2
                        && Character.isUpperCase(fieldName.charAt(2))
                        ? "set" + fieldName.substring(2)
                        : "set" + capitalized;
                out.add(new PredictedMember("method", "void " + prefix + "(" + typeName + " " + fieldName + ")"));
            }
        }

        return out;
    }

/**
 * Checks whether the given type or any of its fields has Lombok annotations.
 */
    public static boolean hasLombokAnnotations(CtType<?> type) {
        if (type == null) return false;
        if (!collectAnnotationNames(type).isEmpty()) return true;
        for (CtField<?> f : type.getFields()) {
            if (!collectAnnotationNames(f).isEmpty()) return true;
        }
        return false;
    }

/**
 * Collects the simple names of all annotations on the given type.
 */
    private static Set<String> collectAnnotationNames(CtType<?> type) {
        return type.getAnnotations().stream()
                .map(a -> a.getAnnotationType().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());
    }

/**
 * Collects the simple names of all annotations on the given field.
 */
    private static Set<String> collectAnnotationNames(CtField<?> field) {
        return field.getAnnotations().stream()
                .map(a -> a.getAnnotationType().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());
    }

/**
 * Returns all non-static instance fields of the given type.
 */
    private static List<CtField<?>> allInstanceFields(CtType<?> type) {
        return type.getFields().stream()
                .filter(f -> !f.hasModifier(ModifierKind.STATIC))
                .toList();
    }

/**
 * Returns all final instance fields of the given type.
 */
    private static List<CtField<?>> finalNonNullFields(CtType<?> type) {
        return allInstanceFields(type).stream()
                .filter(f -> f.hasModifier(ModifierKind.FINAL))
                .toList();
    }

/**
 * Capitalizes the first character of a string, with special handling for boolean {@code is} prefixes.
 */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() > 1 && s.startsWith("is") && Character.isUpperCase(s.charAt(2))) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
