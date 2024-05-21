package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import org.gradle.tooling.BuildAction
import kotlin.reflect.KClass

interface GetModelAction<T : Any> {

    val displayName: String
        get() = "Get ${modelType.simpleName}"

    val modelType: KClass<T>

    @Composable
    fun ColumnScope.ModelContent(model: T)

    interface GetCompositeModelAction<T : Any> : GetModelAction<T> {

        val buildAction : BuildAction<T>

        override val displayName: String
            get() = "Run ${buildAction::class.simpleName}"
    }
}
