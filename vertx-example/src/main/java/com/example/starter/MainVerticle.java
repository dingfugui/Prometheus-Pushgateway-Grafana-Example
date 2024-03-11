package com.example.starter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.PushGateway;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.Router;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Router router = Router.router(vertx);
        // Expose a Prometheus endpoint
        router.route("/metrics").handler(PrometheusScrapingHandler.create());
        // Expose a simple endpoint
        router.get("/greeting").handler(ctx -> {
            ctx.response()
                .putHeader("content-type", "text/plain")
                .end("Hello from Vert.x!");
        });

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8888, http -> {
                if (http.succeeded()) {
                    startPromise.complete();
                    System.out.println("HTTP server started on port 8888");
                } else {
                    startPromise.fail(http.cause());
                }
            });

        // Push metrics to PushGateway
        PushGateway gateway = new PushGateway(new URL("http://localhost:9091"));
        Map<String, String> groupingKey = new HashMap<>();
        groupingKey.put("instance", "vertx-instance");

        vertx.setPeriodic(3000, id -> {
            PrometheusMeterRegistry registry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
            try {
                gateway.pushAdd(registry.getPrometheusRegistry(), "vertx-job", groupingKey);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }


    public static void main(String[] args) {
        MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
            .setEnabled(true)
            .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true));
        VertxOptions vertxOptions = new VertxOptions()
            .setMetricsOptions(metricsOptions);
        Vertx vertx = Vertx.vertx(vertxOptions);


        // Tell others who I am
        Tags tags = Tags.of(
            Tag.of("cfdc", "idc"),
            Tag.of("env", "prod"),
            Tag.of("business", "ordering"),
            Tag.of("system", "OC"),
            Tag.of("service", "vertx-service"),
            Tag.of("instance", "vertx-service-4321")
        );

        PrometheusMeterRegistry registry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
        registry.config().meterFilter(
            new MeterFilter() {
                @Override
                public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                    return DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .build()
                        .merge(config);
                }
            }).commonTags(tags);

        // Vertx does not include these
        // new JvmGcMetrics().bindTo(registry); // some wrong
        new JvmHeapPressureMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
        new JvmInfoMetrics().bindTo(registry);
        new JvmCompilationMetrics().bindTo(registry);


        vertx.deployVerticle(new MainVerticle());
    }

}
