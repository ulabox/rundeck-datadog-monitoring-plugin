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

    Long unixSeconds() {
        return when.getTime() / 1000
    }

    String toString() {
        String.format('[%d, %d]', value, when.getTime())
    }
}

class Serie {
    String metric
    List<Point> points = []
    String host = ""
    List<String> tags = []

    Serie (String _metric) { this(_metric, [])}
    Serie (String _metric, List<Point> _points) { this(_metric, _points, "") }
    Serie (String _metric, List<Point> _points, String _host) { this(_metric, _points, _host, []) }
    Serie (String _metric, List<Point> _points, String _host, List<String> _tags) {
        metric = _metric
        points = _points
        host = _host
        tags = _tags
    }

    def encode() {
        return [
            metric: metric,
            points: points.collect { Point p -> [p.unixSeconds(), p.value]},
            type: 'gauge',
            host: host,
            tags: tags
        ]
    }
}

class Client {

    def store(Serie s, Map configuration) {
        def jsonIn = new JsonBuilder([series: [s.encode()]])
        URL url = new URL("https://app.datadoghq.com/api/v1/series?api_key=${configuration.api_key}")
        post(url, jsonIn)
    }

    def post(URL url, JsonBuilder json) {
        def connection = url.openConnection()
        connection.setRequestMethod("POST")
        connection.addRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.withWriter { Writer w ->
            w << json.toString()
        }
        def jsonOut = connection.inputStream.withReader { Reader r -> new JsonSlurper().parse(r) }
        connection.connect()
        def status = jsonOut.status
        println json
        println jsonOut
        if ("ok" != status) {
            System.err.println("ERROR: DatadogEventNotification plugin status: " + status)
            return false
        }

        return true
    }
}

class MetricsPlugin {
    Client client = new Client()

    String format(String text, Map bindings) { format(makeTpl(text), bindings) }
    String format(Template template, Map binding) {
        template.make(binding)
    }

    private def makeTpl(String text) {
        return new SimpleTemplateEngine().createTemplate(text)
    }

    def formatTags(List<String> tags, Map bindings) {
        tags.collect { tag ->
            format(makeTpl(tag), bindings)
        }
    }

    def onStart(Map execution, Map configuration) {
        println execution
        def point = new Point(0)
        String metric = format(makeTpl(configuration.serie), [execution:execution])
        List<String> tags = configuration.tags.tokenize(',')
        Serie serie = new Serie(
                metric,
                [point],
                (String) configuration.hostname,
                formatTags(tags, [execution: execution])
        )
        System.out.println serie
        client.store(serie, configuration)
    }

    def onSuccess(Map execution, Map configuration) {
        def point = new Point(1)
        String metric = format(makeTpl(configuration.serie), [execution:execution])
        List<String> tags = configuration.tags.tokenize(',')
        Serie serie = new Serie(
                metric,
                [point],
                (String) configuration.hostname,
                formatTags(tags, [execution: execution])
        )
        System.out.println serie
        client.store(serie, configuration)
    }

    def onFailure(Map execution, Map configuration) {
        def point = new Point(-1)
        String metric = format(makeTpl(configuration.serie), [execution:execution])
        List<String> tags = configuration.tags.tokenize(',')
        Serie serie = new Serie(
                metric,
                [point],
                (String) configuration.hostname,
                formatTags(tags, [execution: execution])
        )
        System.out.println serie
        client.store(serie, configuration)
    }
}

rundeckPlugin(NotificationPlugin){
    title='Datadog metric monitoring'
    description='Sends signals to the metrics monitor in datadog.'
    configuration {
        serie(
            title: 'Serie : ',
            description: 'Name for the serie:',
            required: true,
            defaultValue: 'rundeck.${execution.project}.${execution.job.group}.${execution.job.name}'
        )
        api_key(
            title: 'Api key : ',
            description: 'Application key',
            required: true,
            defaultValue: System.getenv('DATADOG_TOKEN')
        )
        host(
            title: 'Hostname : ',
            description: 'Hostname where the metric happens',
            required: false,
            defaultValue: "hostname".execute().text
        )
        tags(
            title: 'Tags',
            description: 'Default tags for that serie',
            required: false,
            defaultValue: 'rundeck:${execution.project},rundeck:${execution.job.group},rundeck:${execution.status},rundeck:${execution.project}:${execution.status}'
        )
    }

    onstart { Map execution, Map configuration ->
        println "ONSTART"
        def metrics = new MetricsPlugin()
        metrics.onStart(execution, configuration)

        true
    }
    onsuccess { Map execution, Map configuration ->
        println "ONSUCCESS"
        def metrics = new MetricsPlugin()
        metrics.onSuccess(execution, configuration)

        true
    }
    onfailure { Map execution, Map configuration ->
        println "ONFAILURE"
        def metrics = new MetricsPlugin()
        metrics.onFailure(execution, configuration)

        true
    }
}