package search.Client;

import java.io.Serializable;

public class TopSearchEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String word;
    private final int count;
    
    public TopSearchEntry(String word, int count) {
        this.word = word;
        this.count = count;
    }
    
    public String getWord() {
        return word;
    }
    
    public int getCount() {
        return count;
    }
    
    @Override
    public String toString() {
        return word + ": " + count;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopSearchEntry)) return false;
        TopSearchEntry that = (TopSearchEntry) o;
        return count == that.count && word.equals(that.word);
    }
    
    @Override
    public int hashCode() {
        return 31 * word.hashCode() + count;
    }
}