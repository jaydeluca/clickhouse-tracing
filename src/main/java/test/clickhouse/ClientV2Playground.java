package test.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandSettings;
import com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

class ClientV2Playground {
    private final Client client = new Client.Builder()
        .addEndpoint("http://localhost:8123")
        .setUsername("default")
        .setPassword("")
        .setDefaultDatabase("default")
        .build();

    void run() throws ExecutionException, InterruptedException, TimeoutException {
        setup();
        basicInsertAndRead();
        pojoExample();
    }

    void setup() throws ExecutionException, InterruptedException, TimeoutException {
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
    }

    void basicInsertAndRead() {
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

    void pojoExample() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("Starting POJO Example");
        String tableName = "pojo_with_json_table";
        String createSQL = "CREATE TABLE " + tableName + " (eventPayload String) ENGINE = MergeTree() ORDER BY tuple()";
        String originalJsonStr = "{\"a\":{\"b\":\"42\"},\"c\":[\"1\",\"2\",\"3\"]}";

        CommandSettings commandSettings = new CommandSettings();
        client.execute("DROP TABLE IF EXISTS " + tableName, commandSettings).get(5, TimeUnit.SECONDS);
        client.execute(createSQL, commandSettings).get(10, TimeUnit.SECONDS);

        System.out.println("Registering POJO");
        client.register(PojoWithJSON.class, client.getTableSchema(tableName, "default"));
        PojoWithJSON pojo = new PojoWithJSON();
        pojo.setEventPayload(originalJsonStr);
        List<Object> data = Collections.singletonList(pojo);

        System.out.println("Inserting POJO");
        InsertSettings insertSettings = new InsertSettings();
        client.insert(tableName, data, insertSettings).get(5, TimeUnit.SECONDS);

        QuerySettings settings = new QuerySettings()
            .setFormat(ClickHouseFormat.CSV);

        System.out.println("Reading POJO");
        try (QueryResponse resp = client.query("SELECT * FROM " + tableName, settings).get(5, TimeUnit.SECONDS)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resp.getInputStream(), UTF_8));
            String jsonStr = StringEscapeUtils.unescapeCsv(reader.lines().findFirst().get());
            System.out.println("JSON: " + jsonStr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static class PojoWithJSON {
        private String eventPayload;
        public String getEventPayload() {
            return eventPayload;
        }
        public void setEventPayload(String eventPayload) {
            this.eventPayload = eventPayload;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PojoWithJSON)) {
                return false;
            }
            PojoWithJSON that = (PojoWithJSON) o;
            return Objects.equals(eventPayload, that.eventPayload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventPayload);
        }

        @Override
        public String toString() {
            return "PojoWithJSON{" +
                "eventPayload='" + eventPayload + '\'' +
                '}';
        }
    }
}
