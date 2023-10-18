package nl.tudelft.cornul11.thesis.packaging;

public class ThresholdStatistics {
    private int nonEmptyProjectCount;
    private double f1ScoreSum;
    private double f1ScoreAverage;
    private double f1ScoreSumSmall;
    private double f1ScoreSumBig;
    private int smallProjectCount;
    private int bigProjectCount;
    private double totalF1ScoreMinimizeJarEnabled;
    private double precisionMinimizeJarEnabled;
    private double recallMinimizeJarEnabled;
    private int totalProjectsMinimizeJarEnabled;
    private double totalF1ScoreMinimizeJarDisabled;
    private double precisionMinimizeJarDisabled;
    private double recallMinimizeJarDisabled;
    private int totalProjectsMinimizeJarDisabled;

    private double totalF1ScoreRelocationEnabled;
    private double precisionRelocationEnabled;
    private double recallRelocationEnabled;
    private int totalProjectsRelocationEnabled;
    private double totalF1ScoreRelocationDisabled;
    private double precisionRelocationDisabled;
    private double recallRelocationDisabled;
    private int totalProjectsRelocationDisabled;


    public double getF1ScoreAverage() {
        return f1ScoreAverage;
    }

    public void setF1ScoreAverage(double f1ScoreAverage) {
        this.f1ScoreAverage = f1ScoreAverage;
    }

    public double getPrecisionMinimizeJarEnabled() {
        return precisionMinimizeJarEnabled;
    }

    public void setPrecisionMinimizeJarEnabled(double precisionMinimizeJarEnabled) {
        this.precisionMinimizeJarEnabled = precisionMinimizeJarEnabled;
    }

    public double getRecallMinimizeJarEnabled() {
        return recallMinimizeJarEnabled;
    }

    public void setRecallMinimizeJarEnabled(double recallMinimizeJarEnabled) {
        this.recallMinimizeJarEnabled = recallMinimizeJarEnabled;
    }

    public double getPrecisionMinimizeJarDisabled() {
        return precisionMinimizeJarDisabled;
    }

    public void setPrecisionMinimizeJarDisabled(double precisionMinimizeJarDisabled) {
        this.precisionMinimizeJarDisabled = precisionMinimizeJarDisabled;
    }

    public double getRecallMinimizeJarDisabled() {
        return recallMinimizeJarDisabled;
    }

    public void setRecallMinimizeJarDisabled(double recallMinimizeJarDisabled) {
        this.recallMinimizeJarDisabled = recallMinimizeJarDisabled;
    }

    public double getPrecisionRelocationEnabled() {
        return precisionRelocationEnabled;
    }

    public void setPrecisionRelocationEnabled(double precisionRelocationEnabled) {
        this.precisionRelocationEnabled = precisionRelocationEnabled;
    }

    public double getRecallRelocationEnabled() {
        return recallRelocationEnabled;
    }

    public void setRecallRelocationEnabled(double recallRelocationEnabled) {
        this.recallRelocationEnabled = recallRelocationEnabled;
    }

    public double getPrecisionRelocationDisabled() {
        return precisionRelocationDisabled;
    }

    public void setPrecisionRelocationDisabled(double precisionRelocationDisabled) {
        this.precisionRelocationDisabled = precisionRelocationDisabled;
    }

    public double getRecallRelocationDisabled() {
        return recallRelocationDisabled;
    }

    public void setRecallRelocationDisabled(double recallRelocationDisabled) {
        this.recallRelocationDisabled = recallRelocationDisabled;
    }


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
