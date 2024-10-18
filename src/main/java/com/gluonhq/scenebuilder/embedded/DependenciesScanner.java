/*
 * Copyright (c) 2024, Gluon and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Gluon nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.scenebuilder.embedded;

import java.lang.StackWalker;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class DependenciesScanner {

    private static void findLayerOrder(ModuleLayer layer, Set<ModuleLayer> visited, Deque<ModuleLayer> layersOut) {
        if (layer != null && visited.add(layer)) {
            for (ModuleLayer parent : layer.parents()) {
                findLayerOrder(parent, visited, layersOut);
            }
            layersOut.push(layer);
        }
    }

    private static List<Entry<ModuleReference, ModuleLayer>> findModuleRefs(Class<?>[] callStack) {
        Deque<ModuleLayer> layerOrder = new ArrayDeque<>();
        Set<ModuleLayer> visited = new HashSet<>();
        for (Class<?> aClass : callStack) {
            ModuleLayer layer = aClass.getModule().getLayer();
            findLayerOrder(layer, visited, layerOrder);
        }
        Set<ModuleReference> addedModules = new HashSet<>();
        List<Entry<ModuleReference, ModuleLayer>> moduleRefs = new ArrayList<>();
        for (ModuleLayer layer : layerOrder) {
            Set<ResolvedModule> modulesInLayerSet = layer.configuration().modules();
            final List<Entry<ModuleReference, ModuleLayer>> modulesInLayer = new ArrayList<>();
            for (ResolvedModule module : modulesInLayerSet) {
                modulesInLayer.add(new SimpleEntry<>(module.reference(), layer));
            }
            modulesInLayer.sort(Comparator.comparing(e -> e.getKey().descriptor().name()));
            for (Entry<ModuleReference, ModuleLayer> m : modulesInLayer) {
                if (addedModules.add(m.getKey())) {
                    moduleRefs.add(m);
                }
            }
        }
        return moduleRefs;
    }

    @SuppressWarnings("removal")
    private static Class<?>[] getCallStack() {
        PrivilegedAction<Class<?>[]> stackWalkerAction = () ->
                        StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                                .walk(s -> s.map(StackFrame::getDeclaringClass).toArray(Class[]::new));
        try {
            return AccessController.doPrivileged(stackWalkerAction);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isSystemModule(final ModuleReference moduleReference) {
        String name = moduleReference.descriptor().name();
        if (name == null) {
            return false;
        }
        return name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("javafx.") ||
                name.startsWith("oracle.") || name.startsWith("com.gluonhq.scenebuilder.") || "EmbeddedSceneBuilderContainer".equals(name);
    }

    public static List<Path> scan() {
        Class<?>[] callStack = getCallStack();
        if (callStack == null) {
            return List.of();
        }
        List<Entry<ModuleReference, ModuleLayer>> nonSystemModuleRefs = new ArrayList<>();
        for (Entry<ModuleReference, ModuleLayer> m : findModuleRefs(callStack)) {
            if (!isSystemModule(m.getKey())) {
                nonSystemModuleRefs.add(m);
            }
        }

        List<Path> paths = new ArrayList<>();
        for (Entry<ModuleReference, ModuleLayer> e : nonSystemModuleRefs) {
            ModuleReference ref = e.getKey();
            ref.location().ifPresent(uri -> paths.add(Path.of(uri)));
        }
        return paths;
    }
}
