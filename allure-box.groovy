import com.beust.jcommander.internal.Lists
@Grapes([
    @Grab(group='io.qameta.allure', module='allure-generator', version='2.3.4'),
    @Grab(group='javax.servlet', module='javax.servlet-api', version='3.0.1'),
    @Grab(group='org.eclipse.jetty.aggregate', module='jetty-all-server', version='8.1.8.v20121106', transitive=false)
])


import io.qameta.allure.ReportGenerator
import io.qameta.allure.core.Configuration;
import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.junitxml.*
import java.util.concurrent.atomic.AtomicInteger
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import javax.servlet.http.*
import javax.servlet.*
import java.util.UUID
import groovy.servlet.*
import groovy.json.*
import java.nio.file.Paths

class Glob {
    static final REPORTDIR = '/reports'
    static BASEURL = 'http://localhost/'  // This one is filled at startup 
}

/**
    AllureGenerating servlet.
    POST a multipart data with report XML and attaches and stuff
    and receive answer with JSON of {result: OK, url: <url-for-report>}
*/
class ABServlet extends HttpServlet {
    void doPost(HttpServletRequest request, HttpServletResponse response) {
        println 'Receiving report data..'
        def report = UUID.randomUUID()
        
        def root = "${Glob.REPORTDIR}/${report}/"
        def inputDirectoryName = "${root}/input"
        def input = Paths.get(inputDirectoryName)
        def outputDirectoryName = "${root}/output"
        def output = Paths.get(outputDirectoryName)
        new File(inputDirectoryName).mkdirs()
        new File(outputDirectoryName).mkdirs()
        
        request.getParts().each{ part ->
            def fileName="${input}/${extractFileName(part)}";
            println "Writing ${part.name} to ${fileName}"
            // write writes relatively to the Glob.REPORTDIR (as it is in MultipartCOnfigElement)
            // so we tailor the paths for it to work properly
            part.write("${report}/input/${extractFileName(part)}")
        }
        
        println "Generating report ${report}.."
        Configuration configOptions = new ConfigurationBuilder().useDefault().build();
        ReportGenerator generator = new ReportGenerator(configOptions);
        generator.generate(output, Lists.newArrayList(input));
        println "Report ${report} done"
        
        response.writer.write(JsonOutput.toJson([result: 'OK', url: "${Glob.BASEURL}/${report}/output/index.html"]))
    }

    private String extractFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        String[] items = contentDisp.split(";");
        for (String s : items) {
            if (s.trim().startsWith("filename")) {
                return s.substring(s.indexOf("=") + 2, s.length()-1);
            }
        }
        return "";
    }
}

def startJetty() {
    def jetty = new Server(80)

    def context = new ServletContextHandler(jetty, '/', ServletContextHandler.SESSIONS)  // Allow sessions.
    
    def generator = context.addServlet(ABServlet, '/generate')
    generator.getRegistration().setMultipartConfig(new MultipartConfigElement(Glob.REPORTDIR));
        
    def filesHolder = context.addServlet(DefaultServlet, '/')
    filesHolder.setInitParameter('resourceBase', Glob.REPORTDIR)

    jetty.start()
}

if (this.args[0] != null) {
    Glob.BASEURL = this.args[0]
}

println "Started service, base URL is ${Glob.BASEURL}, press Ctrl+C to stop."
startJetty()


