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

class OpenAIChat {
    File userInputFile = new File('input.txt')
    File convYamlFile = new File('input.yaml')
    File outputFile = new File('output.md')
    String token
    String url = 'https://api.openai.com/v1/chat/completions'
    String[] modelList = ["gpt-4", "gpt-3.5-turbo", "gpt-3.5-turbo-16k"]
    String systemRoleInitContent = ''
    LinkedHashMap<String, Serializable> json
    List<Map<String, String>> conversation
    List<Map<String, String>> lastConversation = null

    static void main(String[] args) {
        new OpenAIChat(args[0]).startListener()
    }

    OpenAIChat(String token) {
        this.token = token
    }

    void startListener() {
        while (true) {
            String consoleInput = System.console().readLine 'Press Enter to submit the input file...'
            if(consoleInput.contains("clear")){
                convYamlFile.write("conversation: []")
                conversation = []
            } else if(consoleInput.contains("reset")){
                convYamlFile.write("conversation: []")
                conversation = []
                outputFile.write("")
            } else {
                int arrayIndexSelected = consoleInput ? consoleInput.replaceAll("[^0-9]", "").toInteger() : 1
                if (consoleInput.contains("4e")) {
                    systemRoleInitContent = 'You are a helpful assistant'
                } else if (consoleInput.contains("4c")) {
                    systemRoleInitContent = 'Act as a master programmer, respond in code for specified language only.'
                } else {
                    systemRoleInitContent = "Answer concisely, precisely, no summaries. Say 's' or 'sry' for apologies and proceed."
                }
                int modelChoice = arrayIndexSelected != 4 ? 1 : 0
                int max_tokenChoice = arrayIndexSelected != 4 ? 3200 : 7200
                //TODO: write better logic for selecting different models
                if(consoleInput.contains("3l")){
                    systemRoleInitContent = 'You are a helpful assistant'
                    modelChoice = 2
                    max_tokenChoice = 1500
                }

                if(!convYamlFile.exists()) convYamlFile.createNewFile()
                if (convYamlFile.text.isEmpty()) convYamlFile.write("conversation: []")
                Yaml yaml = new Yaml()
                Map<String, List<Map<String, String>>> yamlMap = yaml.load(convYamlFile.text)
                conversation = yamlMap.get("conversation")
                updateCoversationYaml('user', userInputFile.text)
                setJsonPayload(modelChoice, max_tokenChoice)

                if (conversation == lastConversation) {
                    println "The input file has not changed since the last submission. Skipping..."
                } else {
                    processConversation()
                }
            }
        }
    }

    void processConversation() {
        String jsonString = JsonOutput.toJson(json)
        def aiResponse
        def serverResponse
        try {
            serverResponse = invokeSendRequest('POST', url, jsonString, false, true).result
            aiResponse = serverResponse.choices[0].message.content
        } catch (Exception e) {
            println "Error occurred while sending the request: ${e.message}"
            aiResponse = serverResponse
            println "AI response:\n $aiResponse"
            return
        }

        if (outputFile.exists()) {
            if(!outputFile.text.isEmpty()){
                outputFile.append('\n---\n')
            }
        } else {
            outputFile.write('# Conversation\n')
        }

        outputFile.append(aiResponse)
        String updatedYaml = updateCoversationYaml('assistant', aiResponse)
        convYamlFile.write("conversation:\n" + updatedYaml)
        lastConversation = conversation
    }

    String updateCoversationYaml(String role, String text) {
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

    void setJsonPayload(int modelChoice, int max_tokenChoice) {
        json = [
            model: modelList[modelChoice],
            messages: [],
            max_tokens: max_tokenChoice,
            temperature: 0.6
        ]
        println json.toString()
        json.messages.add([role: 'system', content: systemRoleInitContent])
        conversation.each { entry ->
            entry.each { inputKey, inputContent ->
                json.messages.add([role: inputKey, content: """${inputContent}"""])
            }
        }
    }

    def invokeSendRequest(String reqMethod, String URL, String message, boolean failOnError, boolean useProxy) {
        def serverUrl = 'http://localhost:9001/sendRequest'
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