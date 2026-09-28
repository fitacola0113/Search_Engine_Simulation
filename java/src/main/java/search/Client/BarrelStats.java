package search.Client;

public class BarrelStats {
    private double averageSearchTime;  // Tempo total em ms
    private int indexSize;          // Tamanho do índice
    
    public void setAverageSearchTime(double timeMs) {
        averageSearchTime = timeMs;
    }
    
    public void setIndexSize(int size) {
        this.indexSize = size;
    }
    
    public double getAverageSearchTime() {
        return averageSearchTime;
    }
    
    public int getIndexSize() {
        return indexSize;
    }
}
