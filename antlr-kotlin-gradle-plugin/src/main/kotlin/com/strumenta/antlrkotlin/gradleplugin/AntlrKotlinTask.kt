/*
 * Copyright 2010 the original author or authors.
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

package com.strumenta.antlrkotlin.gradleplugin

import com.strumenta.antlrkotlin.gradleplugin.internal.AntlrSourceGenerationException
import com.strumenta.antlrkotlin.gradleplugin.internal.AntlrSpec
import com.strumenta.antlrkotlin.gradleplugin.internal.AntlrWorkerManager
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.process.internal.worker.WorkerProcessFactory
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import javax.inject.Inject

/**
 * Generates parsers from Antlr grammars.
 */
@CacheableTask
abstract class AntlrKotlinTask @Inject constructor(private val workerProcessFactory: WorkerProcessFactory): DefaultTask() {
  /**
   * Specifies that all rules call `traceIn`/`traceOut`.
   */
  @get:Input var trace: Boolean = false

  /**
   * Specifies that all lexer rules call `traceIn`/`traceOut`.
   */
  @get:Input var traceLexer: Boolean = false

  /**
   * Specifies that all parser rules call `traceIn`/`traceOut`.
   */
  @get:Input var traceParser: Boolean = false

  /**
   * Specifies that all tree walker rules call `traceIn`/`traceOut`.
   */
  @get:Input var traceTreeWalker: Boolean = false

  /**
   * The maximum heap size for the forked antlr process (ex: '1g').
   */
  @get:Internal var maxHeapSize: String? = null

  /**
   * The package name of the generated files.
   */
  @get:Internal var packageName: String? = null

  /**
   * List of command-line argumets passed to the antlr process
   */
  @get:Input var arguments: List<String> = listOf()

  /**
   * The classpath containing the Ant ANTLR task implementation.
   */
  @get:Classpath abstract val antlrClasspath: ConfigurableFileCollection

  fun antlrClasspathDependencies(vararg dependencyNotation: Any) {
    antlrClasspath.from(project.configurations.detachedConfiguration(
      *Array(dependencyNotation.size) {
        project.dependencies.create(dependencyNotation[it])
      }
    ))
  }

  /**
   * Specifies the directory containing the source files for this task.
   */
  @get:InputDirectory
  @get:Incremental
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceDirectory: DirectoryProperty

  /**
   * Specifies the directory to generate the parser source files into.
   */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun execute(changes: InputChanges) {
    val fileChanges = changes.getFileChanges(sourceDirectory)
    val grammarFiles = when {
      fileChanges.any { it.changeType == ChangeType.REMOVED } -> {
        outputDirectory.asFile.get().deleteRecursively()
        sourceDirectory.asFileTree.matching {
          include("*.g4")
        }.files
      }
      else -> fileChanges.mapNotNullTo(mutableSetOf()) { change ->
        change.file.takeIf { file ->
          file.extension == "g4"
        }
      }
    }

    val manager = AntlrWorkerManager()
    logger.debug("AntlrWorkerManager created")
    val spec = createAntlrSpec(grammarFiles)
    logger.debug("AntlrSpec created")
    val result = manager.runWorker(project.projectDir, workerProcessFactory, antlrClasspath, spec)
    when {
      result.errorCount < 0 -> throw AntlrSourceGenerationException("There were errors during grammar generation", result.exception)
      result.errorCount == 1 -> throw AntlrSourceGenerationException("There was 1 error during grammar generation", result.exception)
      result.errorCount > 1 -> throw AntlrSourceGenerationException("There were ${result.errorCount} errors during grammar generation", result.exception)
    }
  }

  private fun createAntlrSpec(grammarFiles: Set<File>): AntlrSpec {
    val arguments = arguments.toMutableList()
    var outputDirectory = outputDirectory.asFile.get()

    packageName?.let {
      if ("-package" in arguments) return@let
      arguments += "-package"
      arguments += it
      outputDirectory = outputDirectory.resolve(it.replace('.', '/'))
    }

    if (trace && "-trace" !in arguments) arguments += "-trace"
    if (traceLexer && "-traceLexer" !in arguments) arguments += "-traceLexer"
    if (traceParser && "-traceParser" !in arguments) arguments += "-traceParser"
    if (traceTreeWalker && "-traceTreeWalker" !in arguments) arguments += "-traceTreeWalker"
    return AntlrSpec(arguments, grammarFiles, setOf(sourceDirectory.asFile.get()), outputDirectory, maxHeapSize)
  }
}