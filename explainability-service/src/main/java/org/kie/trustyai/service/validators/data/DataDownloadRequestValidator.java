package org.kie.trustyai.service.validators.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.endpoints.data.DownloadEndpoint;
import org.kie.trustyai.service.payloads.data.download.DataRequestPayload;
import org.kie.trustyai.service.payloads.data.download.MatchOperation;
import org.kie.trustyai.service.payloads.data.download.RowMatcher;
import org.kie.trustyai.service.validators.generic.GenericValidationUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DataDownloadRequestValidator implements ConstraintValidator<ValidDataDownloadRequest, DataRequestPayload> {

    @Inject
    Instance<DataSource> dataSource;

    @Override
    public void initialize(ValidDataDownloadRequest constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    // see if passed operation exists as a rowmatch operation
    private static boolean checkOperationExists(RowMatcher rowMatch, String modelId, ConstraintValidatorContext context) {
        try {
            MatchOperation.valueOf(rowMatch.getOperation());
        } catch (IllegalArgumentException e) {
            context.buildConstraintViolationWithTemplate(String.format(
                    "RowMatch operation must be one of %s, got %s",
                    Arrays.toString(MatchOperation.values()),
                    rowMatch.getOperation()))
                    .addPropertyNode(modelId)
                    .addPropertyNode(rowMatch.getColumnName())
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    // check if the trustyai internal column is a valid internal column name
    private static boolean checkInternalColumnValid(RowMatcher rowMatch, String modelId, String internalColumn, ConstraintValidatorContext context) {
        try {
            Dataframe.InternalColumn.valueOf(internalColumn);
        } catch (IllegalArgumentException e) {
            context.buildConstraintViolationWithTemplate(String.format(
                    "Invalid internal column passed, %s* columns must be one of %s, got %s",
                    DownloadEndpoint.TRUSTY_PREFIX,
                    Arrays.toString(Dataframe.InternalColumn.values()),
                    internalColumn))
                    .addPropertyNode(modelId)
                    .addPropertyNode(rowMatch.getColumnName())
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    // check that the operation chosen for an internal column makes semantic sense
    private static boolean checkInternalColumnOperationValid(RowMatcher rowMatch, String modelId, String internalColumn, ConstraintValidatorContext context) {
        List<String> validBetweens = List.of("TIMESTAMP", "INDEX");
        if (rowMatch.getOperation().equals("BETWEEN") && !validBetweens.contains(internalColumn)) {
            context.buildConstraintViolationWithTemplate(String.format(
                    "BETWEEN operation not applicable to internal column %s",
                    internalColumn))
                    .addPropertyNode(modelId)
                    .addPropertyNode(rowMatch.getColumnName())
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    // if a timestamp is passed, make sure it's parseable
    private static boolean checkAllTimestampsParseable(RowMatcher rowMatch, String modelId, ConstraintValidatorContext context) {
        boolean outcome = true;
        for (JsonNode v : rowMatch.getValues()) {
            try {
                LocalDateTime.parse(v.textValue());
            } catch (DateTimeParseException e) {
                context.buildConstraintViolationWithTemplate(String.format(
                        "Passed datetime %s is unparseable as an ISO_LOCAL_DATE_TIME: %s", v, e.getMessage()))
                        .addPropertyNode(modelId)
                        .addPropertyNode(rowMatch.getColumnName())
                        .addConstraintViolation();
                outcome = false;
            }
        }
        return outcome;
    }

    // make sure the passed values are compatible with the between operation
    private static boolean checkCompatibilityWithBetweenOperation(RowMatcher rowMatch, String modelId, ConstraintValidatorContext context) {
        boolean outcome = true;
        if (rowMatch.getValues().size() != 2) {
            context.buildConstraintViolationWithTemplate(
                    String.format(
                            "BETWEEN operation must contain exactly two values, describing the lower and upper bounds of the desired range. Received %d values: %s",
                            rowMatch.getValues().size(), rowMatch.getValues().stream().map(BaseJsonNode::toString).collect(Collectors.toList())))
                    .addPropertyNode(modelId)
                    .addPropertyNode(rowMatch.getColumnName())
                    .addConstraintViolation();
            outcome = false;
        }

        List<ValueNode> nonNumerics = rowMatch.getValues().stream().filter(vn -> !vn.isNumber()).collect(Collectors.toList());
        if (!nonNumerics.isEmpty() && !rowMatch.getColumnName().equals(DownloadEndpoint.TRUSTY_PREFIX + "TIMESTAMP")) {
            context.buildConstraintViolationWithTemplate(
                    String.format(
                            "BETWEEN operation must only contain numbers, describing the lower and upper bounds of the desired range. Received non-numeric values: %s",
                            nonNumerics))
                    .addPropertyNode(modelId)
                    .addPropertyNode(rowMatch.getColumnName())
                    .addConstraintViolation();
            outcome = false;
        }

        return outcome;
    }

    private static boolean checkColumnTypeCompatibility(RowMatcher rowMatch, String modelId, StorageMetadata storageMetadata, ConstraintValidatorContext context) {
        boolean feature = storageMetadata.getInputSchema().getNameMappedItems().containsKey(rowMatch.getColumnName());
        boolean outcome = true;
        for (ValueNode vn : rowMatch.getValues()) {
            if (feature) {
                outcome = GenericValidationUtils.validateFeatureColumnType(context, storageMetadata, modelId, rowMatch.getColumnName(), List.of(vn)) && outcome;
            } else {
                outcome = GenericValidationUtils.validateOutputColumnType(context, storageMetadata, modelId, rowMatch.getColumnName(), List.of(vn)) && outcome;
            }
        }
        return outcome;
    }

    public static boolean manualValidation(DataRequestPayload dataRequestPayload, ConstraintValidatorContext context, Instance<DataSource> dataSource) {
        context.disableDefaultConstraintViolation();
        final String modelId = dataRequestPayload.getModelId();

        if (!GenericValidationUtils.validateModelId(context, dataSource, modelId)) {
            return false;
        } else {
            boolean result = true;

            final StorageMetadata storageMetadata = dataSource.get().getMetadata(modelId);
            for (RowMatcher rowMatch : Stream.of(dataRequestPayload.getMatchAll(), dataRequestPayload.getMatchAny(), dataRequestPayload.getMatchNone()).flatMap(Collection::stream)
                    .collect(Collectors.toList())) {
                boolean validOp = checkOperationExists(rowMatch, modelId, context);
                result = validOp && result;

                if (rowMatch.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                    String internalColumn = rowMatch.getColumnName().replace(DownloadEndpoint.TRUSTY_PREFIX, "");

                    // check internal column specification
                    result = checkInternalColumnValid(rowMatch, modelId, internalColumn, context) && result;

                    // check that the between operation makes semantic sense for this internal column
                    result = checkInternalColumnOperationValid(rowMatch, modelId, internalColumn, context) && result;

                    // check that any passed timestamps are ISO parseable
                    if (internalColumn.equals("TIMESTAMP")) {
                        result = checkAllTimestampsParseable(rowMatch, modelId, context) && result;
                    }
                } else {
                    // make sure the column exists as a feature/output in the data
                    result = GenericValidationUtils.validateColumnName(context, storageMetadata, modelId, rowMatch.getColumnName()) && result;

                    // make sure passed values are compatible with the chosen row
                    // ** SHORT-CUT IF COLUMN DOES NOT EXIST **
                    result = result && checkColumnTypeCompatibility(rowMatch, modelId, storageMetadata, context);
                }

                // for the between operation, check that values passed have the right count and type
                // short-cut if the operation is invalid
                if (validOp && MatchOperation.valueOf(rowMatch.getOperation()) == MatchOperation.BETWEEN) {
                    result = checkCompatibilityWithBetweenOperation(rowMatch, modelId, context) && result;
                }
            }

            return result;
        }
    }

    @Override
    public boolean isValid(DataRequestPayload dataRequestPayload, ConstraintValidatorContext context) {
        return manualValidation(dataRequestPayload, context, dataSource);
    }
}
