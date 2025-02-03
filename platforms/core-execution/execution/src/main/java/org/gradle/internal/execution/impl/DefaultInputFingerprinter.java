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

package org.gradle.internal.execution.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import org.gradle.internal.execution.FileCollectionFingerprinter;
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.execution.FileNormalizationSpec;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.execution.UnitOfWork.ValueSupplier;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import java.util.function.Consumer;

public class DefaultInputFingerprinter implements InputFingerprinter {

    private final FileCollectionSnapshotter snapshotter;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final ValueSnapshotter valueSnapshotter;
    private final BuildOperationRunner buildOperationRunner;

    public DefaultInputFingerprinter(
        FileCollectionSnapshotter snapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ValueSnapshotter valueSnapshotter,
        BuildOperationRunner buildOperationRunner
    ) {
        this.snapshotter = snapshotter;
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.valueSnapshotter = valueSnapshotter;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public Result fingerprintInputProperties(
        ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints,
        ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints,
        Consumer<InputVisitor> inputs
    ) {
        InputCollectingVisitor visitor = new InputCollectingVisitor(previousValueSnapshots, previousFingerprints, snapshotter, fingerprinterRegistry, valueSnapshotter, knownCurrentValueSnapshots, knownCurrentFingerprints, buildOperationRunner);
        inputs.accept(visitor);
        return visitor.complete();
    }

    private static class InputCollectingVisitor implements InputVisitor {
        private final ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots;
        private final ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints;
        private final FileCollectionSnapshotter snapshotter;
        private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
        private final ValueSnapshotter valueSnapshotter;
        private final ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints;
        private final BuildOperationRunner buildOperationRunner;

        private final ImmutableSortedMap.Builder<String, ValueSnapshot> valueSnapshotsBuilder = ImmutableSortedMap.naturalOrder();
        private final ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> fingerprintsBuilder = ImmutableSortedMap.naturalOrder();
        private final ImmutableSet.Builder<String> propertiesRequiringIsEmptyCheck = ImmutableSet.builder();

        public InputCollectingVisitor(
            ImmutableSortedMap<String, ValueSnapshot> previousValueSnapshots,
            ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousFingerprints,
            FileCollectionSnapshotter snapshotter,
            FileCollectionFingerprinterRegistry fingerprinterRegistry,
            ValueSnapshotter valueSnapshotter,
            ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints,
            BuildOperationRunner buildOperationRunner
        ) {
            this.previousValueSnapshots = previousValueSnapshots;
            this.previousFingerprints = previousFingerprints;
            this.snapshotter = snapshotter;
            this.fingerprinterRegistry = fingerprinterRegistry;
            this.valueSnapshotter = valueSnapshotter;
            this.knownCurrentValueSnapshots = knownCurrentValueSnapshots;
            this.knownCurrentFingerprints = knownCurrentFingerprints;
            this.buildOperationRunner = buildOperationRunner;
        }

        @Override
        public void visitInputProperty(String propertyName, ValueSupplier value) {
            if (knownCurrentValueSnapshots.containsKey(propertyName)) {
                return;
            }
            Object actualValue = value.getValue();
            try {
                ValueSnapshot previousSnapshot = previousValueSnapshots.get(propertyName);
                if (previousSnapshot == null) {
                    valueSnapshotsBuilder.put(propertyName, valueSnapshotter.snapshot(actualValue));
                } else {
                    valueSnapshotsBuilder.put(propertyName, valueSnapshotter.snapshot(actualValue, previousSnapshot));
                }
            } catch (Exception e) {
                throw new InputFingerprintingException(
                    propertyName,
                    String.format("value '%s' cannot be serialized",
                    value.getValue()),
                    e);
            }
        }

        @Override
        public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
            if (knownCurrentFingerprints.containsKey(propertyName)) {
                return;
            }

            FileCollectionFingerprint previousFingerprint = previousFingerprints.get(propertyName);
            FileNormalizationSpec normalizationSpec = DefaultFileNormalizationSpec.from(
                value.getNormalizer(),
                value.getDirectorySensitivity(),
                value.getLineEndingNormalization());
            FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(normalizationSpec);
            try {
                FileCollectionSnapshotter.Result result = snapshotter.snapshot(value.getFiles());



                CurrentFileCollectionFingerprint fingerprint = buildOperationRunner.call(new CallableBuildOperation<CurrentFileCollectionFingerprint>() {
                    @Override
                    public CurrentFileCollectionFingerprint call(BuildOperationContext context) throws Exception {
                        return fingerprinter.fingerprint(result.getSnapshot(), previousFingerprint);
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Fingerprinting property " + propertyName + " " + Iterables.toString(value.getFiles()));
                    }
                });


                fingerprintsBuilder.put(propertyName, fingerprint);
                if (result.containsArchiveTrees()) {
                    propertiesRequiringIsEmptyCheck.add(propertyName);
                }
            } catch (Exception e) {
                throw new InputFileFingerprintingException(propertyName, e);
            }
        }

        public Result complete() {
            return new InputFingerprints(
                knownCurrentValueSnapshots,
                valueSnapshotsBuilder.build(),
                knownCurrentFingerprints,
                fingerprintsBuilder.build(),
                propertiesRequiringIsEmptyCheck.build());
        }
    }

    @VisibleForTesting
    public static class InputFingerprints implements InputFingerprinter.Result {
        private final ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots;
        private final ImmutableSortedMap<String, ValueSnapshot> valueSnapshots;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileFingerprints;
        private final ImmutableSet<String> propertiesRequiringIsEmptyCheck;

        public InputFingerprints(
            ImmutableSortedMap<String, ValueSnapshot> knownCurrentValueSnapshots,
            ImmutableSortedMap<String, ValueSnapshot> valueSnapshots,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownCurrentFingerprints,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileFingerprints,
            ImmutableSet<String> propertiesRequiringIsEmptyCheck
        ) {
            this.knownCurrentValueSnapshots = knownCurrentValueSnapshots;
            this.valueSnapshots = valueSnapshots;
            this.knownCurrentFingerprints = knownCurrentFingerprints;
            this.fileFingerprints = fileFingerprints;
            this.propertiesRequiringIsEmptyCheck = propertiesRequiringIsEmptyCheck;
        }

        @Override
        public ImmutableSortedMap<String, ValueSnapshot> getValueSnapshots() {
            return valueSnapshots;
        }

        @Override
        public ImmutableSortedMap<String, ValueSnapshot> getAllValueSnapshots() {
            return union(knownCurrentValueSnapshots, valueSnapshots);
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFileFingerprints() {
            return fileFingerprints;
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getAllFileFingerprints() {
            return union(knownCurrentFingerprints, fileFingerprints);
        }

        @Override
        public ImmutableSet<String> getPropertiesRequiringIsEmptyCheck() {
            return propertiesRequiringIsEmptyCheck;
        }

        private static <K extends Comparable<?>, V> ImmutableSortedMap<K, V> union(ImmutableSortedMap<K, V> a, ImmutableSortedMap<K, V> b) {
            if (a.isEmpty()) {
                return b;
            } else if (b.isEmpty()) {
                return a;
            } else {
                return ImmutableSortedMap.<K, V>naturalOrder()
                    .putAll(a)
                    .putAll(b)
                    .build();
            }
        }
    }
}
