package dev.nateweisz.bytestore.project.build

import dev.nateweisz.bytestore.node.Node
import dev.nateweisz.bytestore.node.State
import dev.nateweisz.bytestore.node.service.NodeService
import dev.nateweisz.bytestore.node.websocket.NodeSocketHandler
import dev.nateweisz.bytestore.node.websocket.s2n.RequestBuildMessage
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.concurrent.Executors

@Service
class BuildService(
    val nodeService: NodeService,
    @Lazy val nodeSocketHandler: NodeSocketHandler,
    private val buildRepository: BuildRepository,
    private val buildLogsRepository: BuildLogsRepository
) {
    private val deadNodeExecutor = Executors.newSingleThreadScheduledExecutor()
    // Key: build id, Value: (node id, build)
    private val currentBuilds: MutableMap<String, Pair<String, Build>> = mutableMapOf()
    private val queuedBuilds: MutableList<Build> = mutableListOf()

    init {
        // this executors goal is to check if any queued builds can be started
        deadNodeExecutor.scheduleWithFixedDelay({
            currentBuilds.values.forEach { (nodeId, build) ->
            }
        }, 0, 300, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    fun findOpenNode(): Node? {
        return nodeService.nodes.find { it.state == State.ACTIVE && !currentBuilds.containsKey(it.id) }
    }

    fun queueBuild(build: Build) {
        queuedBuilds.add(build)
    }

    fun startBuildOn(node: Node, build: Build) {
        currentBuilds[build.id.toString()] = Pair(node.id, build)
        nodeSocketHandler.sendMessageToNode(node.id, 0x00, RequestBuildMessage(build.owner, build.repository, build.commitHash))
    }

    fun isBuilding(owner: String, repository: String, commitHash: String): Boolean {
        return currentBuilds.values.any { it.second.owner == owner && it.second.repository == repository && it.second.commitHash == commitHash }
    }

    fun finishBuild(webSocketId: String, status: BuildStatus, logs: String) {
        val buildId = NodeSocketHandler.sessionIdToNodeId[webSocketId] ?: throw RuntimeException("Somebody's getting *******")
        val (nodeId, build) = currentBuilds.remove(buildId) ?: throw RuntimeException("Somebody's getting *******")

        buildRepository.save(build.copy(status = status, buildBy = nodeId))
        buildLogsRepository.save(BuildLogs(buildId = build.id, logs = logs))
    }
}