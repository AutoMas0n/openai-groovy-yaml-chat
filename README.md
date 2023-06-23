# public openai-chat repo
This is an experimental groovy/file based chat client with openai api meant for use with visual studio live code share.  
*Live streaming of responses is not supported*

## (This Repo is a work in progress)

# Single Codespace startup command
```bash
export token='myOpenAIToken' && curl -Ls https://sh.jbang.dev | bash -s - app setup && source ~/.bashrc && git clone https://github.com/automationStati0n/openai-groovy-yaml-chat && cd openai-groovy-yaml-chat && jbang jbang_main_v2_JAVA11.java openai-chat-yaml.groovy $token
```

# Jbang & codespaces
1. get jbang  
   ```bash
   curl -Ls https://sh.jbang.dev | bash -s - app setup
   ```
2. follow https://github.com/automationStati0n/jbang-groovy-launcher/tree/main  
   ```bash
   jbang https://gist.github.com/automationStati0n/d8d28cfb7a68592c79fd052419597e04 openai-chat-yaml.groovy $token
   ```
