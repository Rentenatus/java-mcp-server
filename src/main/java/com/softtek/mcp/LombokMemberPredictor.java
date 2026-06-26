package com.softtek.mcp;

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

    private LombokMemberPredictor() {}

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

    public static boolean hasLombokAnnotations(CtType<?> type) {
        if (type == null) return false;
        if (!collectAnnotationNames(type).isEmpty()) return true;
        for (CtField<?> f : type.getFields()) {
            if (!collectAnnotationNames(f).isEmpty()) return true;
        }
        return false;
    }

    private static Set<String> collectAnnotationNames(CtType<?> type) {
        return type.getAnnotations().stream()
                .map(a -> a.getAnnotationType().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> collectAnnotationNames(CtField<?> field) {
        return field.getAnnotations().stream()
                .map(a -> a.getAnnotationType().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static List<CtField<?>> allInstanceFields(CtType<?> type) {
        return type.getFields().stream()
                .filter(f -> !f.hasModifier(ModifierKind.STATIC))
                .toList();
    }

    private static List<CtField<?>> finalNonNullFields(CtType<?> type) {
        return allInstanceFields(type).stream()
                .filter(f -> f.hasModifier(ModifierKind.FINAL))
                .toList();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() > 1 && s.startsWith("is") && Character.isUpperCase(s.charAt(2))) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
