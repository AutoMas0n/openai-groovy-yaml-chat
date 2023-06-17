//JAVA 11
//DEPS org.apache.groovy:groovy:4.0.7
//DEPS org.apache.groovy:groovy-ant:+
//DEPS org.apache.groovy:groovy-astbuilder:+
//DEPS org.apache.groovy:groovy-backports-compat23:+
//DEPS org.apache.groovy:groovy-bsf:+
//DEPS org.apache.groovy:groovy-cli-commons:+
//DEPS org.apache.groovy:groovy-cli-picocli:+
//DEPS org.apache.groovy:groovy-console:+
//DEPS org.apache.groovy:groovy-contracts:+
//DEPS org.apache.groovy:groovy-datetime:+
//DEPS org.apache.groovy:groovy-dateutil:+
//DEPS org.apache.groovy:groovy-docgenerator:+
//DEPS org.apache.groovy:groovy-ginq:+
//DEPS org.apache.groovy:groovy-groovydoc:+
//DEPS org.apache.groovy:groovy-groovysh:+
//DEPS org.apache.groovy:groovy-jaxb:+
//DEPS org.apache.groovy:groovy-jmx:+
//DEPS org.apache.groovy:groovy-json:+
//DEPS org.apache.groovy:groovy-jsr223:+
//DEPS org.apache.groovy:groovy-macro:+
//DEPS org.apache.groovy:groovy-macro-library:+
//DEPS org.apache.groovy:groovy-nio:+
//DEPS org.apache.groovy:groovy-servlet:+
//DEPS org.apache.groovy:groovy-sql:+
//DEPS org.apache.groovy:groovy-swing:+
//DEPS org.apache.groovy:groovy-templates:+
//DEPS org.apache.groovy:groovy-test:+
//DEPS org.apache.groovy:groovy-test-junit5:+
//DEPS org.apache.groovy:groovy-testng:+
//DEPS org.apache.groovy:groovy-toml:+
//DEPS org.apache.groovy:groovy-typecheckers:+
//DEPS org.apache.groovy:groovy-xml:+
//DEPS org.apache.groovy:groovy-yaml:+

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "jbang_main_v2_JAVA11", mixinStandardHelpOptions = true, version = "1.0")
public class jbang_main_v2_JAVA11 implements Runnable {

    @Parameters(index = "0", description = "The groovy script file to run.")
    private String script;

    @Parameters(index = "1..*", arity = "0..*", description = "Arguments for the groovy script.")
    private String[] groovyArgs;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new jbang_main_v2_JAVA11()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            InputStream is = new FileInputStream(script);
            Binding binding = new Binding();
            binding.setVariable("args", groovyArgs);
            GroovyShell shell = new GroovyShell(binding);
            shell.evaluate(new InputStreamReader(is));
        } catch (FileNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
