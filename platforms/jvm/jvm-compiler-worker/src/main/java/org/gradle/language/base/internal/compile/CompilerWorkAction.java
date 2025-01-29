/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.base.internal.compile;

import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.workers.WorkAction;
import org.gradle.workers.internal.ProvidesWorkResult;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Instantiated in a separate compiler daemon process and executes a {@link WorkerCompiler} implementation.
 *
 * @see org.gradle.api.internal.tasks.compile.daemon.AbstractIsolatedCompilerWorkerExecutor
 */
public class CompilerWorkAction implements WorkAction<CompilerParameters>, ProvidesWorkResult {

    private CompilerWorkResult workResult;
    private final CompilerParameters parameters;
    private final Instantiator instantiator;

    @Inject
    public CompilerWorkAction(CompilerParameters parameters, Instantiator instantiator) {
        this.parameters = parameters;
        this.instantiator = instantiator;
    }

    @Override
    // We construct this object differently than the public API does.
    // Perhaps we should use the public API to construct this object.
    @SuppressWarnings("OverridesJavaxInjectableMethod")
    public CompilerParameters getParameters() {
        return parameters;
    }

    @Override
    public void execute() {
        Class<? extends WorkerCompiler<?>> compilerClass = Cast.uncheckedCast(ClassLoaderUtils.classFromContextLoader(getParameters().getCompilerClassName()));
        WorkerCompiler<?> compiler = instantiator.newInstance(compilerClass, getParameters().getCompilerInstanceParameters());
        this.workResult = compiler.execute(Cast.uncheckedCast(getParameters().getCompileSpec()));
    }

    @Override
    public boolean getDidWork() {
        return workResult.getDidWork();
    }

    @Nullable
    @Override
    public Throwable getException() {
        return workResult.getException();
    }

}
