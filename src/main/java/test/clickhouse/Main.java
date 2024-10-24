package test.clickhouse;

import java.util.*;
import java.util.concurrent.*;

import com.clickhouse.client.*;
import com.clickhouse.client.config.ClickHouseClientOption;
import com.clickhouse.data.*;


public class Main {
    private static ClickHouseClient client;


    public static void main(String[] args) throws ClickHouseException, ExecutionException, InterruptedException {
        clickhouseV1();
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

