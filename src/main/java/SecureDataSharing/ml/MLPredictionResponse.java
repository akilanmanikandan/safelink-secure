package SecureDataSharing.ml;

import java.util.List;

public class MLPredictionResponse {
    private String status;
    private int ml_prediction;
    private double ml_score;
    private int risk_score;
    private List<String> reasons;
    private String error;

    public MLPredictionResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getMl_prediction() {
        return ml_prediction;
    }

    public void setMl_prediction(int ml_prediction) {
        this.ml_prediction = ml_prediction;
    }

    public double getMl_score() {
        return ml_score;
    }

    public void setMl_score(double ml_score) {
        this.ml_score = ml_score;
    }

    public int getRisk_score() {
        return risk_score;
    }

    public void setRisk_score(int risk_score) {
        this.risk_score = risk_score;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
