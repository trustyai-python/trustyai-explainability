package org.kie.trustyai.service.data.utils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.endpoints.data.DownloadEndpoint;
import org.kie.trustyai.service.payloads.data.download.DataRequestPayload;
import org.kie.trustyai.service.payloads.data.download.MatchOperation;
import org.kie.trustyai.service.payloads.data.download.RowMatcher;
import org.kie.trustyai.service.payloads.values.DataType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ValueNode;

public class DownloadUtils {

    public static DataType getDataType(StorageMetadata storageMetadata, RowMatcher rowMatcher) {
        if (storageMetadata.getInputSchema().getNameMappedItems().containsKey(rowMatcher.getColumnName())) {
            return storageMetadata.getInputSchema().getNameMappedItems().get(rowMatcher.getColumnName()).getType();
        } else {
            return storageMetadata.getOutputSchema().getNameMappedItems().get(rowMatcher.getColumnName()).getType();
        }
    }

    public static Dataframe betweenMatcher(Dataframe df, RowMatcher rowMatcher, int columnIndex, DataType columnType, boolean invert) {
        ValueNode rawLow = rowMatcher.getValues().get(0);
        ValueNode rawHigh = rowMatcher.getValues().get(1);
        if (columnType == DataType.FLOAT) {
            float low = rawLow.floatValue();
            float high = rawHigh.floatValue();
            return df.filterByColumnValue(columnIndex,
                    value -> {
                        float val = (float) value.getUnderlyingObject();
                        return invert ? low > val || val >= high : low <= val && val < high;
                    });
        } else if (columnType == DataType.DOUBLE) {
            double low = rawLow.doubleValue();
            double high = rawHigh.doubleValue();
            return df.filterByColumnValue(columnIndex,
                    value -> {
                        double val = (double) value.getUnderlyingObject();
                        return invert ? low > val || val >= high : low <= val && val < high;
                    });
        } else if (columnType == DataType.INT32) {
            int low = rawLow.intValue();
            int high = rawHigh.intValue();
            return df.filterByColumnValue(columnIndex,
                    value -> {
                        int val = (int) value.getUnderlyingObject();
                        return invert ? low > val || val >= high : low <= val && val < high;
                    });
        } else if (columnType == DataType.INT64) {
            long low = rawLow.longValue();
            long high = rawHigh.longValue();
            return df.filterByColumnValue(columnIndex,
                    value -> {
                        long val = (long) value.getUnderlyingObject();
                        return invert ? low > val || val >= high : low <= val && val < high;
                    });
        } else {
            return df.filterByColumnValue(columnIndex, value -> invert);
        }
    }

    public static Dataframe betweenMatcherInternal(Dataframe df, RowMatcher rowMatcher, Dataframe.InternalColumn internalColumn, boolean invert) {
        ValueNode rawLow = rowMatcher.getValues().get(0);
        ValueNode rawHigh = rowMatcher.getValues().get(1);
        if (internalColumn == Dataframe.InternalColumn.INDEX) {
            int low = rawLow.intValue();
            int high = rawHigh.intValue();
            return df.filterByInternalColumnValue(internalColumn,
                    value -> {
                        float val = (int) value.getUnderlyingObject();
                        return invert ? low > val || val >= high : low <= val && val < high;
                    });
        } else if (internalColumn == Dataframe.InternalColumn.TIMESTAMP) {
            LocalDateTime low = LocalDateTime.parse(rawLow.textValue());
            LocalDateTime high = LocalDateTime.parse(rawHigh.textValue());
            return df.filterByInternalColumnValue(internalColumn,
                    value -> {
                        LocalDateTime val = (LocalDateTime) value.getUnderlyingObject();
                        // l <= x < h for datetimes
                        boolean condition = (low.isBefore(val) || low.isEqual(val)) && val.isBefore(high);
                        return invert != condition;
                    });
            //        } else if (internalColumn == Dataframe.InternalColumn.GROUND_TRUTH){  //todo
            //            double low = rawLow.numberValue().doubleValue();
            //            double high = rawHigh.numberValue().doubleValue();
            //            return df.filterByInternalColumnValue(internalColumn,
            //                    value -> {
            //                        double val = value.asNumber();
            //                        return invert ? low > val || val >= high : low <= val && val < high;
            //                    });
        } else {
            return df.filterByInternalColumnValue(internalColumn, value -> invert);
        }
    }

