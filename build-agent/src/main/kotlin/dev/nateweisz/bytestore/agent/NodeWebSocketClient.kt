package dev.nateweisz.bytestore.agent

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

val LOGGER = KotlinLogging.logger { }
val client = HttpClient(CIO) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
}

val BACKEND_URL = System.getenv("BACKEND_URL") ?: "https://artifacts-api.akrylic.org"

class NodeWebSocketClient {

    private val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    private val runtime = Runtime.getRuntime()

    suspend fun connect(serverUrl: String, configFile: String = "./agent-data.properties") = coroutineScope {
        val nodeId = getOrRegisterNodeId(configFile)

        client.webSocket("ws${if (BACKEND_URL.startsWith("https")) "s" else ""}://$serverUrl/api/nodes/ws?nodeId=$nodeId") {
            val heartbeatJob = launch {
                while (isActive) {
                    sendHeartbeat()
                    delay(1000)
                }
            }

            LOGGER.info { "Successfully connected to master server" }

            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            val buffer = ByteBuffer.wrap(frame.data)
                            val id = buffer.getInt()

                            LOGGER.info { "Received Packet: $id" }

                            when (id) {
                                0x00 -> {
                                    startBuild(this, buffer.getString(), buffer.getString(), buffer.getString(), buffer.getString(), buffer.getString())
                                }
                            }
                        }
                        else -> println("Received frame: $frame")
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
            } finally {
                val reason = closeReason.await()
                println("WebSocket closed: code=${reason?.code}, reason=${reason?.message}")
                heartbeatJob.cancelAndJoin()
            }
        }
    }

    private suspend fun getOrRegisterNodeId(configFile: String): String {
        val configPath = Path.of(configFile)

        if (!Files.exists(configPath)) {
            val response = client.post("${BACKEND_URL}/api/nodes/register") {
                contentType(ContentType.Application.Json)
                setBody(RegistrationRequest(
                    ipAddress = "127.0.0.1",
                    memory = 1024L * 1024L * 1024L,
                    registrationToken = null
                ))
            }
            println(response.bodyAsText())

            val node = response.body<Node>()
            withContext(Dispatchers.IO) {
                Files.write(configPath, "nodeId=${node.id}".toByteArray())
            }
            return node.id
        }

        return withContext(Dispatchers.IO) {
            Files.readAllLines(configPath).first().split("=")[1]
        }
    }

    private suspend fun DefaultWebSocketSession.sendHeartbeat() {

        val buffer = ByteBuffer.allocate(28).apply {
            putInt(0x00)
            putLong((osBean.processCpuLoad * 100).toLong())
            putDouble(osBean.systemCpuLoad * 100)
            putLong(runtime.totalMemory() - runtime.freeMemory())

            flip()
        }
        send(Frame.Binary(true, buffer.array()))

    }
}

suspend fun main() {
    val client = NodeWebSocketClient()
    client.connect(BACKEND_URL.replace("http://", "").replace("https://", ""))
}

fun ByteBuffer.getString(): String {
    val length = int
    val bytes = ByteArray(length)
    get(bytes)
    return String(bytes)
}

fun ByteBuffer.writeString(string: String) {
    putInt(string.length)
    put(string.toByteArray())
}