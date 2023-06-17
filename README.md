# public openai-chat repo
- [Jbang](#jbang)

# Single Codespace startup command
```bash
export token='myOpenAIToken'
curl -Ls https://sh.jbang.dev | bash -s - app setup && jbang jbang_main_v2_JAVA11.java openai-chat-yaml.groovy $token
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
