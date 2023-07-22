List customArgs = []
args.each { customArgs << it }

if (args.size() == 0) {
    customArgs << '9001'
}

File keyFile = new File('key')
if (keyFile.exists() && customArgs[1] == null) {
    customArgs << keyFile.text
} else if (args.size() < 2 && !keyFile.exists()) {
    throw new Exception('Please provide a token in the second argument or in a file named `key`')
}

Binding binding = new Binding()
binding.setVariable('args', customArgs.toArray(new String[0]))
GroovyShell shell = new GroovyShell(binding)
Thread.start {
    shell.evaluate(new File('server.groovy'))
}

Thread.start {
    shell.evaluate(new File('openai-chat-yaml.groovy'))
}
