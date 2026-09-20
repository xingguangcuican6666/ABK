package com.abk.kernel.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbkAgentRoutesTest {
    @Test
    fun parsesRuntimeModuleActionRoute() {
        val route = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/action")
        assertTrue(route is AbkAgentRoute.RuntimeModuleAction)
        assertEquals("meta-abk-mount", (route as AbkAgentRoute.RuntimeModuleAction).moduleId)
    }

    @Test
    fun parsesTaskDownloadRoute() {
        val route = AbkAgentRoutes.parse("/api/v1/tasks/abc-123/download")
        assertTrue(route is AbkAgentRoute.TaskDownload)
        assertEquals("abc-123", (route as AbkAgentRoute.TaskDownload).taskId)
    }

    @Test
    fun parsesRootGrantIconRoute() {
        val route = AbkAgentRoutes.parse("/api/v1/root-grants/com.example.app/icon")
        assertTrue(route is AbkAgentRoute.RootGrantIcon)
        assertEquals("com.example.app", (route as AbkAgentRoute.RootGrantIcon).packageName)
    }

    @Test
    fun parsesPackageAndInternalRoutes() {
        assertTrue(AbkAgentRoutes.parse("/api/v1/packages") is AbkAgentRoute.PackageList)
        assertTrue(AbkAgentRoutes.parse("/api/v1/packages/info") is AbkAgentRoute.PackageInfo)
        assertTrue(AbkAgentRoutes.parse("/internal/insets.css") is AbkAgentRoute.InternalInsetsCss)
    }

    @Test
    fun parsesRuntimeModuleWebUiRoutes() {
        val files = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/webui/files/assets/app.js")
        assertTrue(files is AbkAgentRoute.RuntimeModuleWebUiFiles)
        files as AbkAgentRoute.RuntimeModuleWebUiFiles
        assertEquals("meta-abk-mount", files.moduleId)
        assertEquals("assets/app.js", files.relativePath)

        val root = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/webui/files")
        assertTrue(root is AbkAgentRoute.RuntimeModuleWebUiFiles)
        assertNull((root as AbkAgentRoute.RuntimeModuleWebUiFiles).relativePath)

        val exec = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/webui/exec")
        assertTrue(exec is AbkAgentRoute.RuntimeModuleWebUiExec)
        assertEquals("meta-abk-mount", (exec as AbkAgentRoute.RuntimeModuleWebUiExec).moduleId)

        val spawn = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/webui/spawn")
        assertTrue(spawn is AbkAgentRoute.RuntimeModuleWebUiSpawn)
        assertEquals("meta-abk-mount", (spawn as AbkAgentRoute.RuntimeModuleWebUiSpawn).moduleId)

        val info = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/webui/module-info")
        assertTrue(info is AbkAgentRoute.RuntimeModuleWebUiModuleInfo)
        assertEquals("meta-abk-mount", (info as AbkAgentRoute.RuntimeModuleWebUiModuleInfo).moduleId)
    }
}
