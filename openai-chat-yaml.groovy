#!/usr/bin/env groovy
@Grab(group='org.yaml', module='snakeyaml', version='2.0')
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
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
import java.nio.file.*
import java.text.SimpleDateFormat

class OpenAIChat {
    String[] args
    File userInputFile = new File('input.txt')
    File convYamlFile = new File('input.yaml')
    File outputFile = new File('output.md')
    Boolean autoClean = true
    String token
    String url = 'https://api.openai.com/v1/'
    String[] modelList = ["gpt-4o", "gpt-4o-mini", "o1-preview", "o1-mini"]
    String systemRoleInitContent = ''
    LinkedHashMap<String, Serializable> json
    List<Map<String, String>> conversation
    List<Map<String, String>> lastConversation = null
    double temp = 0.6

    static void main(String[] args) {
        OpenAIChat aiChat = new OpenAIChat(args)
        aiChat.checkForServer()
        aiChat.startListener()
    }

    OpenAIChat(String[] args) {
        this.args = args
        if(args.size() == 0){
            this.args = ['9001']
        }
    }

    void checkForServer(){
        int port = args[0].toInteger()
        boolean isListening = false
        long timeout = 30000 // timeout in milliseconds (30 seconds)
        long retryInterval = 1000 // retry interval in milliseconds (1 second)
        long maxAttempts = timeout / retryInterval
        long attempts = 0
        while (!isListening && attempts < maxAttempts) {
            try {
                new Socket("localhost", port).close()
                isListening = true
            } catch (ConnectException e) {
                println "Waiting for server to come up on port $port. Retrying..."
                Thread.sleep(retryInterval) // wait for 1 second before retrying
                attempts++
            } catch (Exception e) {
                println "Error: " + e.getMessage()
            }
        }
        if (!isListening) {
            println "Server did not come up on port $port within $timeout milliseconds."
        } else {
            println "Server up and running on port $port"
        }
    }

    void startListener() {
        def properties = new Properties()
        new File('system.properties').withInputStream { properties.load(it) }     
        while (true) {
            processConsoleInput(properties)
        }
    }

    void processConsoleInput(Properties props) {
        String consoleInput = System.console().readLine 'Press Enter to submit the input file...'
        def modelChoice = 1
        def max_tokenChoice = 3200
        //Load system.properties prompts / model choice / max tokens
        props.each { propKey, propValue ->
            def (prompt, model, tokens) = propValue.split(':')
            model = model.toInteger()
            tokens = tokens.toInteger()
            if(consoleInput.equals(propKey)){
                systemRoleInitContent = prompt
                modelChoice = model
                max_tokenChoice = tokens
            }
        }
        switch (consoleInput) {
            case { it.contains("+") }:
                temp = Math.min(1.0, temp + consoleInput.count("+") * 0.1)
                temp = Math.round(temp * 10) / 10.0
                println "Temperature set to ${temp}"
                break
            case { it.contains("-") }:
                temp = Math.max(0.0, temp - consoleInput.count("-") * 0.1)
                temp = Math.round(temp * 10) / 10.0
                println "Temperature set to ${temp}" 
                break
            case { it.contains("clear") }:
                convYamlFile.write("conversation: []")
                conversation = []
                break
            case { it.contains("usage") }:
                println invokeSendRequest('GET', "$url/usage", '', false, true).result
                break                
            case { it.contains("reset") }:
                convYamlFile.write("conversation: []")
                conversation = []
                outputFile.write("")
                break
            case { it.contains("autoclean") }:
                autoClean = !autoClean
                println "autoClean = $autoClean"
                break                
            case { it.contains("backup") }:
                backup()
                outputFile.write("")
                break
            case { it.contains("list") }:
                println invokeSendRequest('GET', "$url/models", '', false, true).result
                break
            case { it.contains("heading") }:
                systemRoleInitContent = "summarize text into short 1 line heading"
                def outputFile = new File("output.md")
                def lines = outputFile.readLines()
                def lastDelimiterIndex = lines.lastIndexOf('---')
                if (lastDelimiterIndex != -1) {
                    def textAfterLastDelimiter = lines[lastDelimiterIndex + 1..-1].join('\n')
                    setJsonPayload(modelChoice, 12)
                    json.messages.add([role: 'user', content: """${truncateString(textAfterLastDelimiter)}"""])
                    lines[lastDelimiterIndex] = "---\n## ${sendAndReceiveFromAI()}"
                    outputFile.write(lines.join('\n'))
                } else {
                    println "Delimiter '---' not found. Heading will not be created"
                }
                break
            default:
                if(systemRoleInitContent.isEmpty())
                    systemRoleInitContent = "Answer concisely, precisely, no summaries. Say 's' or 'sry' for apologies and proceed"
                if(!convYamlFile.exists()) convYamlFile.createNewFile()
                if (convYamlFile.text.trim().isEmpty()) convYamlFile.write("conversation: []")
                Yaml yaml = new Yaml()
                Map<String, List<Map<String, String>>> yamlMap = yaml.load(convYamlFile.text)
                conversation = yamlMap.get("conversation")
                if(consoleInput.equals('c')) {
                    convYamlFile.write("conversation: []")
                    conversation = []                    
                    Tuple2<String, String> result = eatInputInChunks(userInputFile.text, 4)
                    String remainingContent = result.first
                    String droppedContent = result.second
                    userInputFile.text = remainingContent
                    updateCoversationYaml('user', droppedContent)
                } else {
                    updateCoversationYaml('user', userInputFile.text)
                }
                setJsonPayload(modelChoice, max_tokenChoice)
                addYamlConversation()
                if (conversation == lastConversation) {
                    println "The input file has not changed since the last submission. Skipping..."
                } else {
                    processConversation()
                }
                break
        }
    }