    public static Dataframe equalsMatcher(Dataframe df, RowMatcher rowMatcher, int columnIndex, DataType columnType, boolean invert) {
        if (columnType == DataType.BOOL) {
            Set<Boolean> equalsVals = rowMatcher.getValues().stream().map(JsonNode::booleanValue).collect(Collectors.toSet());
            return df.filterByColumnValue(columnIndex, value -> invert ^ equalsVals.contains((boolean) value.getUnderlyingObject()));
        } else if (columnType == DataType.FLOAT) {
            Set<Float> equalsVals = rowMatcher.getValues().stream().map(JsonNode::floatValue).collect(Collectors.toSet());
            return df.filterByColumnValue(columnIndex, value -> invert ^ equalsVals.contains((float) value.getUnderlyingObject()));
        } else if (columnType == DataType.DOUBLE) {
            Set<Double> equalsVals = rowMatcher.getValues().stream().map(JsonNode::doubleValue).collect(Collectors.toSet());
            return df.filterByColumnValue(columnIndex, value -> invert ^ equalsVals.contains((double) value.getUnderlyingObject()));
        } else if (columnType == DataType.INT32) {
            Set<Integer> equalsVals = rowMatcher.getValues().stream().map(JsonNode::intValue).collect(Collectors.toSet());
            return df.filterByColumnValue(columnIndex, value -> invert ^ equalsVals.contains((int) value.getUnderlyingObject()));
        } else if (columnType == DataType.INT64) {
            Set<Long> equalsVals = rowMatcher.getValues().stream().map(JsonNode::longValue).collect(Collectors.toSet());
            return df.filterByColumnValue(columnIndex, value -> invert ^ equalsVals.contains((long) value.getUnderlyingObject()));
        } else if (columnType == DataType.STRING) {
            Set<String> equalsVals = rowMatcher.getValues().stream().map(JsonNode::asText).collect(Collectors.toSet());
            return df.filterByColumnValue(columnIndex, value -> invert ^ equalsVals.contains((String) value.getUnderlyingObject()));
        } else {
            return df.filterByColumnValue(columnIndex, value -> invert);
        }
    }

    public static Dataframe equalsMatcherInternal(Dataframe df, RowMatcher rowMatcher, Dataframe.InternalColumn internalColumn, boolean invert) {
        if (internalColumn == Dataframe.InternalColumn.ID || internalColumn == Dataframe.InternalColumn.TAG) {
            Set<String> equalsVals = rowMatcher.getValues().stream().map(JsonNode::textValue).collect(Collectors.toSet());
            return df.filterByInternalColumnValue(internalColumn, value -> invert ^ equalsVals.contains((String) value.getUnderlyingObject()));
        } else if (internalColumn == Dataframe.InternalColumn.TIMESTAMP) {
            Set<LocalDateTime> equalsVals = rowMatcher.getValues().stream().map(v -> LocalDateTime.parse(v.textValue())).collect(Collectors.toSet());
            return df.filterByInternalColumnValue(internalColumn, value -> invert ^ equalsVals.contains((LocalDateTime) value.getUnderlyingObject()));
        } else if (internalColumn == Dataframe.InternalColumn.INDEX) {
            Set<Integer> equalsVals = rowMatcher.getValues().stream().map(JsonNode::intValue).collect(Collectors.toSet());
            return df.filterByInternalColumnValue(internalColumn, value -> invert ^ equalsVals.contains((int) value.getUnderlyingObject()));
        } else {
            return df.filterByInternalColumnValue(internalColumn, value -> invert);
        }
    }

