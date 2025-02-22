package org.example;

import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

public class BenchmarkService {

    private static final int SELECTS_COUNT = 500_000;

    public static void runBenchmark() {
        List<Mono<Long>> dbCallMonos = LongStream.rangeClosed(1, SELECTS_COUNT)
                .boxed()
                .map(i -> selectCompanyById(10L)) // Hardcoded the same id 10. Or use "i" for more random.
                //.map(i -> selectCompanyByIdWithPause(10L)) // Uncomment to test connections concurrency.
                .toList();

        executeAllAndPrintDuration(dbCallMonos);
    }

    private static void executeAllAndPrintDuration(List<Mono<Long>> dbCallMonos) {
        long timeStartNs = System.nanoTime();

        Mono<Long> zip = Mono.zip(dbCallMonos, results -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timeStartNs));
        zip.subscribe(durationMs -> {
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs);
            durationMs = durationMs - TimeUnit.SECONDS.toMillis(seconds);

            System.out.println("select " + SELECTS_COUNT + " in " + seconds + "." + durationMs + "s.");
        });
    }

    private static Mono<Long> selectCompanyById(Long id) {
        return DataSource.getDatabaseClient().sql("SELECT * FROM companies WHERE company_id = $1")
                .bind(0, id)
                .fetch()
                .first()
                .map(row -> ((Number) row.get("company_id")).longValue());
    }

    private static Mono<Long> selectCompanyByIdWithPause(Long id) {
        return DataSource.getDatabaseClient().sql("SELECT *, pg_sleep(2) FROM companies WHERE company_id = $1")
                .bind(0, id)
                .fetch()
                .first()
                .map(row -> ((Number) row.get("company_id")).longValue());
    }

    //
    // Uncomment this to try raw Connections instead of DatabaseClient. It's a bit faster.
    //
    /*private static Mono<Long> selectCompanyById(Long id) {
        return Mono.usingWhen(
                DataSource.getConnection(),
                connection -> Mono.from(connection
                        .createStatement("SELECT * FROM companies WHERE company_id = $1")
                        .bind("$1", id)
                        .execute()
                ).flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Long.class)))),
                Connection::close
        );
    }*/
}
