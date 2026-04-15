package SecureDataSharing.ml;

import java.util.List;

public class MLPredictionRequest {
    private List<Double> features;

    public MLPredictionRequest() {
    }

    public MLPredictionRequest(List<Double> features) {
        this.features = features;
    }

    public List<Double> getFeatures() {
        return features;
    }

    public void setFeatures(List<Double> features) {
        this.features = features;
    }
}
