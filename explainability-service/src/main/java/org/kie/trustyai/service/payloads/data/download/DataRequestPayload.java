package org.kie.trustyai.service.payloads.data.download;

import java.util.ArrayList;
import java.util.List;

public class DataRequestPayload {
    List<RowMatcher> matchAny = new ArrayList<>();
    List<RowMatcher> matchAll = new ArrayList<>();
    List<RowMatcher> matchNone = new ArrayList<>();

    public List<RowMatcher> getMatchAny() {
        return matchAny;
    }

    public void setMatchAny(List<RowMatcher> matchAny) {
        this.matchAny = matchAny;
    }

    public List<RowMatcher> getMatchAll() {
        return matchAll;
    }

    public void setMatchAll(List<RowMatcher> matchAll) {
        this.matchAll = matchAll;
    }

    public List<RowMatcher> getMatchNone() {
        return matchNone;
    }

    public void setMatchNone(List<RowMatcher> matchNone) {
        this.matchNone = matchNone;
    }

    public String prettyPrint() {
        List<String> builder = new ArrayList<>();

        if (!matchAll.isEmpty()) {
            builder.add("ALL of: " + matchAll);
        }
        if (!matchAny.isEmpty()) {
            builder.add("ANY of: " + matchAny);
        }
        if (!matchNone.isEmpty()) {
            builder.add("NONE of: " + matchNone);
        }
        return String.join(", ", builder);
    }
}
