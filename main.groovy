//Fix this garb
// String[] customArgs = new String[2]
// if(args.length == 0){
//     customArgs[0] = '9007'
// } else if (args.length >= 2) {
//     customArgs = args
// }
// File keyFile = new File('key')
// if(args.length == 1 && keyFile.exists()){
//     customArgs[1] = new File('key').text
// }else if(args.length < 2 && !keyFile.exists()){
//     throw new Exception("Please provide a token in the second argument or in a file named `key`")
// }
Binding binding = new Binding();
binding.setVariable("args", args);
GroovyShell shell = new GroovyShell(binding);
Thread.start {
    shell.evaluate(new File('server.groovy'));
}

Thread.start {
    shell.evaluate(new File('openai-chat-yaml.groovy'))
}