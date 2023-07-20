/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.trustyai.service.payloads.explainability;

import java.util.Map;

public class CounterfactualExplanationRequest extends LocalExplanationRequest {

    private Map<String, String> goals;

    public Map<String, String> getGoals() {
        return goals;
    }

    public void setGoals(Map<String, String> goals) {
        this.goals = goals;
    }
}
