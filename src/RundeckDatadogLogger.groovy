import com.dtolabs.rundeck.plugins.notification.NotificationPlugin
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.text.Template

import java.sql.Timestamp

class Point {
    Integer value
    Timestamp when

    Point(Integer val) { this(val, new Date()) }
    Point(Integer val, Long whn) { this(val, new Date(whn * 1000)) }
    Point(Integer val, Date whn) { this(val, new Timestamp(whn.getTime())) }
    Point(Integer val, Timestamp whn) {
        value = val
        when = whn
    }

    String toString() {
        String.format('[%d, %d]', value, when.time)
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
            points: points.collect { Point p -> [p.when.getTime(), p.value]},
            type: 'gauge',
            host: host,
            tags: tags
        ]
    }
}

class Client {

    def store(Serie s, Map configuration) {
        def jsonIn = new JsonBuilder([series: s.encode()])
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
        if ("success" != status) {
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
        def point = new Point(0)
        String metric = format(makeTpl(configuration.serie), [execution:execution])
        Serie serie = new Serie(
                metric,
                [point],
                (String) configuration.host,
                formatTags((List<String>) configuration.tags, [execution: execution])
        )
        println serie
        client.store(serie, configuration)
    }

    def onSuccess(Map execution, Map configuration) {
        def point = new Point(1)
        String metric = format(makeTpl(configuration.serie), [execution:execution])
        Serie serie = new Serie(
                metric,
                [point],
                (String) configuration.host,
                formatTags((List<String>) configuration.tags, [execution: execution])
        )
        println serie
        client.store(serie, configuration)
    }

    def onFailure(Map execution, Map configuration) {
        def point = new Point(-1)
        String metric = format(makeTpl(configuration.serie), [execution:execution])
        Serie serie = new Serie(
                metric,
                [point],
                (String) configuration.host,
                formatTags((List<String>) configuration.tags, [execution: execution])
        )
        println serie
        client.store(serie, configuration)
    }
}

rundeckPlugin(NotificationPlugin){
    title='Datadog metric monitoring'
    description='Sends signals to the metrics monitor in datadog.'
    configuration {
        serie title: 'Serie: ', description: 'Name for the serie:', required: true, defaultValue: 'rundeck.${execution.project}.${execution.group}.${execution.job.name}'
        api_key title: 'Api key', description: 'Application key', required: true, defaultValue: System.getenv('DATADOG_TOKEN')
        tags title: 'Tags', description: 'Default tags for that serie', required: false,
                defaultValue: 'rundeck:${execution.project},rundeck:${execution.group},rundeck:${execution.status},rundeck:${execution.project}:${execution.status}'
    }

    onstart { Map execution, Map configuration ->
        println "ONSTART"
        MetricsPlugin.onStart(execution, configuration)

        return true
    }
    onsuccess { Map execution, Map configuration ->
        println "ONSUCCESS"
        MetricsPlugin.onSuccess(execution, configuration)

        return true
    }
    onfailure { Map execution, Map configuration ->
        println "ONFAILURE"
        MetricsPlugin.onFailure(execution, configuration)

        return true
    }
}