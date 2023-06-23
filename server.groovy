import com.sun.net.httpserver.*
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import javax.net.ssl.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.security.cert.X509Certificate
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch


    def sendRequest(String reqMethod, String URL, String message, boolean failOnError, boolean useProxy){
        Authenticator authenticator = new Authenticator() {
            public PasswordAuthentication getPasswordAuthentication() {
                return (new PasswordAuthentication(username,
                        password.toCharArray()))
            }
        }
        def response = [:]
        def request
        if(false){
            Authenticator.setDefault(authenticator)
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.com", 8080))
            request = new URL(URL).openConnection(proxy)
        } else {
            request = new URL(URL).openConnection()
        }
        request.setDoOutput(true)
        request.setRequestMethod(reqMethod)
        request.setRequestProperty('Authorization', "Bearer ${args[0]}")
        request.setRequestProperty('Content-Type', 'application/json')
        if(!message.isEmpty())
            request.getOutputStream().write(message.getBytes("UTF-8"))
        def getRC = request.getResponseCode()
        response.rc = getRC
        def slurper = new JsonSlurper()
        def result
        try {
            if(request.getInputStream().available())
                result = slurper.parseText(request.getInputStream().getText())
            response.result = result
        } catch (Exception ignored) {
            if(failOnError){
                throw new Exception("Request made to $URL failed.\nResponse code is: $getRC\n${request.getResponseMessage()}\n${request.getErrorStream().getText()}")
            } else{
                response.result = request.getErrorStream().getText()
            }
        }
        println response
        return response
    }

int port = 9001
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0)
server.createContext('/sendRequest', { HttpExchange exchange ->
    def input = exchange.getRequestBody().getText()
    def parsedInput = new JsonSlurper().parseText(input)
    def response = sendRequest(parsedInput.reqMethod, parsedInput.URL, parsedInput.message, parsedInput.failOnError, parsedInput.useProxy)

    exchange.getResponseHeaders().add('Content-Type', 'application/json')
    exchange.sendResponseHeaders(200, 0)
    def responseBody = exchange.getResponseBody()
    responseBody.write(new JsonBuilder(response).toString().getBytes("UTF-8"))
    responseBody.close()
})
server.start()
CountDownLatch latch = new CountDownLatch(1)
latch.await()