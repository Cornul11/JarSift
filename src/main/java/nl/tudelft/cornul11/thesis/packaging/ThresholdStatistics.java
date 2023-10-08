package nl.tudelft.cornul11.thesis.packaging;

public class ThresholdStatistics {
    private int processedCount;
    private int nonEmptyProjectCount;
    private double f1ScoreSum;
    private double f1ScoreSumSmall;
    private double f1ScoreSumBig;
    private int smallProjectCount;
    private int bigProjectCount;
    private double totalF1ScoreMinimizeJarEnabled;
    private int totalProjectsMinimizeJarEnabled;
    private double totalF1ScoreMinimizeJarDisabled;
    private int totalProjectsMinimizeJarDisabled;

    private double totalF1ScoreRelocationEnabled;
    private int totalProjectsRelocationEnabled;
    private double totalF1ScoreRelocationDisabled;
    private int totalProjectsRelocationDisabled;

    public double getTotalF1ScoreMinimizeJarEnabled() {
        return totalF1ScoreMinimizeJarEnabled;
    }

    public void setTotalF1ScoreMinimizeJarEnabled(double totalF1ScoreMinimizeJarEnabled) {
        this.totalF1ScoreMinimizeJarEnabled = totalF1ScoreMinimizeJarEnabled;
    }

    public int getTotalProjectsMinimizeJarEnabled() {
        return totalProjectsMinimizeJarEnabled;
    }

    public void setTotalProjectsMinimizeJarEnabled(int totalProjectsMinimizeJarEnabled) {
        this.totalProjectsMinimizeJarEnabled = totalProjectsMinimizeJarEnabled;
    }

    public double getTotalF1ScoreMinimizeJarDisabled() {
        return totalF1ScoreMinimizeJarDisabled;
    }

    public void setTotalF1ScoreMinimizeJarDisabled(double totalF1ScoreMinimizeJarDisabled) {
        this.totalF1ScoreMinimizeJarDisabled = totalF1ScoreMinimizeJarDisabled;
    }

    public int getTotalProjectsMinimizeJarDisabled() {
        return totalProjectsMinimizeJarDisabled;
    }

    public void setTotalProjectsMinimizeJarDisabled(int totalProjectsMinimizeJarDisabled) {
        this.totalProjectsMinimizeJarDisabled = totalProjectsMinimizeJarDisabled;
    }

    public double getTotalF1ScoreRelocationEnabled() {
        return totalF1ScoreRelocationEnabled;
    }

    public void setTotalF1ScoreRelocationEnabled(double totalF1ScoreRelocationEnabled) {
        this.totalF1ScoreRelocationEnabled = totalF1ScoreRelocationEnabled;
    }

    public int getTotalProjectsRelocationEnabled() {
        return totalProjectsRelocationEnabled;
    }

    public void setTotalProjectsRelocationEnabled(int totalProjectsRelocationEnabled) {
        this.totalProjectsRelocationEnabled = totalProjectsRelocationEnabled;
    }

    public double getTotalF1ScoreRelocationDisabled() {
        return totalF1ScoreRelocationDisabled;
    }

    public void setTotalF1ScoreRelocationDisabled(double totalF1ScoreRelocationDisabled) {
        this.totalF1ScoreRelocationDisabled = totalF1ScoreRelocationDisabled;
    }

    public int getTotalProjectsRelocationDisabled() {
        return totalProjectsRelocationDisabled;
    }

    public void setTotalProjectsRelocationDisabled(int totalProjectsRelocationDisabled) {
        this.totalProjectsRelocationDisabled = totalProjectsRelocationDisabled;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public int getNonEmptyProjectCount() {
        return nonEmptyProjectCount;
    }

    public void setNonEmptyProjectCount(int nonEmptyProjectCount) {
        this.nonEmptyProjectCount = nonEmptyProjectCount;
    }

    public double getF1ScoreSum() {
        return f1ScoreSum;
    }

    public void setF1ScoreSum(double f1ScoreSum) {
        this.f1ScoreSum = f1ScoreSum;
    }

    public double getF1ScoreSumSmall() {
        return f1ScoreSumSmall;
    }

    public void setF1ScoreSumSmall(double f1ScoreSumSmall) {
        this.f1ScoreSumSmall = f1ScoreSumSmall;
    }

    public double getF1ScoreSumBig() {
        return f1ScoreSumBig;
    }

    public void setF1ScoreSumBig(double f1ScoreSumBig) {
        this.f1ScoreSumBig = f1ScoreSumBig;
    }

    public int getSmallProjectCount() {
        return smallProjectCount;
    }

    public void setSmallProjectCount(int smallProjectCount) {
        this.smallProjectCount = smallProjectCount;
    }

    public int getBigProjectCount() {
        return bigProjectCount;
    }

    public void setBigProjectCount(int bigProjectCount) {
        this.bigProjectCount = bigProjectCount;
    }
}
