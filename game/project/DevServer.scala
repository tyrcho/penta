import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.{InetAddress, InetSocketAddress}
import java.nio.file.{Files, Path}

object DevServer {
  private val POLL_SCRIPT =
    """<script>
(function(){
  var last=null;
  setInterval(function(){
    fetch('/~~mtime').then(function(r){return r.text();}).then(function(t){
      if(last===null){last=t;return;}
      if(t!==last){last=t;location.reload();}
    }).catch(function(){});
  },1000);
})();
</script>
""".getBytes("UTF-8")

  def start(root: Path, jsFile: Path, port: Int = 8080): Unit = {
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, port), 0)
    server.createContext("/", exchange => {
      val path = exchange.getRequestURI.getPath
      path match {
        case "/~~mtime"  => serveMtime(exchange, jsFile)
        case "/main.js"  => serveBytes(exchange, Files.readAllBytes(jsFile), "application/javascript")
        case "/main.js.map" =>
          val mapFile = jsFile.resolveSibling("main.js.map")
          if (Files.exists(mapFile)) serveBytes(exchange, Files.readAllBytes(mapFile), "application/json")
          else notFound(exchange)
        case _           => serveStatic(exchange, root, path)
      }
    })
    val t = new Thread(() => server.start(), "dev-server")
    t.setDaemon(true)
    t.start()
  }

  private def serveMtime(exchange: HttpExchange, jsFile: Path): Unit = {
    val mtime = if (Files.exists(jsFile)) Files.getLastModifiedTime(jsFile).toMillis.toString else "0"
    respond(exchange, 200, "text/plain", mtime.getBytes("UTF-8"), noCache = true)
  }

  private def serveStatic(exchange: HttpExchange, root: Path, rawPath: String): Unit = {
    val rel      = if (rawPath == "/" || rawPath.isEmpty) "index.html" else rawPath.stripPrefix("/")
    val rootAbs  = root.toAbsolutePath.normalize()
    val file     = rootAbs.resolve(rel).normalize()
    if (!file.startsWith(rootAbs) || !Files.exists(file) || Files.isDirectory(file)) { notFound(exchange); return }
    val bytes = maybeInject(Files.readAllBytes(file), rel)
    respond(exchange, 200, contentType(rel), bytes)
  }

  private def serveBytes(exchange: HttpExchange, bytes: Array[Byte], ct: String): Unit =
    respond(exchange, 200, ct, bytes)

  private def notFound(exchange: HttpExchange): Unit =
    respond(exchange, 404, "text/plain", "Not found".getBytes)

  private def respond(
    exchange: HttpExchange, status: Int, ct: String,
    body: Array[Byte], noCache: Boolean = false
  ): Unit = {
    exchange.getResponseHeaders.set("Content-Type", ct)
    if (noCache) exchange.getResponseHeaders.set("Cache-Control", "no-store")
    exchange.sendResponseHeaders(status, body.length)
    val out = exchange.getResponseBody
    out.write(body)
    out.close()
  }

  private def maybeInject(bytes: Array[Byte], path: String): Array[Byte] =
    if (path == "index.html") {
      val html = new String(bytes, "UTF-8")
      val inject = new String(POLL_SCRIPT, "UTF-8")
      html.replace("</body>", inject + "</body>").getBytes("UTF-8")
    } else bytes

  private def contentType(path: String): String = path match {
    case p if p.endsWith(".html") => "text/html; charset=utf-8"
    case p if p.endsWith(".js")   => "application/javascript"
    case p if p.endsWith(".css")  => "text/css"
    case p if p.endsWith(".json") => "application/json"
    case p if p.endsWith(".map")  => "application/json"
    case _                         => "application/octet-stream"
  }
}
