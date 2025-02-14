package flank.corellium.adapter

import flank.corellium.api.AndroidApps
import flank.corellium.client.agent.disconnect
import flank.corellium.client.agent.uploadFile
import flank.corellium.client.console.close
import flank.corellium.client.console.sendCommand
import flank.corellium.client.core.connectAgent
import flank.corellium.client.core.connectConsole
import flank.corellium.client.core.getAllProjects
import flank.corellium.client.core.getProjectInstancesList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

private const val PATH_TO_UPLOAD = "/sdcard"

fun installAndroidApps(
    projectName: String
) = AndroidApps.Install { apps ->
    corellium.launch {
        val projectId = corellium.getAllProjects().first { it.name == projectName }.id
        val instances = corellium.getProjectInstancesList(projectId).associateBy { it.id }

        apps.forEach { apps ->
            val instance = instances.getValue(apps.instanceId)

            println("Connecting agent for ${apps.instanceId}")
            val agentInfo = requireNotNull(instance.agent?.info) {
                "Cannot connect to the agent, no agent info for instance ${instance.name} with id: ${instance.id}"
            }
            val agent = corellium.connectAgent(agentInfo)

            println("Connecting console for ${apps.instanceId}")
            val console = corellium.connectConsole(instance.id)

            // Disable system logging
            flowOf("su", "dmesg -n 1", "exit").collect(console::sendCommand)

            apps.paths.forEach { localPath ->
                val file = File(localPath)
                val remotePath = "$PATH_TO_UPLOAD/${file.name}"

                println("Uploading apk $localPath")
                agent.uploadFile(remotePath, file.readBytes())

                println("Installing apk $localPath")
                console.sendCommand(
                    // Current solution is enough for the MVP.
                    // Fixme: Find better solution for recognizing test apk.
                    if (localPath.endsWith("androidTest.apk"))
                        "pm install -t $remotePath" else
                        "pm install $remotePath"
                )
            }

            console.close()
            agent.disconnect()
        }
    }
}
