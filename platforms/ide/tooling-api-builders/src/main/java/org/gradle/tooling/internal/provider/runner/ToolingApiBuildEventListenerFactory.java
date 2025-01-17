/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.OperationResultPostProcessor;
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.daemon.client.serialization.ClasspathInferer;
import org.gradle.internal.daemon.client.serialization.ClientSidePayloadClassLoaderFactory;
import org.gradle.internal.daemon.client.serialization.ClientSidePayloadClassLoaderRegistry;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.ModelClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;
import org.gradle.workers.internal.IsolatableSerializerRegistry;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

@NonNullApi
public class ToolingApiBuildEventListenerFactory implements BuildEventListenerFactory {
    private final BuildOperationAncestryTracker ancestryTracker;
    private final BuildOperationIdFactory idFactory;
    private final List<OperationResultPostProcessorFactory> postProcessorFactories;
    private final IsolatableSerializerRegistry isolatableSerializerRegistry;

    ToolingApiBuildEventListenerFactory(BuildOperationAncestryTracker ancestryTracker, BuildOperationIdFactory idFactory, List<OperationResultPostProcessorFactory> postProcessorFactories, IsolatableSerializerRegistry isolatableSerializerRegistry) {
        this.ancestryTracker = ancestryTracker;
        this.idFactory = idFactory;
        this.postProcessorFactories = postProcessorFactories;
        this.isolatableSerializerRegistry = isolatableSerializerRegistry;
    }

    @Override
    public Iterable<Object> createListeners(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
        if (!subscriptions.isAnyOperationTypeRequested()) {
            return ImmutableList.of();
        }

        ProgressEventConsumer progressEventConsumer = new ProgressEventConsumer(consumer, ancestryTracker);

        ImmutableList.Builder<Object> listeners = ImmutableList.builder();

        if (subscriptions.isRequested(OperationType.TEST) && subscriptions.isRequested(OperationType.TEST_OUTPUT)) {
            listeners.add(new ClientForwardingTestOutputOperationListener(progressEventConsumer, idFactory));
        }

        if (subscriptions.isRequested(OperationType.TEST) && subscriptions.isRequested(OperationType.TEST_METADATA)) {
            listeners.add(new ClientForwardingTestMetadataOperationListener(progressEventConsumer, idFactory));
        }

        if (subscriptions.isRequested(OperationType.BUILD_PHASE)) {
            listeners.add(new BuildPhaseOperationListener(progressEventConsumer, idFactory));
        }

        listeners.add(createClientBuildEventGenerator(subscriptions, consumer, progressEventConsumer));
        return listeners.build();
    }

    public ProblemsProgressEventUtils createProblemProgressEventUtils() {
        ClassLoaderCache classLoaderCache = new ClassLoaderCache();
        PayloadSerializer payloadSerializer = new PayloadSerializer(
            new WellKnownClassLoaderRegistry(
                new ClientSidePayloadClassLoaderRegistry(
                    new DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        new ClientSidePayloadClassLoaderFactory(
                            new ModelClassLoaderFactory())),
                    new ClasspathInferer(),
                    classLoaderCache)));
        return new ProblemsProgressEventUtils(payloadSerializer, isolatableSerializerRegistry);
    }

    private ClientBuildEventGenerator createClientBuildEventGenerator(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer, ProgressEventConsumer progressEventConsumer) {
        ProblemsProgressEventUtils problemProgressEventUtils = createProblemProgressEventUtils();
        BuildOperationListener buildListener = createBuildOperationListener(subscriptions, progressEventConsumer, problemProgressEventUtils);

        OperationDependenciesResolver operationDependenciesResolver = new OperationDependenciesResolver();

        PluginApplicationTracker pluginApplicationTracker = new PluginApplicationTracker(ancestryTracker);
        TaskForTestEventTracker testTaskTracker = new TaskForTestEventTracker(ancestryTracker);
        ProjectConfigurationTracker projectConfigurationTracker = new ProjectConfigurationTracker(ancestryTracker, pluginApplicationTracker);
        TaskOriginTracker taskOriginTracker = new TaskOriginTracker(pluginApplicationTracker);

        TransformOperationMapper transformOperationMapper = new TransformOperationMapper(operationDependenciesResolver, problemProgressEventUtils);
        operationDependenciesResolver.addLookup(transformOperationMapper);

        List<OperationResultPostProcessor> postProcessors = createPostProcessors(subscriptions, consumer);

        TaskOperationMapper taskOperationMapper = new TaskOperationMapper(postProcessors, taskOriginTracker, operationDependenciesResolver);
        operationDependenciesResolver.addLookup(taskOperationMapper);

        List<BuildOperationMapper<?, ?>> mappers = ImmutableList.of(
            new FileDownloadOperationMapper(),
            new TestOperationMapper(testTaskTracker),
            new ProjectConfigurationOperationMapper(projectConfigurationTracker),
            taskOperationMapper,
            transformOperationMapper,
            new WorkItemOperationMapper(problemProgressEventUtils)
        );
        return new ClientBuildEventGenerator(progressEventConsumer, subscriptions, mappers, buildListener);
    }

    private List<OperationResultPostProcessor> createPostProcessors(BuildEventSubscriptions subscriptions, BuildEventConsumer consumer) {
        return postProcessorFactories.stream()
            .map(factory -> factory.createProcessors(subscriptions, consumer))
            .flatMap(List::stream)
            .collect(toImmutableList());
    }

    private BuildOperationListener createBuildOperationListener(BuildEventSubscriptions subscriptions, ProgressEventConsumer progressEventConsumer, ProblemsProgressEventUtils problemsProgressEventUtils) {
        // TODO (donat) think of a better name for this class
        return new ClientForwardingBuildOperationListener(progressEventConsumer, subscriptions, () -> new OperationIdentifier(idFactory.nextId()), problemsProgressEventUtils);
    }
}
