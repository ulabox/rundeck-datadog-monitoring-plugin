import groovy.mock.interceptor.MockFor
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder

class RundeckDatadogLoggerTestCase extends GroovyTestCase {
    void testIsTrue() {
        assertTrue(1 == 1)
    }

    void testDisplayPoint() {
        def d = new Date().parse('yyyy-MM-dd H:m:s', '2016-09-10 12:20:00')
        def ts = d.toTimestamp()
        def point = new Point(12, d)

        assert point.toString() == String.format('[%d, "%s"]', 12, ts)
        println point
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

    void testDoPost() {
        def serie = new Serie('test.serie', [], 'hostname', ['rundeck:job', 'rundeck:tests'])
        def api = [store: {Serie s -> assert s == serie}] as Api
        api.store(serie)
    }

    void testMetricsOnStart() {
        def metrics = [onSuccess: {Map execution, Map configuration ->
            println execution
        }] as MetricsPlugin

        metrics.onSuccess([foo: "bar"], [eggs: "Spam"])
    }

    void testParseJSON() {
        def serie = new Serie('test.serie', [], 'hostname', ['rundeck:job', 'rundeck:tests'])
        def api = new Api()
        api.doPost(serie)
    }
}