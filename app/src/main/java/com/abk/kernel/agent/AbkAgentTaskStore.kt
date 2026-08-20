package com.abk.kernel.agent

import com.google.gson.Gson
import com.google.gson.JsonElement
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class AbkAgentTaskSnapshot(
    val id: String,
    val kind: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val message: String? = null,
    val output: List<String> = emptyList(),
    val result: JsonElement? = null,
    val downloadName: String? = null,
    val downloadContentType: String? = null,
)

internal data class AbkAgentDownload(
    val file: File,
    val fileName: String,
    val contentType: String,
)

internal object AbkAgentTaskStore {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = ConcurrentHashMap<String, MutableTask>()

    fun submit(
        kind: String,
        operation: suspend AbkAgentTaskHandle.() -> Unit,
    ): AbkAgentTaskSnapshot {
        val task = MutableTask(
            id = UUID.randomUUID().toString(),
            kind = kind,
            createdAt = System.currentTimeMillis(),
        )
        tasks[task.id] = task
        pruneCompletedTasks()
        scope.launch {
            val handle = AbkAgentTaskHandle(task)
            handle.markRunning()
            try {
                handle.operation()
                handle.ensureCompleted()
            } catch (error: Throwable) {
                handle.fail(
                    error.message ?: error::class.java.simpleName,
                    appendMessage = true,
                )
            }
        }
        return task.snapshot()
    }

    fun get(taskId: String): AbkAgentTaskSnapshot? = tasks[taskId]?.snapshot()

    fun getDownload(taskId: String): AbkAgentDownload? = tasks[taskId]?.download()

    private fun pruneCompletedTasks(limit: Int = 64) {
        val completed = tasks.values
            .map { it.snapshot() }
            .filter { it.state == TASK_SUCCEEDED || it.state == TASK_FAILED }
            .sortedByDescending { it.updatedAt }
        if (completed.size <= limit) return
        completed.drop(limit).forEach { snapshot ->
            tasks.remove(snapshot.id)
        }
    }

    internal class MutableTask(
        val id: String,
        val kind: String,
        val createdAt: Long,
    ) {
        private val output = mutableListOf<String>()
        private var state: String = TASK_PENDING
        private var updatedAt: Long = createdAt
        private var message: String? = null
        private var result: JsonElement? = null
        private var downloadFile: File? = null
        private var downloadName: String? = null
        private var downloadContentType: String? = null

        @Synchronized
        fun setRunning() {
            state = TASK_RUNNING
            updatedAt = System.currentTimeMillis()
        }

        @Synchronized
        fun append(line: String) {
            if (line.isBlank()) return
            output += line
            updatedAt = System.currentTimeMillis()
        }

        @Synchronized
        fun complete(
            state: String,
            message: String?,
            result: JsonElement?,
            download: AbkAgentDownload?,
        ) {
            this.state = state
            this.message = message
            this.result = result
            this.downloadFile = download?.file
            this.downloadName = download?.fileName
            this.downloadContentType = download?.contentType
            this.updatedAt = System.currentTimeMillis()
        }

        @Synchronized
        fun snapshot(): AbkAgentTaskSnapshot = AbkAgentTaskSnapshot(
            id = id,
            kind = kind,
            state = state,
            createdAt = createdAt,
            updatedAt = updatedAt,
            message = message,
            output = output.toList(),
            result = result,
            downloadName = downloadName,
            downloadContentType = downloadContentType,
        )

        @Synchronized
        fun download(): AbkAgentDownload? {
            val file = downloadFile ?: return null
            val name = downloadName ?: file.name
            val contentType = downloadContentType ?: "application/octet-stream"
            if (!file.isFile) return null
            return AbkAgentDownload(file, name, contentType)
        }
    }

    internal class AbkAgentTaskHandle(
        private val task: MutableTask,
    ) {
        fun markRunning() {
            task.setRunning()
        }

        fun log(line: String) {
            task.append(line)
        }

        fun success(
            message: String? = null,
            result: Any? = null,
            download: AbkAgentDownload? = null,
        ) {
            task.complete(
                state = TASK_SUCCEEDED,
                message = message,
                result = result?.let { gson.toJsonTree(it) },
                download = download,
            )
        }

        fun fail(
            message: String,
            appendMessage: Boolean = false,
        ) {
            if (appendMessage) {
                task.append(message)
            }
            task.complete(
                state = TASK_FAILED,
                message = message,
                result = null,
                download = null,
            )
        }

        fun ensureCompleted() {
            val snapshot = task.snapshot()
            if (snapshot.state == TASK_RUNNING || snapshot.state == TASK_PENDING) {
                success()
            }
        }
    }

    private const val TASK_PENDING = "pending"
    private const val TASK_RUNNING = "running"
    private const val TASK_SUCCEEDED = "succeeded"
    private const val TASK_FAILED = "failed"
}
