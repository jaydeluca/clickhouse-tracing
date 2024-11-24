package test.clickhouse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.clickhouse.client.*;
import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.metrics.ServerMetrics;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.config.ClickHouseClientOption;
import com.clickhouse.data.*;


public class Main {
    private static ClickHouseClient client;

    private static final String TABLE_NAME = "default";


    public static void main(String[] args) throws ClickHouseException, ExecutionException, InterruptedException, TimeoutException {
//        clickhouseV1();
        clickhouseV2();
    }


    private static void clickhouseV2() throws ExecutionException, InterruptedException, TimeoutException {
        Client client = new Client.Builder()
            .addEndpoint("http://localhost:8123")
            .setUsername("default")
            .setPassword("")
            .setDefaultDatabase("default")
            .build();

        var dbInit = new ByteArrayInputStream("""
            create table if not exists my_metrics (
            id Nullable(Float64),
            value Nullable(Float64),
            type Nullable(String),
            ) engine = MergeTree order by ();
            """.getBytes());

        BufferedReader reader = new BufferedReader(new InputStreamReader(dbInit));
        String sql = reader.lines().collect(Collectors.joining("\n"));
        System.out.println("Executing Create Table: " + sql);
        client.query(sql).get(3, TimeUnit.SECONDS);
        System.out.println("Table initialized");

        String insertSQL = """
        insert into my_metrics (id, value, type) values
        (1.0, 100.0, 'type1'),
        (2.0, 200.0, 'type1'),
        (3.0, 300.0, 'type1');
        """;

        try {
            System.out.println("Executing Insert: " + insertSQL);
            client.query(insertSQL).get(3, TimeUnit.SECONDS);
            System.out.println("Data inserted successfully");
        } catch (Exception e) {
            System.out.println("Failed to insert data");
            System.out.println(e);
        }

        final String readSql = "select * from my_metrics limit 10";

        try (QueryResponse response = client.query(readSql).get(3, TimeUnit.SECONDS);) {
            ClickHouseBinaryFormatReader newReader = client.newBinaryFormatReader(response);

            while (newReader.hasNext()) {
                newReader.next();
                double id = newReader.getDouble("id");
                String title = newReader.getString("type");
                double value = newReader.getDouble("value");
                System.out.println("id: " + id + ", title: " + title + ", value: " + value);
            }
        } catch (Exception e) {
            System.out.println("Failed to read data" + e);
        }

    }

    private static void clickhouseV1() throws ClickHouseException, ExecutionException, InterruptedException {
        String clickhouseUrl = System.getenv().getOrDefault("CLICKHOUSE_URL", "http://localhost:8123");

        ClickHouseNodes servers = ClickHouseNodes.of(clickhouseUrl + "/default?compress=0");
        ClickHouseResponse response;

        setup(servers);

        client = ClickHouseClient.builder()
            .config(new ClickHouseConfig(Map.of(ClickHouseClientOption.COMPRESS, false)))
            .nodeSelector(ClickHouseNodeSelector.of(ClickHouseProtocol.HTTP))
            .build();

        response = client.read(servers)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from test_table limit :limit")
            .params(2)
            .executeAndWait();

        System.out.println("Select * from test_table limit 2:");

        for (ClickHouseRecord r : response.records()) {
            System.out.println(r.getValue(0).asString());
        }
        response.close();

        response = client.write(servers.apply(servers.getNodeSelector()))
            .query("insert into test_table values(:val1)(:val2)(:val3)")
            .params(Map.of("val1", "1", "val2", "2", "val3", "3"))
            .executeAndWait();

        System.out.println("rows inserted into test_table:");
        System.out.println(response.getSummary().getWrittenRows());
        response.close();

        System.out.println("select * from test_table:");
        response = client.read(servers)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from test_table")
            .executeAndWait();

        for (ClickHouseRecord r : response.records()) {
            System.out.println(r.getValue(0).asString());
        }
        response.close();

        System.out.println("select * from test_table async:");
        CompletableFuture<ClickHouseResponse> future = client.read(servers)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from test_table")
            .execute();

        ClickHouseResponse result = future.get();
        response.close();

        System.out.println(result.getSummary().getReadRows());


        response = client.read(servers)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from test_table where s=:value limit :limit")
            .params("1", 2)
            .executeAndWait();

        System.out.println("select * with limit 2:");
        for (ClickHouseRecord r : response.records()) {
            System.out.println(r.getValue(0).asString());
        }
        response.close();

        ClickHouseRequest<?> request = client
            .read(servers)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes);

        response =
            request
                .query(ClickHouseParameterizedQuery.of(
                    request.getConfig(),
                    "select * from test_table where s=:val"))
                .params(Map.of("val", "'2'"))
                .executeAndWait();

        System.out.println("select * where s=2:");
        for (ClickHouseRecord r : response.records()) {
            System.out.println(r.getValue(0).asString());
        }
        response.close();

        try (ClickHouseResponse response1 = client.read(servers)
            .format(ClickHouseFormat.RowBinaryWithNamesAndTypes)
            .query("select * from nonexistent")
            .executeAndWait()) {
            //...
        } catch (ClickHouseException e) {
            System.out.println("Exception: " + e.getMessage());
        }
        client.close();
    }

    private static void setup(ClickHouseNodes servers) throws ExecutionException, InterruptedException {

        CompletableFuture<List<ClickHouseResponseSummary>> future = ClickHouseClient.send(servers.apply(servers.getNodeSelector()),
            "create database if not exists my_base",
            "use my_base",
            "create table if not exists test_table(s String) engine=Memory",
            "truncate test_table",
            "insert into test_table values('1')('2')('3')");
        future.get();
    }
}

