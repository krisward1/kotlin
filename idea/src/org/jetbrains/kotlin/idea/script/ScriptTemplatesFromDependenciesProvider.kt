/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.script

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionSourceAsContributor
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_PATH
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration


class ScriptTemplatesFromDependenciesProvider(private val project: Project) : ScriptDefinitionSourceAsContributor {
    override val id = "ScriptTemplatesFromDependenciesProvider"

    override fun isReady(): Boolean = _definitions != null

    override val definitions: Sequence<ScriptDefinition>
        get() {
            definitionsLock.withLock {
                if (_definitions != null) {
                    return _definitions!!.asSequence()
                }
            }

            forceStartUpdate = false
            asyncRunUpdateScriptTemplates()
            return emptySequence()
        }

    init {
        val connection = project.messageBus.connect()
        connection.subscribe(
            ProjectTopics.PROJECT_ROOTS,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    if (project.isInitialized) {
                        forceStartUpdate = true
                        asyncRunUpdateScriptTemplates()
                    }
                }
            },
        )
    }

    private fun asyncRunUpdateScriptTemplates() {
        definitionsLock.withLock {
            if (!forceStartUpdate && _definitions != null) return
        }

        inProgressLock.withLock {
            if (!inProgress) {
                inProgress = true

                loadScriptDefinitions()
            }
        }
    }

    private var _definitions: List<ScriptDefinition>? = null
    private val definitionsLock = ReentrantLock()

    private var oldTemplates: TemplatesWithCp? = null

    private data class TemplatesWithCp(
        val templates: List<String>,
        val classpath: List<File>,
    )

    private var inProgress = false
    private val inProgressLock = ReentrantLock()

    @Volatile
    private var forceStartUpdate = false

    private fun loadScriptDefinitions() {
        if (ApplicationManager.getApplication().isUnitTestMode || project.isDefault) {
            return onEarlyEnd()
        }

        val templates = LinkedHashSet<String>()
        val classpath = LinkedHashSet<File>()

        ReadAction
            .nonBlocking<List<VirtualFile>> {
                val fileManager = VirtualFileManager.getInstance()
                FileBasedIndex.getInstance().getAllKeys(ScriptTemplatesClassRootsIndex.KEY, project).mapNotNull {
                    val vFile = fileManager.findFileByUrl(it)

                    // see SCRIPT_DEFINITION_MARKERS_PATH
                    vFile?.parent?.parent?.parent?.parent
                }
            }
            .inSmartMode(project)
            .expireWith(project)
            .submit(AppExecutorUtil.getAppExecutorService())
            .onSuccess { roots ->
                val jarFS = JarFileSystem.getInstance()
                roots.forEach { root ->
                    root.findFileByRelativePath(SCRIPT_DEFINITION_MARKERS_PATH)?.children?.forEach { resourceFile ->
                        if (resourceFile.isValid && !resourceFile.isDirectory) {
                            templates.add(resourceFile.name.removeSuffix(SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT))
                        }
                    }

                    val templateSource = jarFS.getVirtualFileForJar(root) ?: root
                    val module = ProjectFileIndex.getInstance(project).getModuleForFile(templateSource) ?: return@forEach

                    // assuming that all libraries are placed into classes roots
                    // TODO: extract exact library dependencies instead of putting all module dependencies into classpath
                    // minimizing the classpath needed to use the template by taking cp only from modules with new templates found
                    // on the other hand the approach may fail if some module contains a template without proper classpath, while
                    // the other has properly configured classpath, so assuming that the dependencies are set correctly everywhere
                    classpath.addAll(
                        OrderEnumerator.orderEntries(module).withoutSdk().classesRoots.mapNotNull {
                            it.canonicalPath?.removeSuffix("!/").let(::File)
                        }
                    )
                }
            }
            .onProcessed {
                if (templates.isEmpty()) return@onProcessed onEarlyEnd()

                val newTemplates = TemplatesWithCp(templates.toList(), classpath.toList())
                if (newTemplates == oldTemplates) {
                    inProgressLock.withLock {
                        inProgress = false
                    }

                    return@onProcessed
                }

                oldTemplates = newTemplates

                val hostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                    getEnvironment {
                        mapOf(
                            "projectRoot" to (project.basePath ?: project.baseDir.canonicalPath)?.let(::File),
                        )
                    }
                }

                val newDefinitions = loadDefinitionsFromTemplates(
                    templateClassNames = newTemplates.templates,
                    templateClasspath = newTemplates.classpath,
                    baseHostConfiguration = hostConfiguration,
                )

                val needReload = definitionsLock.withLock {
                    if (newDefinitions != _definitions) {
                        _definitions = newDefinitions
                        return@withLock true
                    }
                    return@withLock false
                }

                if (needReload) {
                    ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this@ScriptTemplatesFromDependenciesProvider)
                }

                inProgressLock.withLock {
                    inProgress = false
                }
            }
    }

    private fun onEarlyEnd() {
        definitionsLock.withLock {
            _definitions = emptyList()
        }
        inProgressLock.withLock {
            inProgress = false
        }
    }
}