import groovy.json.JsonBuilder
import groovy.text.SimpleTemplateEngine

import java.sql.Timestamp

class RundeckDatadogLoggerTestCase extends GroovyTestCase {
    void testIsTrue() {
        assertTrue(1 == 1)
    }

    void testCreatePoint1() {
        def p = new Point(1)

        assert p.value == 1
    }

    void testCreatePoint2() {
        def d = Date.parse('yyyy-MM-dd hh:mm', '2016-09-02 12:00')
        def p = new Point(1, d)

        assert p.when == new Timestamp(d.getTime())
    }

    void testDisplayPoint() {
        def d = Date.parse('yyyy-MM-dd H:m:s', '2016-09-10 12:20:00')
        def ts = d.toTimestamp()
        def point = new Point(12, d)

        println String.format('[%d, %d]', 12, ts.time)
        assert point.toString() == String.format('[%d, %d]', 12, ts.time)
    }

    void testCreateSerie1() {
        def serie = new Serie('test.serie')

        assert serie.metric == 'test.serie'
    }

    void testCreateSerie2() {
        def p1 = new Point(12, new Date())
        def p2 = new Point(5, new Date())
        def serie = new Serie('test.serie', [p1, p2])

        assert serie.metric == 'test.serie'
        assert serie.points[0] == p1
        assert serie.points[1] == p2
    }

    void testCreateSerie3() {
        def serie = new Serie('test.serie', [], 'hostname')

        assert serie.host == 'hostname'
    }

    void testCreateSerie4() {
        def serie = new Serie('test.serie', [], 'hostname', ['rundeck:job', 'rundeck:tests'])

        assert serie.tags[0] == 'rundeck:job'
        assert serie.tags[1] == 'rundeck:tests'
    }

    void testHTTPPost() {
        def serie = new Serie('test.serie', [], 'hostname', ['rundeck:job', 'rundeck:tests'])

        def sampleJson = new JsonBuilder(serie)
        def connection = [
                outputStream: [ withWriter: {
                    Closure c -> c([
                            write: {
                                assert it == new JsonBuilder(serie).toString()
                            } ] as Writer)
                }],
                inputStream: [withReader: {
                    Closure c -> [status: "success"]
                }],
                setRequestMethod: {},
                addRequestProperty: { a, b ->
                    assert a == "Content-Type"
                    assert b == "application/json"
                },
                connect: {true}
        ]

        URL.metaClass.openConnection = {
            connection
        }

        def sampleUrl = "https://app.datadoghq.com/api/v1/series?api_key=api-key".toURL()
        def client = new Client()

        assert true == client.post(sampleUrl, sampleJson)
    }

    void testMetricsFormat() {
        String serie = 'rundeck.${execution.project}.${execution.group}.${execution.job.name}'
        Map<String, String> execution = [
                project: "ulaoffice",
                group: "group",
                job: [
                        name: "job-name"
                ]
        ]
        def plugin = new MetricsPlugin()
        assert "rundeck.ulaoffice.group.job-name" == plugin.format(serie, [execution: execution])
    }

    void testMetricsTagsFormat() {
        Map execution = [
            project: "ulaoffice",
            group: "group",
            status: 'SUCCESS',
            job: [
                    name: "job-name"
            ]
        ]
        List<String> tags = ['rundeck:${execution.project}', 'rundeck:${execution.group}', 'rundeck:${execution.status}', 'rundeck:${execution.project}:${execution.status}']
        def newTags = new MetricsPlugin().formatTags(tags, [execution: execution])

        assert newTags[0] == 'rundeck:ulaoffice'
        assert newTags[1] == 'rundeck:group'
        assert newTags[2] == 'rundeck:SUCCESS'
        assert newTags[3] == 'rundeck:ulaoffice:SUCCESS'
     }

    void testMetricsOnSuccess() {
        def ts = 197126
        def cfg = [eggs: "Spam", host: "Hostname"]
        def serie = new Serie('metric.for.serie', [new Point(1, ts)], "Hostname", [])
        SimpleTemplateEngine.metaClass.createTemplate = {'metric.for.serie'}
        def plugin = new MetricsPlugin()
        Point.metaClass.constructor = { v -> new Point(v, ts) }
        plugin.client = [store: { Serie s, Map config ->
            assert s.encode() == serie.encode()
            assert config == cfg
        }] as Client
        plugin.onSuccess([foo: "bar"], cfg)
    }

    void testMetricsOnFailure() {
        def ts = 197126
        def cfg = [eggs: "Spam", host: "Hostname"]
        def serie = new Serie('metric.for.serie', [new Point(-1, 197126)], "Hostname", [])
        SimpleTemplateEngine.metaClass.createTemplate = {'metric.for.serie'}
        def plugin = new MetricsPlugin()
        Point.metaClass.constructor = { v -> new Point(v, ts) }
        plugin.client = [store: { Serie s, Map config ->
            assert s.encode() == serie.encode()
            assert config == cfg
        }] as Client
        plugin.onFailure([foo: "bar"], cfg)
    }

    void testMetricsOnStart() {
        def ts = 197126
        def cfg = [eggs: "Spam", host: "Hostname"]
        def serie = new Serie('metric.for.serie', [new Point(0, 197126)], "Hostname", [])
        SimpleTemplateEngine.metaClass.createTemplate = {'metric.for.serie'}
        def plugin = new MetricsPlugin()
        Point.metaClass.constructor = { v -> new Point(v, ts) }
        plugin.client = [store: { Serie s, Map config ->
            assert s.encode() == serie.encode()
            assert config == cfg
        }] as Client
        plugin.onStart([foo: "bar"], cfg)
    }
}