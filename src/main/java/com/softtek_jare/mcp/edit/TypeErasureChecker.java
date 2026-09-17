/*
 * MIT License
 *
 * Copyright (c) 2026 Janusch Rentenatus
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

package com.softtek_jare.mcp.edit;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for type-erasure signature collisions between methods.
 * Java does not permit two methods with the same erased parameter types.
 *
 * @author Janusch Rentenatus
 */
public class TypeErasureChecker {

    /**
     * Result of an erasure check.
     */
    public record ClashResult(boolean clash, String message) {}

    /**
     * Checks whether adding a method with the given name and parameter types
     * would collide with an existing method after type erasure.
     *
     * @param targetClass the class where the method would be added
     * @param methodName  the method name
     * @param paramTypes  the erased parameter types (simple names)
     * @return clash result with descriptive message
     */
    public ClashResult checkErasure(CtType<?> targetClass, String methodName, List<String> paramTypes) {
        for (CtMethod<?> existing : targetClass.getMethods()) {
            if (!existing.getSimpleName().equals(methodName)) continue;
            List<String> existingErased = erasedParamTypes(existing);
            if (sameErased(existingErased, paramTypes)) {
                return new ClashResult(true,
                    "Type erasure clash — method '" + methodName + "(" + String.join(", ", paramTypes) + ")' "
                    + "has the same erased signature as existing method '"
                    + methodName + "(" + String.join(", ", existingErased) + ")'. "
                    + "Java does not permit both. This will not compile.");
            }
        }
        return new ClashResult(false, null);
    }

    private List<String> erasedParamTypes(CtMethod<?> method) {
        List<String> result = new ArrayList<>();
        for (var param : method.getParameters()) {
            if (param.getType() != null) {
                result.add(erasedName(param.getType()));
            } else {
                result.add("?");
            }
        }
        return result;
    }

    private String erasedName(CtTypeReference<?> typeRef) {
        // Erasure: List<String> -> List, Map<K,V> -> Map
        String qualified = typeRef.getQualifiedName();
        if (qualified == null) return typeRef.getSimpleName();
        int genStart = qualified.indexOf("<");
        if (genStart > 0) {
            qualified = qualified.substring(0, genStart);
        }
        // Return simple name of the erased type
        int lastDot = qualified.lastIndexOf('.');
        return lastDot >= 0 ? qualified.substring(lastDot + 1) : qualified;
    }

    private boolean sameErased(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }
}
