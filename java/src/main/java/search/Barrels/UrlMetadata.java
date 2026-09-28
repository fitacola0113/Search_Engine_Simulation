package search.Barrels;

import java.io.Serializable;

public class UrlMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String url;
    private String title;
    private String snippet; // Citação/resumo
    private int referenceCount; // Número de referências (backlinks)
    
    public UrlMetadata(String url, String title, String snippet) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.referenceCount = 0;
    }
    
    public UrlMetadata(String url, String title, String snippet, int referenceCount) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.referenceCount = referenceCount;
    }
    
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public int getReferenceCount() { return referenceCount; }
    public void setReferenceCount(int count) { this.referenceCount = count; }
    
    @Override
    public String toString() {
        return title + "\n" + url + "\n" + snippet + "\n" + "Referências: " + referenceCount;
    }
}