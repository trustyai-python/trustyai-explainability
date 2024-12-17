package org.kie.trustyai.service.endpoints.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kie.trustyai.service.payloads.data.download.DataRequestPayload;
import org.kie.trustyai.service.payloads.data.download.RowMatcher;
import org.kie.trustyai.service.payloads.metrics.fairness.group.AdvancedGroupMetricRequest;
import org.kie.trustyai.service.payloads.metrics.fairness.group.GroupMetricRequest;
import org.kie.trustyai.service.payloads.metrics.identity.IdentityMetricRequest;
import org.kie.trustyai.service.payloads.values.reconcilable.ReconcilableFeature;
import org.kie.trustyai.service.payloads.values.reconcilable.ReconcilableOutput;

import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class RequestPayloadGenerator {

    private static final String MODEL_ID = "example1";

    public static GroupMetricRequest correct() {
        GroupMetricRequest request = new GroupMetricRequest();
        request.setProtectedAttribute("gender");
        request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(1)));
        request.setOutcomeName("income");
        request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
        request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
        request.setModelId(MODEL_ID);

        return request;
    }

    public static IdentityMetricRequest identityCorrect() {
        IdentityMetricRequest request = new IdentityMetricRequest();
        request.setColumnName("gender");
        request.setModelId(MODEL_ID);

        return request;
    }

    // multi valued requests disabled until better integrated with ODH UI
    //    public static GroupMetricRequest multiValueCorrect() {
    //        GroupMetricRequest request = new GroupMetricRequest();
    //        request.setProtectedAttribute("age");
    //
    //        List<ValueNode> privAge = new ArrayList<>();
    //        List<ValueNode> unprivAge = new ArrayList<>();
    //        for (int i = 0; i < 50; i++) {
    //            privAge.add(IntNode.valueOf(i));
    //            unprivAge.add(IntNode.valueOf(i + 50));
    //        }
    //        request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(0)));
    //        request.setOutcomeName("income");
    //        request.setPrivilegedAttribute(new ReconcilableFeature(privAge));
    //        request.setUnprivilegedAttribute(new ReconcilableFeature(unprivAge));
    //        request.setModelId(MODEL_ID);
    //
    //        return request;
    //    }
    //
    //    public static GroupMetricRequest multiValueMismatchingType() {
    //        GroupMetricRequest request = new GroupMetricRequest();
    //        request.setProtectedAttribute("age");
    //
    //        List<ValueNode> privAge = new ArrayList<>();
    //        List<ValueNode> unprivAge = new ArrayList<>();
    //        for (int i = 0; i < 50; i++) {
    //            if (i < 25) {
    //                privAge.add(IntNode.valueOf(i));
    //                unprivAge.add(IntNode.valueOf(i + 50));
    //            } else {
    //                privAge.add(TextNode.valueOf("wrong"));
    //                unprivAge.add(TextNode.valueOf("wrong"));
    //            }
    //        }
    //        request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(0)));
    //        request.setOutcomeName("income");
    //        request.setPrivilegedAttribute(new ReconcilableFeature(privAge));
    //        request.setUnprivilegedAttribute(new ReconcilableFeature(unprivAge));
    //        request.setModelId(MODEL_ID);
    //
    //        return request;
    //    }

    public static GroupMetricRequest named(String name) {
        GroupMetricRequest request = new GroupMetricRequest();
        request.setProtectedAttribute("gender");
        request.setFavorableOutcome(new ReconcilableOutput(IntNode.valueOf(1)));
        request.setOutcomeName("income");
        request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
        request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
        request.setModelId(MODEL_ID);
        request.setRequestName(name);

        return request;
    }

    public static GroupMetricRequest incorrectType() {
        GroupMetricRequest request = new GroupMetricRequest();
        request.setProtectedAttribute("gender");
        request.setFavorableOutcome(new ReconcilableOutput(TextNode.valueOf("male")));
        request.setOutcomeName("income");
        request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
        request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
        request.setModelId(MODEL_ID);

        return request;
    }

    public static GroupMetricRequest incorrectInput() {
        GroupMetricRequest request = new GroupMetricRequest();
        request.setProtectedAttribute("city");
        request.setFavorableOutcome(new ReconcilableOutput(TextNode.valueOf("approved")));
        request.setOutcomeName("income");
        request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
        request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
        request.setModelId(MODEL_ID);
        return request;
    }

    public static IdentityMetricRequest correctIdentityInput() {
        IdentityMetricRequest request = new IdentityMetricRequest();
        request.setColumnName("gender");
        request.setModelId(MODEL_ID);
        return request;
    }

    public static IdentityMetricRequest incorrectIdentityInput() {
        IdentityMetricRequest request = new IdentityMetricRequest();
        request.setColumnName("THIS_FIELD_DOES_NOT_EXIST");
        request.setModelId(MODEL_ID);
        return request;
    }

    public static GroupMetricRequest incorrectManyWrongNames() {
        GroupMetricRequest request = new GroupMetricRequest();
        request.setProtectedAttribute("city");
        request.setFavorableOutcome(new ReconcilableOutput(TextNode.valueOf("approved")));
        request.setOutcomeName("icnome");
        request.setPrivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(1)));
        request.setUnprivilegedAttribute(new ReconcilableFeature(IntNode.valueOf(0)));
        request.setModelId(MODEL_ID);
        return request;
    }

    public static GroupMetricRequest incorrectManyWrongTypes() {
        GroupMetricRequest request = new GroupMetricRequest();
        request.setProtectedAttribute("gender");
        request.setFavorableOutcome(new ReconcilableOutput(TextNode.valueOf("approved-doesnt-exist")));
        request.setOutcomeName("income");
        request.setPrivilegedAttribute(new ReconcilableFeature(TextNode.valueOf("lemons")));
        request.setUnprivilegedAttribute(new ReconcilableFeature(DoubleNode.valueOf(1.5)));
        request.setModelId(MODEL_ID);
        return request;
    }

    public static Map<String, Object> unknownType() {
        final Map<String, Object> payload = new HashMap<>();
        payload.put("protectedAttribute", "city");
        Map<String, Object> favorableOutcome = new HashMap<>();
        favorableOutcome.put("type", "FOO");
        favorableOutcome.put("value", "approved");
        payload.put("favorableOutcome", favorableOutcome);
        payload.put("outcomeName", "income");
        Map<String, Object> privilegedAttribute = new HashMap<>();
        privilegedAttribute.put("type", "INT32");
        privilegedAttribute.put("value", 1);
        payload.put("privilegedAttribute", privilegedAttribute);
        Map<String, Object> unprivilegedAttribute = new HashMap<>();
        unprivilegedAttribute.put("type", "INT32");
        unprivilegedAttribute.put("value", 0);
        payload.put("unprivilegedAttribute", unprivilegedAttribute);

        payload.put("modelId", MODEL_ID);

        return payload;
    }

    public static AdvancedGroupMetricRequest advancedCorrect() {
        DataRequestPayload privileged = new DataRequestPayload();
        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("gender", "EQUALS", List.of(new IntNode(0))));
        matchAllList.add(new RowMatcher("race", "EQUALS", List.of(new IntNode(0))));
        privileged.setMatchAll(matchAllList);

        List<RowMatcher> matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(5), new IntNode(10))));
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(50), new IntNode(70))));
        privileged.setMatchAny(matchAnyList);

        List<RowMatcher> matchNoneList = new ArrayList<>();
        matchNoneList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(55), new IntNode(65))));
        privileged.setMatchNone(matchNoneList);

        DataRequestPayload unprivileged = new DataRequestPayload();
        matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("gender", "EQUALS", List.of(new IntNode(1))));
        matchAllList.add(new RowMatcher("race", "EQUALS", List.of(new IntNode(1))));
        unprivileged.setMatchAll(matchAllList);

        matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(10), new IntNode(50))));
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(70), new IntNode(100))));
        unprivileged.setMatchAny(matchAnyList);

        DataRequestPayload favorable = new DataRequestPayload();
        matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("income", "EQUALS", List.of(new IntNode(1))));
        favorable.setMatchAll(matchAllList);

        AdvancedGroupMetricRequest request = new AdvancedGroupMetricRequest();
        request.setModelId(MODEL_ID);
        request.setPrivilegedAttribute(privileged);
        request.setUnprivilegedAttribute(unprivileged);
        request.setFavorableOutcome(favorable);
        return request;
    }

    public static AdvancedGroupMetricRequest advancedNameMappedCorrect() {
        DataRequestPayload privileged = new DataRequestPayload();
        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("genderMapped", "EQUALS", List.of(new IntNode(0))));
        matchAllList.add(new RowMatcher("raceMapped", "EQUALS", List.of(new IntNode(0))));
        privileged.setMatchAll(matchAllList);

        List<RowMatcher> matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("ageMapped", "BETWEEN", List.of(new IntNode(5), new IntNode(10))));
        matchAnyList.add(new RowMatcher("ageMapped", "BETWEEN", List.of(new IntNode(50), new IntNode(70))));
        privileged.setMatchAny(matchAnyList);

        List<RowMatcher> matchNoneList = new ArrayList<>();
        matchNoneList.add(new RowMatcher("ageMapped", "BETWEEN", List.of(new IntNode(55), new IntNode(65))));
        privileged.setMatchNone(matchNoneList);

        DataRequestPayload unprivileged = new DataRequestPayload();
        matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("genderMapped", "EQUALS", List.of(new IntNode(1))));
        matchAllList.add(new RowMatcher("raceMapped", "EQUALS", List.of(new IntNode(1))));
        unprivileged.setMatchAll(matchAllList);

        matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("ageMapped", "BETWEEN", List.of(new IntNode(10), new IntNode(50))));
        matchAnyList.add(new RowMatcher("ageMapped", "BETWEEN", List.of(new IntNode(70), new IntNode(100))));
        unprivileged.setMatchAny(matchAnyList);

        DataRequestPayload favorable = new DataRequestPayload();
        matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("incomeMapped", "EQUALS", List.of(new IntNode(1))));
        favorable.setMatchAll(matchAllList);

        AdvancedGroupMetricRequest request = new AdvancedGroupMetricRequest();
        request.setModelId(MODEL_ID);
        request.setPrivilegedAttribute(privileged);
        request.setUnprivilegedAttribute(unprivileged);
        request.setFavorableOutcome(favorable);
        return request;
    }

    public static AdvancedGroupMetricRequest advancedIncorrect() {
        DataRequestPayload privileged = new DataRequestPayload();
        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("FIELD_DOES_NOT_EXIST", "EQUALS", List.of(new IntNode(0))));
        matchAllList.add(new RowMatcher("race", "EQUALS", List.of(new IntNode(0))));
        privileged.setMatchAll(matchAllList);

        List<RowMatcher> matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("age", "NO_SUCH_OPERATION", List.of(new IntNode(5), new IntNode(10))));
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(50), new IntNode(70))));
        privileged.setMatchAny(matchAnyList);

        List<RowMatcher> matchNoneList = new ArrayList<>();
        matchNoneList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(55), new IntNode(65))));
        privileged.setMatchNone(matchNoneList);

        DataRequestPayload unprivileged = new DataRequestPayload();
        matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("gender", "EQUALS", List.of(new IntNode(1))));
        matchAllList.add(new RowMatcher("race", "EQUALS", List.of(new IntNode(1))));
        unprivileged.setMatchAll(matchAllList);

        matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(10), new IntNode(50))));
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(70), new IntNode(100))));
        unprivileged.setMatchAny(matchAnyList);

        DataRequestPayload favorable = new DataRequestPayload();
        matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("income", "EQUALS", List.of(new TextNode("WRONG_VALUE_TYPE"))));
        favorable.setMatchAll(matchAllList);

        AdvancedGroupMetricRequest request = new AdvancedGroupMetricRequest();
        request.setModelId(MODEL_ID);
        request.setPrivilegedAttribute(privileged);
        request.setUnprivilegedAttribute(unprivileged);
        request.setFavorableOutcome(favorable);
        return request;
    }

}
