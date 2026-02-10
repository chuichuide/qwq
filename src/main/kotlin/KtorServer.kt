package pingu

import FastFile
import detectContentType
import fileExtension
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun startServer(fs: FastFile, port: Int) {
    embeddedServer(Netty, port) {
        routing {
            get("/") {
                val query = call.request.queryParameters["search"]?.trim()?.lowercase() ?: ""

                val allMatches = if (query.isEmpty()) {
                    fs.getAllEntries()
                } else {
                    fs.getAllEntries().filter { it.name.lowercase().contains(query) }
                }

                val displayEntries = allMatches.sortedBy { it.name }

                call.respondHtml {
                    head {
                        title { +"FastFile Explorer" }
                        style {
                            unsafe {
                                +"""
                                body { background: #121212; color: #e0e0e0; font-family: 'Consolas', monospace; padding: 20px; }
                                table { width: 100%; border-collapse: collapse; margin-top: 20px; box-shadow: 0 0 10px rgba(0,0,0,0.5); }
                                th { background: #333; color: #4db8ff; border: 1px solid #444; padding: 10px; text-align: left; }
                                td { border: 1px solid #333; padding: 8px; font-size: 14px; }
                                tr:nth-child(even) { background: #1e1e1e; }
                                tr:hover { background: #2a2a2a; }
                                a { color: #82aaff; text-decoration: none; margin-right: 15px; transition: 0.2s; }
                                a:hover { color: #fff; text-decoration: underline; }
                                .search-box { background: #222; border: 1px solid #444; color: #fff; padding: 10px; width: 300px; outline: none; }
                                .badge { background: #444; padding: 2px 6px; border-radius: 4px; font-size: 12px; margin-left: 10px; }
                                """.trimIndent()
                            }
                        }
                    }
                    body {
                        h2 {
                            +"FastFile Explorer"
                            span(classes = "badge") { +"${allMatches.size} files" }
                        }
                        form(method = FormMethod.get, action = "/") {
                            input(type = InputType.text, name = "search", classes = "search-box") {
                                placeholder = "Search filename..."; value = query
                                autoFocus = true
                            }
                        }

                        table {
                            thead {
                                tr {
                                    th { +"Name" }
                                    th { +"Offset (Hex)" }
                                    th { +"Size" }
                                    th { +"Actions" }
                                }
                            }
                            tbody {
                                displayEntries.forEach { entry ->
                                    tr {
                                        td { +entry.name }
                                        td { +entry.offset.toString(16).uppercase().padStart(8, '0') }
                                        td { +"${entry.size} B" }
                                        td {
                                            val url = entry.name.encodeURLPathPart()
                                            a(href = "/view/$url") { target = "_blank"; +"Preview" }
                                            a(href = "/download/$url") { +"Download" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            get("/view/{name...}") { handleFile(call, fs, false) }
            get("/download/{name...}") { handleFile(call, fs, true) }
        }
    }.start(wait = true)
}

private suspend fun handleFile(call: ApplicationCall, fs: FastFile, isDownload: Boolean) {
    // 處理路徑包含斜線的情況
    val name = call.parameters.getAll("name")?.joinToString("/") ?: return call.respond(HttpStatusCode.BadRequest)

    val data = fs.readFile(name) ?: return call.respond(HttpStatusCode.NotFound)

    val contentType = detectContentType(data, name)

    if (isDownload) {
        val ext = contentType.fileExtension()
        var fileName = name.substringAfterLast('/')
        if (ext.isNotEmpty() && !fileName.endsWith(ext, ignoreCase = true)) fileName += ext

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
        )
    }
    call.respondBytes(data, contentType)
}