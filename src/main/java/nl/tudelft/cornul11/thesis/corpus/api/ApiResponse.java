package nl.tudelft.cornul11.thesis.corpus.api;

import nl.tudelft.cornul11.thesis.api.Schema;

import java.util.List;

public class ApiResponse {
    private List<Schema> vulns;

    public List<Schema> getVulns() {
        return vulns;
    }

    public void setVulns(List<Schema> vulns) {
        this.vulns = vulns;
    }
}
