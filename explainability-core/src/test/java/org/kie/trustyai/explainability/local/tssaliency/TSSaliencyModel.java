package org.kie.trustyai.explainability.local.tssaliency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.kie.trustyai.explainability.model.Feature;
import org.kie.trustyai.explainability.model.Output;
import org.kie.trustyai.explainability.model.PredictionInput;
import org.kie.trustyai.explainability.model.PredictionOutput;
import org.kie.trustyai.explainability.model.PredictionProvider;
import org.kie.trustyai.explainability.model.Type;
import org.kie.trustyai.explainability.model.Value;

public class TSSaliencyModel implements PredictionProvider {

    int NUM_RANDOM = 2000;
    
    Double random[];

    public TSSaliencyModel() {

        random = new Double[NUM_RANDOM];

        for (int i = 0; i < NUM_RANDOM; i++) {
            random[i] = Math.random();
        }
    }

    /**
     * Perform a batch of predictions, given a batch of inputs.
     * 
     * @param inputs the input batch
     * @return a batch of prediction outputs
     */
    public CompletableFuture<List<PredictionOutput>> predictAsync(List<PredictionInput> inputs) {

        CompletableFuture<List<PredictionOutput>> retval = new CompletableFuture<List<PredictionOutput>>();

        int numInputs = inputs.size();
        System.out.println("numInputs = " + numInputs);

        List<PredictionOutput> outputs = new ArrayList<PredictionOutput>(numInputs);

        for (PredictionInput input : inputs) {
            List<Feature> features = input.getFeatures();

            System.out.println("features.size() = " + features.size());

            double y = compute(features);

            Output output = new Output("y", Type.NUMBER, new Value(null), y);

            List<Output> outputList = new ArrayList<Output>(1);
            outputList.add(output);

            PredictionOutput predOut = new PredictionOutput(outputList);

            outputs.add(predOut);
        }

        retval.complete(outputs);

        return retval;
    }

    // Sample Function (f) (input(500, 4), weights(500, 4)):
    // flatten_input = flatten(input) -> 2000
    // flatten_weights = flatten(weights) -> 2000
    // y = sum(flatten_input * flatten_weights) -> single float value
    // return y

    public double compute(List<Feature> features) {

        double sum = 0.0;
        int randomIndex = 0;
        for (Feature feature : features) { // iterate through features vectpr
            // System.out.println("feature = " + feature);

            assert feature.getType() == Type.VECTOR; 

            Value value = feature.getValue();

            double[] elements = value.asVector();
            
            for (double element : elements) {
                assert randomIndex <= NUM_RANDOM;
                
                sum += element * random[randomIndex];
                
                randomIndex += 1;
            }
        }

        return sum;
    }

}
