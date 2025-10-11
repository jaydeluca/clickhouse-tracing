package test.clickhouse;


import com.clickhouse.client.ClickHouseException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Main {

    public static void main(String[] args) throws ClickHouseException, ExecutionException, InterruptedException, TimeoutException {
//        new ClientV1Playground().run();
        new ClientV2Playground().run();
    }

}

