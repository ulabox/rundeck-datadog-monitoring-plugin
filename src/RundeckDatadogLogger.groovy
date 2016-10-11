import com.dtolabs.rundeck.plugins.notification.NotificationPlugin
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.text.Template

class Point {
    Integer value
    Date when

    Point(Integer val) { this(val, new Date()) }
    Point(Integer val, Long whn) { this(val, new Date(whn * 1000)) }
    Point(Integer val, Date whn) {
        value = val
        when = whn
    }

    String toString() {
        String.format('[%d, "%s"]', value, when.toTimestamp())
    }
}

class Serie {
    String metric
    List<Point> points = []
    String host = ""
    List<String> tags = []

    Serie(String _metric) { this(_metric, [])}
    Serie (String _metric, List<Point> _points) { this(_metric, _points, "") }
    Serie (String _metric, List<Point> _points, String _host) { this(_metric, _points, _host, []) }
    Serie (String _metric, List<Point> _points, String _host, List<String> _tags) {
        metric = _metric
        points = _points
        host = _host
        tags = _tags
    }
}

class Api {

    private String apiKey
    private URL url

    Api(String _apiKey) {
        apiKey = _apiKey
        url = new URL("https://app.datadoghq.com/api/v1/series?api_key=${apiKey}")
    }

    def store(Serie s) {
        def connection = url.openConnection()
        connection.setRequestMethod("POST")
        connection.addRequestProperty("Content-type", "application/json")
        connection.doOutput = true

        connection.outputStream.withWriter { Writer w ->
            w << new JsonBuilder([series: [
                    metric: s.metric,
                    points: s.points.collect {[it.when.getTime(), it.value]},
                    type: 'gauge',
                    host: s.host,
                    tags: s.tags
            ]]).toString()
        }
        def json = connection.inputStream.withReader { Reader r -> new JsonSlurper().parse(r) }
        connection.connect()
        def status = json.status
        if (!"success".equals(status)) {
            System.err.println("ERROR: DatadogEventNotification plugin status: " + status)
            return false
        }

        return true
    }
}


class MetricsPlugin {
    private List<String> tags = []
    private String host = ""
    Api apiClient

    def format(Template template, Map binding) {
        return template.make(binding)
    }

    def setHost(String _host) {
        host = _host
    }

    def setTags(List<String> _tags) {
        tags = _tags
    }

    def addTag(String tag) {
        tags.add(tag)
    }

    private def templateEngine = new SimpleTemplateEngine()
    private def createTemplate(String text) {
        return templateEngine.createTemplate(text)
    }

    def onStart(Map execution, Map configuration) {
        def point = new Point(0)
        String metric = format(createTemplate(configuration.serie), [execution:execution])
        Serie serie = new Serie(metric, [point], host, tags)
        println serie
        apiClient.store(serie)
    }

    def onSuccess(Map execution, Map configuration) {
        def point = new Point(1)
        String metric = format(createTemplate(configuration.serie), [execution:execution])
        Serie serie = new Serie(metric, [point], host, tags)
        println serie
        apiClient.store(serie)
    }

    def onFailure(Map execution, Map configuration) {
        def point = new Point(-1)
        String metric = format(createTemplate(configuration.serie), [execution:execution])
        Serie serie = new Serie(metric, [point], host, tags)
        println serie
        apiClient.store(serie)
    }
}

def metrics = new MetricsPlugin()

rundeckPlugin(NotificationPlugin){
    title='Datadog metric monitoring'
    description='Does some action'
    configuration {
        serie title: 'Serie: ', description: 'Name for the serie:', required: true, defaultValue: 'rundeck.${execution.project}.${execution.group}.${execution.job.name}'
        api_key(title: 'Api key', description: 'Application key', required: true, defaultValue: System.getenv('DATADOG_TOKEN')) {
            metrics.setApiClient(new Api(it))
            metrics.setHost(InetAddress.getLocalHost().getHostName())
        }
        tags(
                title: 'Tags',
                description: 'Default tags for that serie',
                required: false,
                defaultValue: 'rundeck:${execution.project},rundeck:${execution.group},rundeck:${execution.status},rundeck:${execution.project}:${execution.status}'
        ) {
            metrics.setTags( it.tokenize(",") )
        }
    }

    onstart { Map execution, Map configuration ->
        println "ONSTART"
        metrics.onStart(execution, configuration)

        return true
    }
    onsuccess { Map execution, Map configuration ->
        println "ONSUCCESS"
        metrics.onSuccess(execution, configuration)

        return true
    }
    onfailure { Map execution, Map configuration ->
        println "ONFAILURE"
        metrics.onFailure(execution, configuration)

        return true
    }
}