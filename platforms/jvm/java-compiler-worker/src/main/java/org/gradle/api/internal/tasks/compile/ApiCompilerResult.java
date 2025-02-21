/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.workers.internal.DefaultWorkResult;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ApiCompilerResult extends DefaultWorkResult implements Serializable {

    private final AnnotationProcessingResult annotationProcessingResult = new AnnotationProcessingResult();
    private final ConstantsAnalysisResult constantsAnalysisResult = new ConstantsAnalysisResult();
    private final Map<String, Set<String>> sourceToClassMapping = new HashMap<>();
    private final Map<String, String> backupClassFiles = new HashMap<>();

    public ApiCompilerResult() {
        super(true, null);
    }

    public AnnotationProcessingResult getAnnotationProcessingResult() {
        return annotationProcessingResult;
    }

    public ConstantsAnalysisResult getConstantsAnalysisResult() {
        return constantsAnalysisResult;
    }

    public Map<String, Set<String>> getSourceClassesMapping() {
        return sourceToClassMapping;
    }

    public Map<String, String> getBackupClassFiles() {
        return backupClassFiles;
    }
}
