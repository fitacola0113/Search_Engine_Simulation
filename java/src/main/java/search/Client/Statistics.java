package search.Client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Statistics {
    private List<TopSearchEntry> topSearches = new ArrayList<>();
    private Map<String, BarrelStats> activeBarrels = new HashMap<>();

    public void setTopSearches(List<TopSearchEntry> topSearches) {
        this.topSearches = topSearches;
    }

    public List<TopSearchEntry> getTopSearches() {
        return this.topSearches;
    }

    public Map<String, BarrelStats> getactiveBarrels() {
        return this.activeBarrels;
    }

    public void setActiveBarrels(Map<String, BarrelStats> activeBarrels) {
        this.activeBarrels = activeBarrels;
    }

    public void setAverageTime(double averageTime, String barrelName) {
        activeBarrels.computeIfAbsent(barrelName, k -> new BarrelStats()
        ).setAverageSearchTime(averageTime);
    }

    public void setIndexSize(int size, String barrelName) {
        activeBarrels.computeIfAbsent(barrelName, k -> new BarrelStats()
        ).setIndexSize(size);
    }

    public void newBarrel(String barrelName, long averageSearchTime, int indexSize) {
        BarrelStats stats = activeBarrels.computeIfAbsent(barrelName, k -> new BarrelStats());
        stats.setAverageSearchTime(averageSearchTime);
        stats.setIndexSize(indexSize);
    }

    public void deleteBarrel(String barrelName) {
        activeBarrels.remove(barrelName);
    }
}
