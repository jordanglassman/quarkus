package io.quarkus.flyway;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2__Test extends BaseJavaMigration {
    @Override
    public void migrate(final Context context) throws Exception {
        System.out.println("migrating!");
    }
}