    def sendAndReceiveFromAI() {
        def serverResponse
        try {
            String jsonString = JsonOutput.toJson(json)
            def aiResponse
            serverResponse = invokeSendRequest('POST', "$url/chat/completions", jsonString, false, true).result
            aiResponse = serverResponse.choices[0].message.content
            return aiResponse
        } catch (Exception e) {
            println "Error occurred while sending the request: ${e.message}"
            if(serverResponse!=null)
                println "serverResponse: $serverResponse"
            println 'Could not get AI response.'
            return ''
        }
    }

    void processConversation() {
        def aiResponse = sendAndReceiveFromAI()
        if (outputFile.exists()) {
            if(!outputFile.text.isEmpty()){
                outputFile.append('\n\n---\n\n')
            }
        } else {
            outputFile.write('# Conversation\n')
        }

        def lastMessageMap = conversation.last()
        def lastMessage = lastMessageMap.values().last() // Get the last value from the map, remove 'user:' prefix
        def quotedMessage = lastMessage.split('\n').collect { '> ' + it }.join('\n')
        // outputFile.append(quotedMessage + '\n\n' + aiResponse)
        outputFile.append(aiResponse)
        String updatedYaml = updateCoversationYaml('assistant', aiResponse)
        convYamlFile.write("conversation:\n" + updatedYaml)
        lastConversation = conversation
    }

    String updateCoversationYaml(String role, String text) {
        if(autoClean)
            text = cleanInput(text)
        Map<String, String> newEntry = [(role): text]

        if (!conversation) {
            convYamlFile.write("conversation: []")
            conversation = []
        }

        conversation.add(newEntry)

        DumperOptions options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setPrettyFlow(true)
        options.setIndent(2)
        Yaml dumper = new Yaml(options)
        return dumper.dump(conversation)
    }

    Tuple2<String, String> eatInputInChunks(String content, int numChunksToEat) {
        // Split the content into chunks based on timestamps indicating new chunks
        def chunks = content.split(/(?=\n\d{2}:\d{2}\n)/)
        // Initialize a variable to keep track of the dropped chunks
        def droppedChunks = []
        // Remove the specified number of chunks from the beginning if possible
        if (chunks.size() > numChunksToEat) {
            droppedChunks = chunks.take(numChunksToEat)
            chunks = chunks.drop(numChunksToEat)
        } else {
            // If the number of chunks to eat exceeds available chunks, drop all chunks
            droppedChunks = chunks
            chunks = []
        }
        // Join the remaining chunks back together
        def remainingContent = chunks.join("")
        // Store the dropped chunks in a separate variable and join them together
        def droppedContent = droppedChunks.join("")
        // Return both the remaining content and the dropped content as a tuple
        return new Tuple2<>(remainingContent, droppedContent)
    }

    String cleanInput(String input) {
        input = input.replaceAll(/(?m)[ \t]+$/, '')
        input = input.replaceAll(/\n+/, "\n")
        input = input.trim()
        return input
    }

    def backup() {
        def sourceFile = Paths.get("output.md")
        def dateFormat = new SimpleDateFormat("dd-MM-yyyy_HH_mm_ss")
        dateFormat.setTimeZone(TimeZone.getTimeZone("EST"))
        def now = dateFormat.format(new Date())
        def targetFile = Paths.get("chats/output-${now}.md")

        if(!targetFile.getParent().toFile().exists()) {
            targetFile.getParent().toFile().mkdirs()
        }
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
    }

    void setJsonPayload(int modelChoice, int max_tokenChoice) {
        json = [
            model: modelList[modelChoice],
            messages: [],
            max_tokens: max_tokenChoice,
            temperature: temp
        ]
        println json.toString()
        json.messages.add([role: 'system', content: systemRoleInitContent])
    }

    void addYamlConversation(){
        conversation.each { entry ->
            entry.each { inputKey, inputContent ->
                json.messages.add([role: inputKey, content: """${inputContent}"""])
            }
        }
    }

//Utilities:

    //For summary with gpt-3.5
    def truncateString(String str) {
        def tokens = str.tokenize()
        if (tokens.size() > 2500) {
            return tokens.take(2500).join(' ')
        } else {
            return str
        }
    }

    def invokeSendRequest(String reqMethod, String URL, String message, boolean failOnError, boolean useProxy) {
        String serverUrl = "http://localhost:${args[0]}/sendRequest"
        def connection = new URL(serverUrl).openConnection()
        connection.setRequestMethod('POST')
        connection.setDoOutput(true)
        connection.setRequestProperty('Content-Type', 'application/json')

        def payload = [
            reqMethod: reqMethod,
            URL: URL,
            message: message,
            failOnError: failOnError,
            useProxy: useProxy
        ]

        connection.getOutputStream().write(new JsonBuilder(payload).toString().getBytes("UTF-8"))

        def response = new JsonSlurper().parseText(connection.getInputStream().getText())
        return response
    }
}

class Tuple2<T1, T2> {
    final T1 first
    final T2 second

    Tuple2(T1 first, T2 second) {
        this.first = first
        this.second = second
    }

    @Override
    String toString() {
        return "Tuple2(first: $first, second: $second)"
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (obj == null || getClass() != obj.getClass()) return false
        Tuple2 other = (Tuple2) obj
        return first == other.first && second == other.second
    }

    @Override
    int hashCode() {
        int result = first != null ? first.hashCode() : 0
        result = 31 * result + (second != null ? second.hashCode() : 0)
        return result
    }
}