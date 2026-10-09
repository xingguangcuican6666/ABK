package com.abk.kernel.agent

import android.content.Context
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal class AbkAgentServer(
    private val context: Context,
    private val host: String,
    port: Int,
) : NanoHTTPD(host, port) {
    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        return try {
            val route = AbkAgentRoutes.parse(session.uri)
                ?: return jsonResponse(
                    status = Response.Status.NOT_FOUND,
                    payload = mapOf("error" to "route not found", "path" to session.uri),
                )
            when (route) {
                AbkAgentRoute.Health -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.health(context, listeningPort))
                }
                AbkAgentRoute.Session -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.session(context, host, listeningPort))
                }
                AbkAgentRoute.Runtime -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.runtime(context))
                }
                AbkAgentRoute.RootGrants -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.rootGrants(context))
                }
                AbkAgentRoute.KernelFeatures -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.kernelFeatures(context))
                }
                AbkAgentRoute.PackageList -> requireMethod(session, Method.GET) {
                    val packageType = decode(session.parameters["type"]?.firstOrNull().orEmpty())
                    jsonResponse(payload = mapOf("packages" to AbkAgentFacade.listPackages(context, packageType)))
                }
                AbkAgentRoute.PackageInfo -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val packages = body?.getAsJsonArray("packages")
                        ?.mapNotNull { element -> element?.asString?.trim()?.takeIf { it.isNotBlank() } }
                        .orEmpty()
                    jsonResponse(payload = mapOf("packages" to AbkAgentFacade.packageInfos(context, packages)))
                }
                is AbkAgentRoute.RootGrantAllow -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val allowed = body?.get("allowed")?.asBoolean ?: false
                    jsonResponse(payload = shellResultPayload(AbkAgentFacade.setRootGrantAllowed(context, decode(route.packageName), allowed)))
                }
                is AbkAgentRoute.RootGrantIcon -> requireMethod(session, Method.GET) {
                    val icon = AbkAgentFacade.readRootGrantIconPng(context, decode(route.packageName))
                        ?: return@requireMethod jsonResponse(
                            Response.Status.NOT_FOUND,
                            mapOf("error" to "icon not found", "packageName" to route.packageName),
                        )
                    binaryResponse(
                        bytes = icon,
                        contentType = "image/png",
                        fileName = "${decode(route.packageName)}.png",
                    )
                }
                is AbkAgentRoute.KernelFeatureSet -> requireMethod(session, Method.POST) {
                    val enabled = readJsonBody(session)?.get("enabled")?.asBoolean ?: false
                    jsonResponse(
                        payload = shellResultPayload(
                            AbkAgentFacade.setKernelFeatureEnabled(
                                context,
                                decode(route.featureId),
                                enabled,
                            ),
                        ),
                    )
                }
                AbkAgentRoute.InternalInsetsCss -> requireMethod(session, Method.GET) {
                    binaryResponse(
                        bytes = """
                            :root {
                              --ksu-safe-area-inset-top: 0px;
                              --ksu-safe-area-inset-right: 0px;
                              --ksu-safe-area-inset-bottom: 0px;
                              --ksu-safe-area-inset-left: 0px;
                            }
                        """.trimIndent().toByteArray(StandardCharsets.UTF_8),
                        contentType = "text/css; charset=utf-8",
                        fileName = "insets.css",
                    )
                }
                AbkAgentRoute.Susfs -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.susfs(context))
                }
                AbkAgentRoute.ApplySusfs -> requireMethod(session, Method.POST) {
                    val body = readBody(session)
                    if (body.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "request body missing"))
                    }
                    val config = runCatching {
                        gson.fromJson(body, SusfsConfig::class.java)
                    }.getOrNull() ?: return@requireMethod jsonResponse(
                        Response.Status.BAD_REQUEST,
                        mapOf("error" to "invalid susfs config json"),
                    )
                    acceptTask("susfs.apply") {
                        val result = AbkAgentFacade.applySusfsConfig(config) { line -> log(line) }
                        if (result.success) {
                            success(
                                message = "susfs applied",
                                result = mapOf(
                                    "shell" to shellResultPayload(result),
                                    "susfs" to AbkAgentFacade.susfs(context),
                                ),
                            )
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "susfs apply failed" }, appendMessage = false)
                        }
                    }
                }
                is AbkAgentRoute.RuntimeModuleEnable -> requireMethod(session, Method.POST) {
                    val enabled = readJsonBody(session)?.get("enabled")?.asBoolean ?: false
                    jsonResponse(payload = shellResultPayload(AbkAgentFacade.setRuntimeModuleEnabled(decode(route.moduleId), enabled)))
                }
                is AbkAgentRoute.RuntimeModulePendingUninstall -> requireMethod(session, Method.POST) {
                    val pending = readJsonBody(session)?.get("pending")?.asBoolean ?: false
                    jsonResponse(payload = shellResultPayload(AbkAgentFacade.setRuntimeModulePendingUninstall(decode(route.moduleId), pending)))
                }
                is AbkAgentRoute.RuntimeModuleAction -> requireMethod(session, Method.POST) {
                    acceptTask("runtime.module.action") {
                        val result = AbkAgentFacade.runRuntimeModuleAction(decode(route.moduleId)) { line -> log(line) }
                        if (result.success) {
                            success(
                                message = "module action complete",
                                result = shellResultPayload(result),
                            )
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "module action failed" }, appendMessage = false)
                        }
                    }
                }
                is AbkAgentRoute.RuntimeModuleWebUiFiles -> requireMethod(session, Method.GET) {
                    val bytes = AbkAgentFacade.readRuntimeModuleWebResource(
                        moduleId = decode(route.moduleId),
                        relativePath = route.relativePath?.let(::decode),
                    )
                        ?: return@requireMethod jsonResponse(
                            Response.Status.NOT_FOUND,
                            mapOf(
                                "error" to "module webui resource not found",
                                "moduleId" to route.moduleId,
                                "relativePath" to route.relativePath,
                            ),
                        )
                    binaryResponse(
                        bytes = bytes,
                        contentType = webUiContentType(route.relativePath),
                        fileName = webUiFileName(route.relativePath),
                    )
                }
                is AbkAgentRoute.RuntimeModuleWebUiHttpProxy -> proxyModuleWebUiRequest(
                    session = session,
                    moduleId = decode(route.moduleId),
                )
                is AbkAgentRoute.RuntimeModuleWebUiExec -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val command = body?.get("command")?.asString.orEmpty()
                    val optionsJson = body?.get("options")?.toString()
                    jsonResponse(
                        payload = webUiShellResultPayload(
                            AbkAgentFacade.executeRuntimeModuleWebCommand(
                                moduleId = decode(route.moduleId),
                                command = command,
                                optionsJson = optionsJson,
                            ),
                        ),
                    )
                }
                is AbkAgentRoute.RuntimeModuleWebUiSpawn -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val command = body?.get("command")?.asString.orEmpty()
                    val argsJson = body?.get("args")?.toString()
                    val optionsJson = body?.get("options")?.toString()
                    jsonResponse(
                        payload = webUiShellResultPayload(
                            AbkAgentFacade.spawnRuntimeModuleWebCommand(
                                moduleId = decode(route.moduleId),
                                command = command,
                                argsJson = argsJson,
                                optionsJson = optionsJson,
                            ),
                        ),
                    )
                }
                is AbkAgentRoute.RuntimeModuleWebUiModuleInfo -> requireMethod(session, Method.GET) {
                    jsonResponse(
                        payload = mapOf(
                            "raw" to AbkAgentFacade.runtimeModuleWebInfoJson(decode(route.moduleId)),
                        ),
                    )
                }
                AbkAgentRoute.InstallModule -> requireMethod(session, Method.POST) {
                    val path = readJsonBody(session)?.get("zipPath")?.asString.orEmpty()
                    if (path.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "zipPath missing"))
                    }
                    acceptTask("install.module") {
                        val result = AbkAgentFacade.installModule(path) { line -> log(line) }
                        if (result.success) {
                            success(message = "module installed", result = shellResultPayload(result))
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "module install failed" })
                        }
                    }
                }
                AbkAgentRoute.InstallApk -> requireMethod(session, Method.POST) {
                    val path = readJsonBody(session)?.get("apkPath")?.asString.orEmpty()
                    if (path.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "apkPath missing"))
                    }
                    acceptTask("install.apk") {
                        val result = AbkAgentFacade.installApk(context, path) { line -> log(line) }
                        if (result.success) {
                            success(message = "apk installed", result = shellResultPayload(result))
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "apk install failed" })
                        }
                    }
                }
                AbkAgentRoute.FlashImage -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val imagePath = body?.get("imagePath")?.asString.orEmpty()
                    val partition = body?.get("partition")?.asString?.ifBlank { "boot" } ?: "boot"
                    if (imagePath.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "imagePath missing"))
                    }
                    acceptTask("flash.image") {
                        val result = AbkAgentFacade.flashImage(imagePath, partition) { line -> log(line) }
                        if (result.success) {
                            success(message = "image flashed", result = shellResultPayload(result))
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "flash failed" })
                        }
                    }
                }
                AbkAgentRoute.ExportDiagnostics -> requireMethod(session, Method.POST) {
                    acceptTask("diagnostics.export") {
                        val (zipFile, warnings) = AbkAgentFacade.exportDiagnostics(context)
                        success(
                            message = "diagnostics exported",
                            result = mapOf("warnings" to warnings),
                            download = AbkAgentDownload(
                                file = zipFile,
                                fileName = zipFile.name,
                                contentType = "application/zip",
                            ),
                        )
                    }
                }
                is AbkAgentRoute.Task -> requireMethod(session, Method.GET) {
                    val snapshot = AbkAgentTaskStore.get(decode(route.taskId))
                        ?: return@requireMethod jsonResponse(
                            Response.Status.NOT_FOUND,
                            mapOf("error" to "task not found", "taskId" to route.taskId),
                        )
                    jsonResponse(payload = snapshot)
                }
                is AbkAgentRoute.TaskDownload -> requireMethod(session, Method.GET) {
                    val download = AbkAgentTaskStore.getDownload(decode(route.taskId))
                        ?: return@requireMethod jsonResponse(
                            Response.Status.NOT_FOUND,
                            mapOf("error" to "task download not available", "taskId" to route.taskId),
                        )
                    fileResponse(download)
                }
            }
        } catch (error: Exception) {
            jsonResponse(
                status = Response.Status.INTERNAL_ERROR,
                payload = mapOf(
                    "error" to (error.message ?: error::class.java.simpleName),
                ),
            )
        }
    }

    private fun requireMethod(
        session: IHTTPSession,
        method: Method,
        handler: () -> Response,
    ): Response {
        return if (session.method == method) {
            handler()
        } else {
            jsonResponse(
                status = Response.Status.METHOD_NOT_ALLOWED,
                payload = mapOf("error" to "method not allowed", "expected" to method.name),
            )
        }
    }

    private fun acceptTask(
        kind: String,
        operation: suspend AbkAgentTaskStore.AbkAgentTaskHandle.() -> Unit,
    ): Response {
        val snapshot = AbkAgentTaskStore.submit(kind, operation)
        return jsonResponse(
            status = Response.Status.ACCEPTED,
            payload = snapshot,
        )
    }

    private fun readJsonBody(session: IHTTPSession): JsonObject? {
        val body = readBody(session)
        if (body.isBlank()) return null
        return runCatching {
            JsonParser.parseString(body).asJsonObject
        }.getOrNull()
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"].orEmpty()
    }

    private fun jsonResponse(
        status: Response.IStatus = Response.Status.OK,
        payload: Any,
    ): Response {
        val response = newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            gson.toJson(payload),
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun fileResponse(download: AbkAgentDownload): Response {
        val response = newChunkedResponse(
            Response.Status.OK,
            download.contentType,
            FileInputStream(download.file),
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"${download.fileName}\"")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun binaryResponse(
        bytes: ByteArray,
        contentType: String,
        fileName: String,
    ): Response {
        val response = newChunkedResponse(
            Response.Status.OK,
            contentType,
            ByteArrayInputStream(bytes),
        )
        response.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun proxyModuleWebUiRequest(
        session: IHTTPSession,
        moduleId: String,
    ): Response {
        if (!RootUtils.isSafeModuleIdForPath(moduleId)) {
            return jsonResponse(
                status = Response.Status.BAD_REQUEST,
                payload = mapOf("error" to "invalid module id"),
            )
        }
        val target = decode(session.parameters["target"]?.firstOrNull().orEmpty())
        if (target.isBlank()) {
            return jsonResponse(
                status = Response.Status.BAD_REQUEST,
                payload = mapOf("error" to "target missing"),
            )
        }
        val url = runCatching { URL(target) }.getOrNull()
            ?: return jsonResponse(
                status = Response.Status.BAD_REQUEST,
                payload = mapOf("error" to "invalid target url", "target" to target),
            )
        if (!isAllowedLocalWebUiTarget(url)) {
            return jsonResponse(
                status = Response.Status.FORBIDDEN,
                payload = mapOf("error" to "target host forbidden", "target" to target),
            )
        }

        val connection = (url.openConnection() as? HttpURLConnection)
            ?: return jsonResponse(
                status = Response.Status.BAD_REQUEST,
                payload = mapOf("error" to "unsupported target protocol", "target" to target),
            )
        return try {
            connection.requestMethod = session.method.name
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doInput = true
            copyProxyRequestHeaders(session, connection)

            val body = if (session.method == Method.POST || session.method == Method.PUT) {
                readBody(session)
            } else {
                ""
            }
            if (body.isNotEmpty()) {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val statusCode = connection.responseCode.takeIf { it > 0 } ?: 502
            val stream = connection.errorStream ?: connection.inputStream
                ?: ByteArrayInputStream(ByteArray(0))
            val response = newChunkedResponse(
                proxyStatus(statusCode),
                connection.contentType ?: "application/octet-stream",
                stream,
            )
            response.addHeader("Cache-Control", "no-store")
            response.addHeader("Access-Control-Allow-Origin", "*")
            connection.headerFields
                .filterKeys { key -> !key.isNullOrBlank() }
                .forEach { (key, values) ->
                    values?.forEach { value ->
                        if (!value.isNullOrBlank() && shouldForwardProxyResponseHeader(key)) {
                            response.addHeader(key, value)
                        }
                    }
                }
            response
        } catch (error: Exception) {
            connection.disconnect()
            jsonResponse(
                status = proxyStatus(502),
                payload = mapOf(
                    "error" to (error.message ?: error::class.java.simpleName),
                    "target" to target,
                ),
            )
        }
    }

    private fun shellResultPayload(result: RootUtils.ShellResult): Map<String, Any> = mapOf(
        "success" to result.success,
        "output" to result.output,
    )

    private fun webUiShellResultPayload(result: RootUtils.ShellResult): Map<String, Any> {
        val stdout = result.output.joinToString("\n")
        return mapOf(
            "success" to result.success,
            "code" to if (result.success) 0 else 1,
            "stdout" to stdout,
            "output" to result.output,
        )
    }

    private fun webUiContentType(relativePath: String?): String {
        val clean = relativePath
            ?.substringAfterLast('/')
            ?.lowercase()
            ?.ifBlank { "index.html" }
            ?: "index.html"
        return when {
            clean.endsWith(".html") || clean.endsWith(".htm") -> "text/html; charset=utf-8"
            clean.endsWith(".js") -> "application/javascript; charset=utf-8"
            clean.endsWith(".mjs") -> "application/javascript; charset=utf-8"
            clean.endsWith(".css") -> "text/css; charset=utf-8"
            clean.endsWith(".json") -> "application/json; charset=utf-8"
            clean.endsWith(".svg") -> "image/svg+xml"
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
            clean.endsWith(".gif") -> "image/gif"
            clean.endsWith(".webp") -> "image/webp"
            clean.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    private fun webUiFileName(relativePath: String?): String =
        relativePath
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "index.html"

    private fun isAllowedLocalWebUiTarget(url: URL): Boolean {
        val protocol = url.protocol?.lowercase().orEmpty()
        if (protocol != "http" && protocol != "https") return false
        val host = url.host?.trim()?.lowercase().orEmpty()
        return host == "127.0.0.1" ||
            host == "localhost" ||
            host == "::1" ||
            host == "[::1]" ||
            host == "0.0.0.0"
    }

    private fun copyProxyRequestHeaders(session: IHTTPSession, connection: HttpURLConnection) {
        session.headers.forEach { (key, value) ->
            if (key.isNullOrBlank() || value.isNullOrBlank()) return@forEach
            if (!shouldForwardProxyRequestHeader(key)) return@forEach
            connection.setRequestProperty(key, value)
        }
    }

    private fun shouldForwardProxyRequestHeader(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized !in setOf(
            "host",
            "connection",
            "content-length",
            "accept-encoding",
            "origin",
            "referer",
        )
    }

    private fun shouldForwardProxyResponseHeader(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized !in setOf(
            "transfer-encoding",
            "connection",
            "content-length",
            "content-encoding",
            "access-control-allow-origin",
        )
    }

    private fun proxyStatus(code: Int): Response.IStatus = object : Response.IStatus {
        override fun getRequestStatus(): Int = code

        override fun getDescription(): String = "$code Proxy"
    }

    private fun decode(raw: String): String =
        URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
}
