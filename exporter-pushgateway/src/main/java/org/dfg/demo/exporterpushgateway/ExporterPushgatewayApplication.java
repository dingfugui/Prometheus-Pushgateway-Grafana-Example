package org.dfg.demo.exporterpushgateway;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.pushgateway.PushGateway;
import io.prometheus.metrics.model.snapshots.Unit;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExporterPushgatewayApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(ExporterPushgatewayApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        PushGateway pushGateway = PushGateway.builder()
                .address("localhost:9091")
                .job("example")
                .build();
        try {
            Gauge dataProcessedInBytes = Gauge.builder()
                    .name("data_processed")
                    .help("data processed in the last batch job run")
                    .unit(Unit.BYTES)
                    .register();
            dataProcessedInBytes.set(99);
        } finally {
            pushGateway.push();
        }
    }


}