    public static Dataframe applyMatches(Dataframe df, StorageMetadata metadata, DataRequestPayload requestPayload) {
        for (RowMatcher rowMatcher : requestPayload.getMatchAll()) {
            MatchOperation operation = MatchOperation.valueOf(rowMatcher.getOperation());
            if (rowMatcher.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                Dataframe.InternalColumn internalColumn = Dataframe.InternalColumn.valueOf(rowMatcher.getColumnName().replace(DownloadEndpoint.TRUSTY_PREFIX, ""));
                if (operation == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcherInternal(df, rowMatcher, internalColumn, false);
                } else if (operation == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcherInternal(df, rowMatcher, internalColumn, false);
                }
            } else {
                int columnIndex = df.getColumnNames().indexOf(rowMatcher.getColumnName());
                DataType columnType = DownloadUtils.getDataType(metadata, rowMatcher);

                // row match
                if (operation == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcher(df, rowMatcher, columnIndex, columnType, false);
                } else if (operation == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcher(df, rowMatcher, columnIndex, columnType, false);
                }
            }
        }

        for (RowMatcher rowMatcher : requestPayload.getMatchNone()) {
            MatchOperation operation = MatchOperation.valueOf(rowMatcher.getOperation());
            if (rowMatcher.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                Dataframe.InternalColumn internalColumn = Dataframe.InternalColumn.valueOf(rowMatcher.getColumnName().replace(DownloadEndpoint.TRUSTY_PREFIX, ""));
                if (operation == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcherInternal(df, rowMatcher, internalColumn, true);
                } else if (operation == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcherInternal(df, rowMatcher, internalColumn, true);
                }
            } else {
                int columnIndex = df.getColumnNames().indexOf(rowMatcher.getColumnName());
                DataType columnType = DownloadUtils.getDataType(metadata, rowMatcher);

                // row match
                if (operation == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcher(df, rowMatcher, columnIndex, columnType, true);
                } else if (operation == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcher(df, rowMatcher, columnIndex, columnType, true);
                }
            }
        }

        Dataframe returnDF;
        if (!requestPayload.getMatchAny().isEmpty()) {
            returnDF = df.filterByColumnValue(0, v -> false); //get null df
            for (RowMatcher rowMatcher : requestPayload.getMatchAny()) {
                MatchOperation operation = MatchOperation.valueOf(rowMatcher.getOperation());
                if (rowMatcher.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                    Dataframe.InternalColumn internalColumn = Dataframe.InternalColumn.valueOf(rowMatcher.getColumnName().replace(DownloadEndpoint.TRUSTY_PREFIX, ""));
                    if (operation == MatchOperation.BETWEEN) {
                        returnDF.addPredictions(DownloadUtils.betweenMatcherInternal(df, rowMatcher, internalColumn, false).asPredictions());
                    } else if (operation == MatchOperation.EQUALS) {
                        returnDF.addPredictions(DownloadUtils.equalsMatcherInternal(df, rowMatcher, internalColumn, false).asPredictions());
                    }
                } else {
                    int columnIndex = df.getColumnNames().indexOf(rowMatcher.getColumnName());
                    DataType columnType = DownloadUtils.getDataType(metadata, rowMatcher);

                    // row match
                    if (operation == MatchOperation.BETWEEN) {
                        returnDF.addPredictions(DownloadUtils.betweenMatcher(df, rowMatcher, columnIndex, columnType, false).asPredictions());
                    } else if (operation == MatchOperation.EQUALS) {
                        returnDF.addPredictions(DownloadUtils.equalsMatcher(df, rowMatcher, columnIndex, columnType, false).asPredictions());
                    }
                }
            }
        } else {
            returnDF = df;
        }

        return returnDF;
    }
}
