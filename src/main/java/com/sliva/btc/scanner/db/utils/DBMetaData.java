/*
 * Copyright 2020 Sliva Co.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sliva.btc.scanner.db.utils;

import static com.google.common.base.Preconditions.checkArgument;
import com.sliva.btc.scanner.db.DBConnectionSupplier;
import static com.sliva.btc.scanner.db.utils.DbResultSetUtils.executeQueryToList;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;

/**
 *
 * @author Sliva Co
 */
@ToString
public class DBMetaData {

    @Getter
    private final Map<String, Table> tables;
    private final Set<String> allFields;
    private final Set<String> indexedFields;

    public DBMetaData(DBConnectionSupplier con) {
        this.tables = Collections.unmodifiableMap(_collect(con));
        allFields = tables.values().stream().flatMap(t -> t.fieldNames.stream().map(fn -> t.name + '.' + fn)).collect(Collectors.toSet());
        indexedFields = tables.values().stream().flatMap(t -> t.indexes.stream().map(i -> t.name + '.' + i.fields.get(0).name)).collect(Collectors.toSet());
    }

    public boolean hasTable(String tableName) {
        checkArgument(tableName != null, "Argument 'tableName' is null");
        return tables.containsKey(tableName.toLowerCase());
    }

    public boolean hasAllTables(String... tableNames) {
        return Stream.of(tableNames).map(String::toLowerCase).allMatch(tables::containsKey);
    }

    public boolean hasField(String tableName, String fieldName) {
        checkArgument(tableName != null, "Argument 'tableName' is null");
        checkArgument(fieldName != null, "Argument 'fieldName' is null");
        return hasField(tableName + '.' + fieldName);
    }

    /**
     * Check if field exists.
     *
     * @param fullFieldName field name prefixed with table name, separated by
     * dot ("."), i.e. table_name.field_name
     * @return true if table exists and has the field
     */
    public boolean hasField(String fullFieldName) {
        checkArgument(fullFieldName != null, "Argument 'fullFieldName' is null");
        return allFields.contains(fullFieldName.toLowerCase());
    }

    public boolean isIndexed(String tableName, String fieldName) {
        checkArgument(tableName != null, "Argument 'tableName' is null");
        checkArgument(fieldName != null, "Argument 'fieldName' is null");
        return isIndexed(tableName + '.' + fieldName);
    }

    /**
     * Check if field is indexed. (DB has an index with this field included as
     * first in the list)
     *
     * @param tableFieldName field name prefixed with table name, separated by
     * dot ("."), i.e. table_name.field_name
     * @return true if DB has an index on the field
     */
    public boolean isIndexed(String tableFieldName) {
        checkArgument(tableFieldName != null, "Argument 'tableFieldName' is null");
        return indexedFields.contains(tableFieldName.toLowerCase());
    }

    @SneakyThrows(SQLException.class)
    private Map<String, Table> _collect(DBConnectionSupplier con) {
        String dbName = con.getDBName();
        DatabaseMetaData md = con.get().getMetaData();
        List<String> tableNames = executeQueryToList(md.getTables(dbName, null, null, new String[]{"TABLE"}), rs -> rs.getString("TABLE_NAME"));
        return tableNames.stream().map(tableName -> {
            List<IndexRec> indexRecords = loadIndexes(dbName, tableName, md);
            List<Index> indexes = indexRecords.stream()
                    .map(IndexRec::getIndexName).distinct()
                    .map(indexName -> new Index(indexName,
                    indexRecords.stream().filter(r -> indexName.equals(r.getIndexName()))
                            .sorted(Comparator.comparingInt(IndexRec::getFieldPosition))
                            .map(r -> new IndexField(r.fieldName.toLowerCase(), r.ascending)).collect(Collectors.toList()))
                    ).collect(Collectors.toList());
            List<FieldRec> fieldRecords = loadFields(dbName, tableName, md);
            List<String> fields = fieldRecords.stream().sorted(Comparator.comparingInt(FieldRec::getFieldPosition)).map(FieldRec::getFieldName).map(String::toLowerCase).collect(Collectors.toList());
            return new Table(tableName.toLowerCase(), fields, indexes);
        }).collect(Collectors.toMap(Table::getName, Function.identity()));
    }

    @SneakyThrows(SQLException.class)
    private List<IndexRec> loadIndexes(String dbName, String tableName, DatabaseMetaData md) {
        return executeQueryToList(md.getIndexInfo(dbName, null, tableName, false, false),
                rs -> new IndexRec(rs.getString("TABLE_NAME"), rs.getString("INDEX_NAME"), rs.getShort("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"), "A".equalsIgnoreCase(rs.getString("ASC_OR_DESC"))));
    }

    @SneakyThrows(SQLException.class)
    private List<FieldRec> loadFields(String dbName, String tableName, DatabaseMetaData md) {
        return executeQueryToList(md.getColumns(dbName, null, tableName, null), rs -> new FieldRec(tableName, rs.getString("COLUMN_NAME"), rs.getInt("ORDINAL_POSITION")));
    }

    @Getter
    @AllArgsConstructor
    @ToString
    private static class Table {

        private final String name;
        private final List<String> fieldNames;
        private final List<Index> indexes;
    }

    @AllArgsConstructor
    @ToString
    private static class Index {

        private final String name;
        private final List<IndexField> fields;
    }

    @AllArgsConstructor
    @ToString
    private static class IndexField {

        private final String name;
        private final boolean ascending;
    }

    @Getter
    @AllArgsConstructor
    private static class FieldRec {

        private final String tableName;
        private final String fieldName;
        private final int fieldPosition;
    }

    @Getter
    @AllArgsConstructor
    private static class IndexRec {

        private final String tableName;
        private final String indexName;
        private final short fieldPosition;
        private final String fieldName;
        private final boolean ascending;
    }
}
