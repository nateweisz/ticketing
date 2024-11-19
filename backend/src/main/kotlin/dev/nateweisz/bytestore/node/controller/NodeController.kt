package dev.nateweisz.bytestore.node.controller

import dev.nateweisz.bytestore.node.ApprovalStage
import dev.nateweisz.bytestore.node.Node
import dev.nateweisz.bytestore.node.data.NodeHeartBeat
import dev.nateweisz.bytestore.node.data.RegistrationRequest
import dev.nateweisz.bytestore.node.service.NodeService
import jakarta.servlet.ServletRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/nodes")
class NodeController(private val nodeService: NodeService) {

    @GetMapping("")
    fun allNodes(): List<Node> {
        return nodeService.nodes
    }

    @PostMapping("/{nodeId}/heartbeat")
    fun heartbeat(nodeId: String, @RequestBody heartbeat: NodeHeartBeat) {
        nodeService.heartbeat(nodeId, heartbeat)
    }

    @PostMapping("/register")
    fun registerNode(@RequestBody registration: RegistrationRequest, request: ServletRequest): Node {
        return nodeService.registerNode(registration, request)
    }

    @PostMapping("/{nodeId}/approve")
    fun approveNode(@PathVariable nodeId: String) {
        val node = nodeService.nodes.find { it.id == nodeId }
        if (node != null) {
            node.approvalStage = ApprovalStage.MANUALLY_APPROVED
        }
    }
}