package org.kie.trustyai.service.payloads.data.download;

import java.util.List;

import com.fasterxml.jackson.databind.node.ValueNode;

public class RowMatcher {
    String columnName;
    String operation;
    List<ValueNode> values;

    public RowMatcher() {
    }

    public RowMatcher(String columnName, String operation, List<ValueNode> values) {
        this.columnName = columnName;
        this.operation = operation;
        this.values = values;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public List<ValueNode> getValues() {
        return values;
    }

    public void setValues(List<ValueNode> values) {
        this.values = values;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    @Override
    public String toString() {
        if (operation.equals("EQUALS")) {
            if (values.size() == 1) {
                return "{" + columnName + " " + operation + " " + values.get(0) + "}";
            } else {
                return "{" + columnName + " " + operation + " one of " + values + "}";
            }
        } else {
            return "{" + columnName + " " + operation + " " + values.get(0) + " and " + values.get(1) + "}";
        }
    }
}
