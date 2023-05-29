package nl.tudelft.cornul11.thesis.api;

import java.util.List;
import nl.tudelft.cornul11.thesis.api.Schema;

public class ApiResponse {
    private List<Schema> vulns;

    public List<Schema> getVulns() {
        return vulns;
    }

    public void setVulns(List<Schema> vulns) {
        this.vulns = vulns;
    }
}
